package com.zako.xmuschedule.ui.importing

import android.app.Application
import android.net.Uri
import android.webkit.WebView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zako.xmuschedule.data.ImportResult
import com.zako.xmuschedule.data.ScheduleRepository
import com.zako.xmuschedule.data.remote.JwScheduleParser
import com.zako.xmuschedule.data.remote.JwUrls
import com.zako.xmuschedule.reminder.ReminderScheduler
import com.zako.xmuschedule.util.AppLog
import com.zako.xmuschedule.util.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONTokener
import kotlin.coroutines.resume

sealed interface ImportUiState {
    data object Idle : ImportUiState
    data class NeedLogin(val hint: String) : ImportUiState
    data object WebImporting : ImportUiState
    data class Done(val count: Int, val unrecognized: Int, val semester: String) : ImportUiState
    data class Error(val message: String) : ImportUiState
}

/**
 * 页面内导入流程（与参考实现同构）：
 * WebView 打开 CAS 登录（service=应用入口）→ 登录完成后 WebView 停留在教务应用页 →
 * 直接在该页面里用 fetch() 调 jwapp 接口取课表（同源、自动带会话），绕开 cookie 同步问题。
 */
class ImportViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = ScheduleRepository(app)
    private var pendingCode: String? = null
    private var webView: WebView? = null

    private val _state = MutableStateFlow<ImportUiState>(ImportUiState.Idle)
    val state: StateFlow<ImportUiState> = _state.asStateFlow()

    fun semesterCodeGuess(): String = TimeUtils.guessSemesterCode()

    fun startImport(code: String?) {
        pendingCode = code
        transition(ImportUiState.NeedLogin("请完成厦大统一身份认证登录"), "startImport")
    }

    fun attachWebView(v: WebView) {
        webView = v
        AppLog.event(getApplication(), "import", "WebView 就绪")
    }

    fun detachWebView() {
        webView = null
    }

    /** WebView 层判定登录流程已就绪（SAAS_U 出现或已落到教务首页） */
    fun onWebReady() {
        if (_state.value == ImportUiState.WebImporting) return
        transition(ImportUiState.WebImporting, "web ready")
        viewModelScope.launch { webImport() }
    }

    private fun transition(s: ImportUiState, reason: String) {
        AppLog.event(getApplication(), "import", "state -> ${s::class.simpleName} ($reason)")
        _state.value = s
    }

    private suspend fun webImport() {
        val app = getApplication<Application>()
        try {
            val wv = webView ?: error("WebView 未就绪")
            delay(2500) // 等待应用入口的 SSO 链与页面稳定

            var code = pendingCode?.takeIf { it.isNotBlank() }
            if (code == null) {
                code = resolveSemesterCode(wv) ?: TimeUtils.guessSemesterCode()
                AppLog.event(app, "import", "学期代码=$code")
            }

            val url = JwUrls.BASE + JwUrls.XSKCB
            val body = "requestJson=" + Uri.encode("""{"XNXQDM":"$code"}""")

            var raw = ""
            var attempt = 0
            while (attempt < 3) {
                attempt += 1
                raw = inPageFetch(wv, url, body)
                AppLog.event(
                    app, "import",
                    "页面内取数 第${attempt}次: len=${raw.length} 首20=${raw.take(20).replace('\n', ' ')}",
                )
                if (raw.trimStart().startsWith("{")) break
                delay(2500)
            }
            if (!raw.trimStart().startsWith("{")) {
                transition(
                    ImportUiState.Error(
                        "页面内取数未返回 JSON（会话可能仍无效）。原文开头：${raw.take(80)}"
                    ),
                    "fetch not json",
                )
                return
            }

            when (val result = repo.applyWebImport(raw, code)) {
                is ImportResult.Success -> {
                    ReminderScheduler.scheduleWindow(app)
                    transition(ImportUiState.Done(result.count, result.unrecognized, result.semester), "success")
                }
                is ImportResult.Failure -> transition(ImportUiState.Error(result.message), "failure")
            }
        } catch (t: Throwable) {
            AppLog.error(app, "import", t)
            transition(
                ImportUiState.Error("导入过程出现异常：${t.javaClass.simpleName}: ${t.message}"),
                "exception",
            )
        }
    }

    private suspend fun resolveSemesterCode(wv: WebView): String? {
        val guess = TimeUtils.guessSemesterCode()
        val year = guess.substringBefore('-')
        return try {
            val resp = inPageFetch(
                wv,
                JwUrls.BASE + JwUrls.XNXQDM,
                "requestJson=" + Uri.encode("""{"XN":"$year"}"""),
            )
            if (!resp.trimStart().startsWith("{")) {
                AppLog.event(getApplication(), "import", "学期接口未返回 JSON，使用预填 $guess")
                null
            } else {
                extractSemesterCode(resp, year) ?: guess
            }
        } catch (t: Throwable) {
            AppLog.error(getApplication(), "import", t)
            null
        }
    }

    private fun extractSemesterCode(body: String, yearHint: String): String? = runCatching {
        val root = JSONObject(body)
        val datas = root.optJSONObject("datas") ?: return@runCatching null
        val arr = datas.keys().asSequence()
            .mapNotNull { datas.optJSONArray(it) }
            .firstOrNull() ?: return@runCatching null
        val candidates = (0 until arr.length())
            .mapNotNull { arr.optJSONObject(it)?.optString("XNXQDM")?.takeIf { v -> v.isNotBlank() && v != "null" } }
        candidates.lastOrNull { it.contains(yearHint) } ?: candidates.lastOrNull()
    }.getOrNull()

    /** 在 WebView 当前页面（同源）里发 fetch，返回响应文本 */
    private suspend fun inPageFetch(wv: WebView, url: String, body: String): String =
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                val js = "(async () => { try { const resp = await fetch('$url', " +
                    "{ method: 'POST', credentials: 'include', " +
                    "headers: {'X-Requested-With': 'XMLHttpRequest', 'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8'}, " +
                    "body: '$body' }); const text = await resp.text(); " +
                    "return JSON.stringify({ status: resp.status, text: text.substring(0, 400000) }); " +
                    "} catch (e) { return JSON.stringify({ error: String(e) }); } })()"
                wv.evaluateJavascript(js) { result ->
                    cont.resume(
                        runCatching {
                            val v = JSONTokener(result ?: "null").nextValue()
                            when (v) {
                                is String -> v
                                is JSONObject -> v.optString("text")
                                else -> result.orEmpty()
                            }
                        }.getOrElse { result.orEmpty() }
                    )
                }
            }
        }
}
