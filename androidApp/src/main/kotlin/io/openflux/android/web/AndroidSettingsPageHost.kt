package io.openflux.android.web

import io.openflux.desktop.service.SettingsPageHost
import io.openflux.app.platform.BrowserPage

/** Shows a script's settings wizard in a WebView, answered on its own channel (never the connection's setup requests). */
object AndroidSettingsPageHost : SettingsPageHost {
    override suspend fun open(html: String, onStep: (String) -> Unit, onSubmit: (String) -> Unit): BrowserPage =
        WebPage(html = html, onSubmit = onSubmit)

    override fun close(page: BrowserPage) {
        (page as? WebPage)?.close()
    }
}
