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
import com.zako.xmuschedule.data.remote.JwUrls
import com.zako.xmuschedule.util.AppLog

private const val SESSION_COOKIE = "SAAS_U="

/**
 * CAS 登录 WebView：从教务系统自身的 /login 入口进入（由教务生成规范的 CAS 跳转）。
 * 登录成功的判定是教务域 cookie 中出现会话 cookie SAAS_U（仅本地日志记录 cookie 名单，不记录值）。
 */
@Composable
fun LoginWebView(onSuccess: () -> Unit) {
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
                    private fun cookieNames(): String =
                        CookieManager.getInstance().getCookie(JwUrls.BASE)
                            ?.split(';')
                            ?.map { it.trim().substringBefore('=') }
                            ?.joinToString(",")
                            .orEmpty()

                    private fun check(url: String?) {
                        if (succeeded) return
                        val host = url?.let { Uri.parse(it).host } ?: return
                        if (host != "jw.xmu.edu.cn") return
                        val cookie = CookieManager.getInstance().getCookie(JwUrls.BASE).orEmpty()
                        if (cookie.contains(SESSION_COOKIE)) {
                            AppLog.event(appContext, "webview", "登录成功（检测到 SAAS_U），landing=$url cookies=${cookieNames()}")
                            CookieManager.getInstance().flush()
                            succeeded = true
                            onSuccess()
                        } else if (url.contains("/new/index.html")) {
                            AppLog.event(appContext, "webview", "落到教务首页但未见 SAAS_U，cookies=${cookieNames()}")
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
