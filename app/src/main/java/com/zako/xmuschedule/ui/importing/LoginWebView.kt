package com.zako.xmuschedule.ui.importing

import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.zako.xmuschedule.data.remote.JwClient
import com.zako.xmuschedule.data.remote.JwUrls
import com.zako.xmuschedule.util.AppLog

private const val SESSION_COOKIE = "SAAS_U="

/**
 * CAS 登录 WebView：从教务系统自身的 /login 入口进入。
 * 成功判定优先看接口路径下的 SAAS_U cookie；若落到教务首页仍未见该 cookie，
 * 则触发一次服务端会话探测（onPortalLanded），以接口实际返回为准。
 */
@Composable
fun LoginWebView(
    onSuccess: () -> Unit,
    onPortalLanded: () -> Unit,
) {
    var succeeded by remember { mutableStateOf(false) }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            val appContext = context.applicationContext
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                webViewClient = object : WebViewClient() {
                    private var portalProbeFired = false

                    private fun check(url: String?) {
                        if (succeeded) return
                        val host = url?.let { Uri.parse(it).host } ?: return
                        if (host != "jw.xmu.edu.cn") return
                        val cookie = JwClient(appContext).apiCookie().orEmpty()
                        if (cookie.contains(SESSION_COOKIE)) {
                            AppLog.event(appContext, "webview", "登录成功（接口路径下检测到 SAAS_U），landing=$url")
                            CookieManager.getInstance().flush()
                            succeeded = true
                            onSuccess()
                        } else if (url.contains("/new/index.html") && !portalProbeFired) {
                            portalProbeFired = true
                            AppLog.event(appContext, "webview", "落到教务首页未见 SAAS_U，触发服务端会话探测")
                            onPortalLanded()
                        }
                    }

                    override fun onPageFinished(view: WebView, url: String?) {
                        CookieManager.getInstance().flush()
                        check(url)
                    }

                    override fun onReceivedError(
                        view: WebView,
                        request: android.webkit.WebResourceRequest,
                        error: android.webkit.WebResourceError,
                    ) {
                        AppLog.event(
                            appContext, "webview",
                            "加载失败: ${request.url} ${error.description}",
                        )
                    }

                    override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                        check(url)
                    }
                }
                AppLog.event(appContext, "webview", "打开登录入口: ${JwUrls.CAS_LOGIN}")
                loadUrl(JwUrls.CAS_LOGIN)
            }
        },
    )
}
