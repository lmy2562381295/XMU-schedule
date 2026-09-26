package com.zako.xmuschedule.ui.importing

import android.app.Application
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

/**
 * CAS 登录 WebView：登录成功（跳回教务系统主页）后回调一次。
 * 会话 cookie 由系统 CookieManager 持有并持久化，网络层请求时自动附加。
 */
@Composable
fun LoginWebView(onSuccess: () -> Unit) {
    var succeeded by remember { mutableStateOf(false) }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                webViewClient = object : WebViewClient() {
                    private fun check(url: String?) {
                        val target = url ?: return
                        val host = Uri.parse(target).host ?: return
                        if (!succeeded && host == "jw.xmu.edu.cn" && !target.contains("/login")) {
                            CookieManager.getInstance().flush()
                            succeeded = true
                            onSuccess()
                        }
                    }

                    override fun onPageFinished(view: WebView, url: String?) {
                        CookieManager.getInstance().flush()
                        check(url)
                    }

                    override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                        check(url)
                    }
                }
                loadUrl(JwUrls.CAS_LOGIN)
            }
        },
    )
}
