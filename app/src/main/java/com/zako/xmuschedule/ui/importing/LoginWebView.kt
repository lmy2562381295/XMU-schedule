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
import com.zako.xmuschedule.data.remote.JwUrls
import com.zako.xmuschedule.util.AppLog

/**
 * CAS 登录 WebView：入口是带编码 service 的应用登录链。
 * 页面保持挂载直到导入完成——登录就绪后直接在这个页面里用 fetch() 取数，
 * 会话有效性由取数结果验证，不依赖特定 cookie 名。
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
                        // 票据回跳页（/login?ticket=...）是中间态，等它落定后的下一个教务页面
                        if (url.contains("ticket=")) return
                        // 进入任何教务应用页面即视为登录就绪；会话有效性由页面内取数结果验证
                        AppLog.event(
                            appContext, "webview",
                            "进入教务页面（登录就绪），landing=${url.take(140)} cookies=${cookieNames()}",
                        )
                        CookieManager.getInstance().flush()
                        succeeded = true
                        onReady()
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
