package app.riwaya.core

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebSettings
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * The app's network. Cookies live in the WebView's cookie store, so a site's check that the reader passes in the
 * in-app browser is reused here; the user agent is the WebView's own, as the site expects. Nothing here gets past a
 * site's protection by itself.
 */
object Net {

    @Volatile var userAgent: String = DEFAULT_UA
        private set

    lateinit var client: OkHttpClient
        private set

    fun init(context: Context) {
        userAgent = runCatching { WebSettings.getDefaultUserAgent(context) }.getOrNull() ?: DEFAULT_UA
        client = OkHttpClient.Builder()
            .cookieJar(WebViewCookies)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .cache(okhttp3.Cache(File(context.cacheDir, "http"), 50L * 1024 * 1024))
            .addInterceptor { chain ->
                val req = chain.request()
                val out = if (req.header("User-Agent") == null || req.header("User-Agent")!!.startsWith("okhttp") || req.header("User-Agent")!!.startsWith("Ktor")) {
                    req.newBuilder().header("User-Agent", userAgent).build()
                } else {
                    req
                }
                chain.proceed(out)
            }
            .build()
    }

    fun get(url: String, headers: Map<String, String> = emptyMap()): Request =
        Request.Builder().url(url).apply { headers.forEach { (k, v) -> header(k, v) } }.build()

    private object WebViewCookies : CookieJar {
        private val manager: CookieManager? by lazy { runCatching { CookieManager.getInstance() }.getOrNull() }

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val m = manager ?: return
            val u = url.toString()
            cookies.forEach { m.setCookie(u, it.toString()) }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val raw = manager?.getCookie(url.toString()) ?: return emptyList()
            return raw.split(';').mapNotNull { Cookie.parse(url, it.trim()) }
        }
    }

    private const val DEFAULT_UA =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"
}

/** Runs a call and gives the body text, failing on an error code. */
suspend fun OkHttpClient.text(request: Request): String = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    newCall(request).execute().use { r ->
        if (!r.isSuccessful) throw java.io.IOException("HTTP ${r.code}")
        r.body.string()
    }
}
