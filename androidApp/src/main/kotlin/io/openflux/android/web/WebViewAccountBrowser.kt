package io.openflux.android.web

import android.webkit.CookieManager
import io.openflux.desktop.model.AccountCookies
import io.openflux.desktop.model.AccountKind
import io.openflux.desktop.model.YandexDisk
import io.openflux.desktop.service.AccountBrowser
import io.openflux.desktop.ui.BrowserPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * [AccountBrowser] on a WebView: the app's one cookie jar is emptied before
 * each page and after it, so a sign-in stays only in accounts.json.
 */
class WebViewAccountBrowser : AccountBrowser {
    private val _page = MutableStateFlow<BrowserPage?>(null)
    override val page: StateFlow<BrowserPage?> = _page.asStateFlow()
    private val current get() = _page.value as? WebPage

    override val url get() = current?.url.orEmpty()
    override val loading get() = current?.loading ?: false
    override val closed get() = current?.closed ?: true

    override suspend fun open(kind: AccountKind, url: String, cookies: Map<String, String>, onStep: (String) -> Unit) {
        close()
        withContext(Dispatchers.Main) {
            val manager = CookieManager.getInstance()
            suspendCancellableCoroutine { done -> manager.removeAllCookies { done.resume(Unit) } }
            val site = "https://${kind.cookieDomain.removePrefix(".")}/"
            for ((name, value) in cookies) {
                suspendCancellableCoroutine { done ->
                    manager.setCookie(site, "$name=$value; Domain=${kind.cookieDomain}; Path=/; Secure; HttpOnly") { done.resume(Unit) }
                }
            }
            manager.flush()
        }
        // The page loads once the screen shows it (WebBrowserViews).
        // Only Yandex ties a session to the core's user agent; other sign-in
        // pages (VK ID for Mail.ru) break under a desktop one.
        _page.value = WebPage(url, scripts = true, userAgent = if (kind == AccountKind.Yandex) WebPage.USER_AGENT else null)
    }

    override fun load(url: String) {
        current?.load(url)
    }

    override suspend fun evaluate(script: String): String =
        (current ?: throw IllegalStateException("Страница закрыта")).evaluate(script)

    override suspend fun cookies(kind: AccountKind): Map<String, String> = withContext(Dispatchers.Main) {
        val urls = kind.cookieUrls
        val manager = CookieManager.getInstance()
        val out = linkedMapOf<String, String>()
        // WebView does not say a cookie's domain; the sign-in host comes first.
        for (u in urls) AccountCookies.parseHeader(manager.getCookie(u).orEmpty()).forEach { (k, v) -> out.putIfAbsent(k, v) }
        out
    }

    override fun close() {
        val page = current
        _page.value = null
        if (page != null) {
            page.close()
            WebPage.clearCookies()
        }
    }
}
