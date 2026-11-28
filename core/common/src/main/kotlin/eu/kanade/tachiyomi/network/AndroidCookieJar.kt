package eu.kanade.tachiyomi.network

import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

class AndroidCookieJar(
    // KMK -->
    /** Whether cookies without an expiry date are kept for [SESSION_COOKIE_TTL_SECONDS] instead of until the app is closed. */
    private val keepSessionCookies: () -> Boolean = { false },
    // KMK <--
) : CookieJar {

    private val manager = CookieManager.getInstance()

    // KMK -->
    private val handler = Handler(Looper.getMainLooper())
    private val flushRunnable = Runnable { manager.flush() }

    /** Writes the cookies to disk now: the system only does it lazily and a killed process would lose them. */
    fun flush() {
        handler.removeCallbacks(flushRunnable)
        manager.flush()
    }

    private fun scheduleFlush() {
        handler.removeCallbacks(flushRunnable)
        handler.postDelayed(flushRunnable, FLUSH_DELAY_MS)
    }
    // KMK <--

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val urlString = url.toString()

        // KMK -->
        val keep = keepSessionCookies()
        cookies.forEach { cookie ->
            val header = cookie.toString()
            // A session cookie has no expiry: the browser engine forgets it when the process dies, which logs the
            // user out of the source. A server that wants the cookie gone still can: the deletion arrives as an
            // expired cookie, which is persistent and is applied normally.
            val value = if (keep && !cookie.persistent) "$header; Max-Age=$SESSION_COOKIE_TTL_SECONDS" else header
            manager.setCookie(urlString, value)
        }
        if (cookies.isNotEmpty()) scheduleFlush()
        // KMK <--
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        return get(url)
    }

    fun get(url: HttpUrl): List<Cookie> {
        val cookies = manager.getCookie(url.toString())

        return if (cookies != null && cookies.isNotEmpty()) {
            cookies.split(";").mapNotNull { Cookie.parse(url, it) }
        } else {
            emptyList()
        }
    }

    fun remove(url: HttpUrl, cookieNames: List<String>? = null, maxAge: Int = -1): Int {
        val urlString = url.toString()
        val cookies = manager.getCookie(urlString) ?: return 0

        fun List<String>.filterNames(): List<String> {
            return if (cookieNames != null) {
                this.filter { it in cookieNames }
            } else {
                this
            }
        }

        return cookies.split(";")
            .map { it.substringBefore("=") }
            .filterNames()
            .onEach { manager.setCookie(urlString, "$it=;Max-Age=$maxAge") }
            .count()
    }

    fun removeAll() {
        manager.removeAllCookies {}
    }

    // KMK -->
    private companion object {
        const val SESSION_COOKIE_TTL_SECONDS = 30L * 24 * 60 * 60
        const val FLUSH_DELAY_MS = 2_000L
    }
    // KMK <--
}
