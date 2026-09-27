package com.zako.xmuschedule.ui.importing

import android.app.Application
import android.net.Uri
import android.webkit.JavascriptInterface
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

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

    @Volatile
    private var pendingGate: CompletableDeferred<String>? = null

    /** 暴露给页面 JS 的结果回传桥（@JavascriptInterface 回调在 JS 线程执行） */
    private inner class FetchBridge {
        @JavascriptInterface
        fun postResult(payload: String) {
            pendingGate?.complete(payload)
        }
    }

    private val _state = MutableStateFlow<ImportUiState>(ImportUiState.Idle)
    val state: StateFlow<ImportUiState> = _state.asStateFlow()

    fun semesterCodeGuess(): String = TimeUtils.guessSemesterCode()

    fun startImport(code: String?) {
        pendingCode = code
        transition(ImportUiState.NeedLogin("请完成厦大统一身份认证登录"), "startImport")
    }

    fun attachWebView(v: WebView) {
        webView = v
        v.addJavascriptInterface(FetchBridge(), "ZakoBridge")
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

            val userCode = pendingCode?.takeIf { it.isNotBlank() }
            val autoCode = resolveSemesterCode(wv)
            AppLog.event(app, "import", "学期候选: 自动=$autoCode 输入=$userCode 预填=${TimeUtils.guessSemesterCode()}")

            val candidates = LinkedHashSet<String>()
            userCode?.let { candidates.add(it) }
            autoCode?.let { candidates.add(it) }
            candidates.add(TimeUtils.guessSemesterCode())

            val url = JwUrls.BASE + JwUrls.XSKCB
            for (code in candidates) {
                val body = "requestJson=" + Uri.encode("""{"XNXQDM":"$code"}""")
                var raw = ""
                var attempt = 0
                while (attempt < 2) {
                    attempt += 1
                    raw = inPageFetch(wv, url, body)
                    AppLog.event(
                        app, "import",
                        "取数 学期=$code 第${attempt}次: len=${raw.length} 正文=${raw.take(400).replace('\n', ' ')}",
                    )
                    if (raw.trimStart().startsWith("{")) break
                    delay(2500)
                }
                if (!raw.trimStart().startsWith("{")) continue

                val parsed = JwScheduleParser.parse(raw)
                if (parsed.courses.isEmpty()) {
                    AppLog.event(app, "import", "学期=$code 返回 0 条课程，尝试下一候选")
                    continue
                }
                when (val result = repo.applyWebImport(raw, code)) {
                    is ImportResult.Success -> {
                        ReminderScheduler.scheduleWindow(app)
                        transition(ImportUiState.Done(result.count, result.unrecognized, result.semester), "success")
                    }
                    is ImportResult.Failure -> transition(ImportUiState.Error(result.message), "failure")
                }
                return
            }
            transition(
                ImportUiState.Error(
                    "所有学期候选均未取到课程（尝试：${candidates.joinToString("、")}）。" +
                        "可能是学期代码格式与学校不一致——请把运行日志发我，日志里有学期列表接口的原始返回，可据此修正。"
                ),
                "empty all",
            )
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
            AppLog.event(getApplication(), "import", "学期接口返回: ${resp.take(300).replace('\n', ' ')}")
            if (!resp.trimStart().startsWith("{")) {
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

    /** 在 WebView 当前页面（同源）里发 fetch，结果经 ZakoBridge 推回（不依赖 evaluateJavascript 回传值） */
    private suspend fun inPageFetch(wv: WebView, url: String, body: String): String {
        val gate = CompletableDeferred<String>()
        pendingGate = gate
        withContext(Dispatchers.Main) {
            val js = "(function(){ try { fetch('$url', {method:'POST',credentials:'include'," +
                "headers:{'X-Requested-With':'XMLHttpRequest','Content-Type':'application/x-www-form-urlencoded; charset=UTF-8'}," +
                "body:'$body'})" +
                ".then(function(r){ return r.text().then(function(t){ return JSON.stringify({status:r.status,text:t.substring(0,200000)}); }); })" +
                ".then(function(p){ ZakoBridge.postResult(p); })" +
                ".catch(function(e){ ZakoBridge.postResult(JSON.stringify({error:String(e)})); }); " +
                "return 'ok'; } catch(e){ return 'jserr:'+String(e); } })()"
            wv.evaluateJavascript(js) { ret ->
                val trimmed = ret?.trim()?.removePrefix("\"")?.removeSuffix("\"")
                if (trimmed != "ok") {
                    AppLog.event(getApplication(), "webview", "fetch 注入返回异常: ${ret?.take(120)}")
                }
            }
        }
        val payload = withTimeoutOrNull(20_000) { gate.await() } ?: ""
        pendingGate = null
        val parsed = runCatching { JSONObject(payload) }.getOrNull()
        val status = parsed?.optInt("status", -1) ?: -2
        val text = parsed?.optString("text").orEmpty()
        AppLog.event(getApplication(), "webview", "页面内 fetch: status=$status len=${text.length}")
        return text
    }
}
