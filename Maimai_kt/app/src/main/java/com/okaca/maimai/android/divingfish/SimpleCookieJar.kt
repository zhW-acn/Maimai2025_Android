package com.okaca.maimai.android.divingfish

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

class SimpleCookieJar : CookieJar {
    private val cookieStore = mutableMapOf<String, List<Cookie>>()

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val merged = cookieStore[url.host].orEmpty()
            .associateBy { it.name }
            .toMutableMap()
        cookies.forEach { merged[it.name] = it }
        cookieStore[url.host] = merged.values.toList()
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> =
        cookieStore[url.host].orEmpty()

    @Synchronized
    fun clearCookieStore() {
        cookieStore.clear()
    }
}
