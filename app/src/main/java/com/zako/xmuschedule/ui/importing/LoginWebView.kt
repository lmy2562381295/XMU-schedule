package com.zako.xmuschedule.ui.importing

import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
 * CAS 登录 WebView：入口是带编码 service 的应用登录链。
 * 页面保持挂载直到导入完成——后续取数直接在这个页面里用 fetch 完成。
 * 登录就绪的判定：接口路径下出现 SAAS_U，或已落到教务首页（两种情况都交给
 * 页面内取数流程去验证，以服务端返回为准）。
 */
@Composable
fun LoginWebView(
    onReady: () -> Unit,
    onAttach: (WebView) -> Unit,
    onDetach: () -> Unit,
) {
    var succeeded by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose { onDetach() }
    }

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
                    private fun check(url: String?) {
                        if (succeeded) return
                        val host = url?.let { Uri.parse(it).host } ?: return
                        if (host != "jw.xmu.edu.cn") return
                        val cookie = JwClient.apiCookie().orEmpty()
                        val hasSession = cookie.contains(SESSION_COOKIE)
                        if (hasSession || url.contains("/new/index.html")) {
                            AppLog.event(
                                appContext, "webview",
                                "登录流程就绪（SAAS_U=$hasSession），landing=$url cookies=${cookieNames()}",
                            )
                            CookieManager.getInstance().flush()
                            succeeded = true
                            onReady()
                        }
                    }

                    private fun cookieNames(): String =
                        CookieManager.getInstance().getCookie(JwUrls.BASE)
                            ?.split(';')
                            ?.map { it.trim().substringBefore('=') }
                            ?.joinToString(",")
                            .orEmpty()

                    override fun onPageFinished(view: WebView, url: String?) {
                        CookieManager.getInstance().flush()
                        if (!succeeded) {
                            AppLog.event(
                                appContext, "webview",
                                "页面加载完成: ${url?.take(140)} cookies=${cookieNames()}",
                            )
                        }
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
                        if (!succeeded && url != null && url.contains("ticket=")) {
                            AppLog.event(appContext, "webview", "票据回跳: ${url.take(160)}")
                        }
                        check(url)
                    }
                }
                AppLog.event(appContext, "webview", "打开登录入口: ${JwUrls.CAS_LOGIN}")
                loadUrl(JwUrls.CAS_LOGIN)
            }.also { onAttach(it) }
        },
    )
}
