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
import io.openflux.android.core.AndroidConnectionService
import io.openflux.android.core.MobileCoreLinks
import io.openflux.android.node.AndroidNodeWizard
import io.openflux.android.platform.AndroidPlatformServices
import io.openflux.desktop.data.FileProfileRepository
import io.openflux.desktop.data.FileSettingsRepository
import io.openflux.desktop.model.AppSettings
import io.openflux.desktop.model.CoreShareLinkCodec
import io.openflux.desktop.service.AppContainer

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
        connection = AndroidConnectionService(this, settings, bridge, accounts)
        container = AppContainer(
            profiles = FileProfileRepository(filesDir),
            settings = settings,
            connection = connection,
            platform = AndroidPlatformServices(this, bridge),
            shareCodec = CoreShareLinkCodec(MobileCoreLinks),
            nodeWizard = AndroidNodeWizard(accounts),
            accounts = accounts,
        )
    }
}

val Context.openFlux: OpenFluxApplication get() = applicationContext as OpenFluxApplication
