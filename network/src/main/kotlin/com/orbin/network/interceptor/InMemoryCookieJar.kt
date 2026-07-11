package com.orbin.network.interceptor

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.util.concurrent.ConcurrentHashMap

/**
 * A small in-memory [CookieJar] scoped to the process lifetime. It exists so gate-clearance cookies
 * (POWBlock's `POW_TOKEN` / `POW_ID`, a site's terms-of-service cookie) set on redirect hops are
 * captured and replayed automatically — an application interceptor cannot see `Set-Cookie` headers
 * from intermediate redirect responses, but the client's cookie jar does.
 *
 * Cookies are held per host and re-sent on every matching request. Expired cookies are dropped on
 * read. Nothing is persisted to disk: clearance is cheap to re-acquire and keeping it in memory
 * avoids storing site cookies at rest.
 */
class InMemoryCookieJar : CookieJar {
    private val store = ConcurrentHashMap<String, MutableList<Cookie>>()

    override fun saveFromResponse(
        url: HttpUrl,
        cookies: List<Cookie>,
    ) {
        if (cookies.isEmpty()) return
        val host = url.host
        val list = store.getOrPut(host) { mutableListOf() }
        synchronized(list) {
            for (cookie in cookies) {
                list.removeAll { it.name == cookie.name && it.path == cookie.path }
                list.add(cookie)
            }
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val list = store[url.host] ?: return emptyList()
        val now = System.currentTimeMillis()
        return synchronized(list) {
            list.removeAll { it.expiresAt < now }
            list.filter { it.matches(url) }
        }
    }
}
