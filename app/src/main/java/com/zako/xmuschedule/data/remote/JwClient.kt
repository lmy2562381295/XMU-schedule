package com.zako.xmuschedule.data.remote

import android.content.Context
import android.webkit.CookieManager
import com.zako.xmuschedule.util.AppLog
import okhttp3.FormBody
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

object JwUrls {
    const val BASE = "https://jw.xmu.edu.cn"

    /**
     * 应用入口（参考实现验证过的标准成绩应用）。appShow 的 SSO 链会种下 jwapp 所需的
     * SAAS_U 会话 cookie。登录入口的 service 参数用它并整体 URL 编码。
     */
    const val START_URL = "$BASE/appShow?appId=4768574631264620"

    /** CAS 登录入口：service=整体编码后的应用入口（与参考实现一致，勿改用手拼嵌套 service） */
    val CAS_LOGIN: String =
        "https://ids.xmu.edu.cn/authserver/login?type=userNameLogin&service=" +
            android.net.Uri.encode(START_URL, "")

    const val XSKCB = "/jwapp/sys/wdkb/modules/xskcb/xskcb.do"
    const val XNXQDM = "/jwapp/sys/wdkb/modules/xskcb/xnxqdm.do"
}

/**
 * 教务系统 HTTP 客户端。
 * 登录会话由 WebView 的 CookieManager 持有；这里每次请求把该域 cookie 附加到请求头。
 */
class JwClient(private val context: Context) {

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .addInterceptor(::attachCookies)
        .addInterceptor(::baseHeaders)
        .build()

    private fun attachCookies(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val cookie = CookieManager.getInstance().getCookie(request.url.toString())
        return chain.proceed(
            if (cookie.isNullOrBlank()) request
            else request.newBuilder().header("Cookie", cookie).build()
        )
    }

    private fun baseHeaders(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36")
            .header("Referer", "${JwUrls.BASE}/new/index.html")
            .header("X-Requested-With", "XMLHttpRequest")
            .build()
        return chain.proceed(request)
    }

    /**
     * 会话有效性探测：向课表接口发一次空查询。
     * 未登录时 jw 会 302 到 CAS 或返回登录页 HTML；已登录时返回 JSON（即便业务参数不对）。
     */
    fun isSessionValid(): Boolean = try {
        postForm(JwUrls.XSKCB, "{}").use { resp ->
            val text = resp.body?.string().orEmpty().trimStart()
            val valid = text.startsWith("{")
            val cookieNames = apiCookieNames()
            AppLog.event(
                context, "jw",
                "isSessionValid -> $valid (body 首 20 字符: ${text.take(20).replace('\n', ' ')}) cookies=$cookieNames",
            )
            valid
        }
    } catch (e: Exception) {
        AppLog.error(context, "jw", e)
        false
    }

    /** 用真实接口的完整路径查 cookie（cookie 可能带 Path=/jwapp 之类的限制） */
    fun apiCookie(): String? = CookieManager.getInstance().getCookie(JwUrls.BASE + JwUrls.XSKCB)

    fun apiCookieNames(): String =
        apiCookie()?.split(';')?.map { it.trim().substringBefore('=') }?.joinToString(",").orEmpty()

    companion object {
        /** 静态版本供 WebView 回调等无实例场景使用 */
        fun apiCookie(): String? = CookieManager.getInstance().getCookie(JwUrls.BASE + JwUrls.XSKCB)
    }

    /**
     * 拉取可选学期列表并挑选当前学年学期的代码。
     * 各校该接口字段差异较大，这里做宽松解析：datas 下的第一个数组，取其中 XNXQDM 字段。
     */
    fun fetchSemesterCode(yearHint: String): String? = try {
        postForm(JwUrls.XNXQDM, """{"XN":"$yearHint"}""").use { resp ->
            val code = extractSemesterCode(resp.body?.string().orEmpty(), yearHint)
            AppLog.event(context, "jw", "fetchSemesterCode -> $code")
            code
        }
    } catch (e: Exception) {
        AppLog.error(context, "jw", e)
        null
    }

    private fun extractSemesterCode(body: String, yearHint: String): String? {
        val root = runCatching { org.json.JSONObject(body) }.getOrNull() ?: return null
        val datas = root.optJSONObject("datas") ?: return null
        val list = datas.keys().asSequence()
            .mapNotNull { datas.optJSONArray(it) }
            .firstOrNull() ?: return null
        val candidates = (0 until list.length())
            .mapNotNull { list.optJSONObject(it)?.optString("XNXQDM")?.takeIf { v -> v.isNotBlank() && v != "null" } }
        return candidates.lastOrNull { it.contains(yearHint.substringBefore('-')) }
            ?: candidates.lastOrNull()
    }

    /**
     * 拉取课表原始 JSON。
     * 兼容两种请求形态：表单 requestJson / 原始 JSON body；返回首个能解出 xskcb 数据的响应。
     */
    fun fetchScheduleRaw(semesterCode: String?): String? {
        lastJson = null
        val requestJson = semesterCode?.let { """{"XNXQDM":"$it"}""" }
        runCatching {
            postForm(JwUrls.XSKCB, requestJson ?: "{}").use { resp ->
                val text = resp.body?.string().orEmpty()
                AppLog.event(context, "jw", "schedule 表单形态: len=${text.length} hasData=${hasScheduleData(text)} 首20=${text.take(20).replace('\n', ' ')}")
                if (hasScheduleData(text)) return text
                if (!text.trimStart().startsWith("{")) return null // 疑似被重定向到登录页
                lastJson = text
            }
        }
        // 形态 2：raw JSON body
        runCatching {
            postRaw(JwUrls.XSKCB, requestJson ?: "{}").use { resp ->
                val text = resp.body?.string().orEmpty()
                AppLog.event(context, "jw", "schedule JSON形态: len=${text.length} hasData=${hasScheduleData(text)}")
                if (hasScheduleData(text)) return text
                if (text.trimStart().startsWith("{")) lastJson = text
            }
        }
        return lastJson?.takeIf { it.isNotBlank() }
    }

    private var lastJson: String? = null

    private fun hasScheduleData(body: String): Boolean {
        val root = runCatching { org.json.JSONObject(body) }.getOrNull() ?: return false
        val datas = root.optJSONObject("datas") ?: return false
        return (datas.opt("xskcb") ?: root.opt("xskcb")) != null
    }

    private fun postForm(path: String, requestJson: String): Response {
        val form = FormBody.Builder().add("requestJson", requestJson).build()
        val request = Request.Builder().url(JwUrls.BASE + path).post(form).build()
        return http.newCall(request).execute()
    }

    private fun postRaw(path: String, json: String): Response {
        val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder().url(JwUrls.BASE + path).post(body).build()
        return http.newCall(request).execute()
    }
}
