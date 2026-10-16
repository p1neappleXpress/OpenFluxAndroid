package io.openflux.android

import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import io.openflux.desktop.service.Accounts
import io.openflux.desktop.data.HttpSessionProbe
import io.openflux.desktop.data.FileAccountRepository
import io.openflux.android.web.WebViewAccountBrowser
import android.app.Application
import android.content.Context
import android.util.Log
import io.openflux.bridge.mobile.Mobile
import io.openflux.android.core.AndroidConnectionService
import io.openflux.android.core.AndroidScriptRepository
import io.openflux.android.core.MobileCoreLinks
import io.openflux.android.node.AndroidNodeWizard
import io.openflux.android.node.AndroidPhpTransport
import io.openflux.android.platform.AndroidPlatformServices
import io.openflux.desktop.data.FileProfileRepository
import io.openflux.desktop.data.FileSettingsRepository
import io.openflux.desktop.model.AppSettings
import io.openflux.desktop.model.CoreShareLinkCodec
import io.openflux.desktop.service.AppContainer
import io.openflux.desktop.service.NodeKeepingConnection
import io.openflux.desktop.service.PhpHostingService
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Holds what the UI and the core service share for the life of the process:
 * the core keeps running in [io.openflux.android.core.CoreService] while the
 * activity comes and goes.
 */
class OpenFluxApplication : Application() {
    val bridge = ActivityBridge()
    lateinit var connection: AndroidConnectionService
        private set
    lateinit var container: AppContainer
        private set

    /** An activity of the app is on screen (captcha requests notify otherwise). */
    @Volatile var visible = false

    /** The way back from the sign-in page; [MainActivity] hands it the address the browser returned to. */
    lateinit var signIn: io.openflux.desktop.data.RedirectSignIn
        private set

    override fun onCreate() {
        super.onCreate()
        // On a phone the VPN is what "connected" means; the proxy is the opt-out.
        val settings = FileSettingsRepository(filesDir, defaults = AppSettings(fullTunnel = true))
        val accounts = Accounts(
            repo = FileAccountRepository(noBackupFilesDir),
            browser = WebViewAccountBrowser(),
            probe = HttpSessionProbe(),
            now = System::currentTimeMillis,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        )
        val scripts = AndroidScriptRepository(this)
        connection = AndroidConnectionService(this, settings, bridge, accounts, scripts)
        val phpHosting = PhpHostingService(AndroidPhpTransport(), clock = System::currentTimeMillis)
        signIn = io.openflux.desktop.data.RedirectSignIn(
            BuildConfig.CLIENT_ID, "io.openflux.client:/oauth2redirect", open = ::openSignInPage,
        )
        container = AppContainer(
            profiles = FileProfileRepository(filesDir),
            settings = settings,
            scripts = scripts,
            // Connecting a profile made by the "без сервера" wizard first asks its node on the hosting to run.
            connection = NodeKeepingConnection(connection, phpHosting, CoroutineScope(SupervisorJob() + Dispatchers.Default)),
            platform = AndroidPlatformServices(this, bridge),
            shareCodec = CoreShareLinkCodec(MobileCoreLinks),
            nodeWizard = AndroidNodeWizard(accounts),
            accounts = accounts,
            phpHosting = phpHosting,
            subscriptionApi = io.openflux.desktop.data.HttpSubscriptionApi(),
            subscriptionRepo = io.openflux.desktop.data.FileSubscriptionRepository(noBackupFilesDir),
            botApi = io.openflux.desktop.data.HttpBotApi(appId = io.openflux.desktop.data.AppIds.valid(BuildConfig.APP_ID)),
            botRepo = io.openflux.desktop.data.FileBotRepository(noBackupFilesDir),
            signIn = signIn,
            settingsPageHost = io.openflux.android.web.AndroidSettingsPageHost,
        )
        // The subscription's list of locations is refreshed in the background.
        container.subscription.start(CoroutineScope(SupervisorJob() + Dispatchers.Default))
        container.bot.start(CoroutineScope(SupervisorJob() + Dispatchers.Default))

        // The JS (goja) script-transport engine is an experimental feature: nothing of it runs
        // until the user turns "Экспериментальные функции" on. Then the scripts shipped in the
        // APK are installed (or upgraded) and the engine is proved to run in-process once; the
        // result lands in logcat under the tag "OpenFluxJS".
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            settings.settings.map { it.experimental }.distinctUntilChanged().collect { on ->
                if (!on) return@collect
                scripts.syncBundled()
                try {
                    val available = Mobile.scriptEngineAvailable()
                    val result = Mobile.scriptEngineSelfTest(cacheDir.absolutePath)
                    Log.i("OpenFluxJS", "engine available=$available selftest=$result")
                } catch (t: Throwable) {
                    Log.e("OpenFluxJS", "engine self-test failed", t)
                }
            }
        }
    }

    /** The sign-in page in a Custom Tab (never a WebView); the system browser when no browser supports Custom Tabs. */
    private fun openSignInPage(url: String) {
        try {
            val tab = androidx.browser.customtabs.CustomTabsIntent.Builder().setShowTitle(true).build()
            tab.intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            tab.launchUrl(this, android.net.Uri.parse(url))
        } catch (e: android.content.ActivityNotFoundException) {
            container.platform.openUrl(url)
        }
    }
}

val Context.openFlux: OpenFluxApplication get() = applicationContext as OpenFluxApplication
