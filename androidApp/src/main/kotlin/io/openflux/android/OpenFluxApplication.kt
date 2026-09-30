package io.openflux.android

import android.app.Application
import android.content.Context
import io.openflux.android.core.AndroidConnectionService
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

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
        connection = AndroidConnectionService(this, settings, bridge)
        val phpHosting = PhpHostingService(AndroidPhpTransport(), clock = System::currentTimeMillis)
        container = AppContainer(
            profiles = FileProfileRepository(filesDir),
            settings = settings,
            // Connecting a profile made by the "без сервера" wizard first asks its node on the hosting to run.
            connection = NodeKeepingConnection(connection, phpHosting, CoroutineScope(SupervisorJob() + Dispatchers.Default)),
            platform = AndroidPlatformServices(this, bridge),
            shareCodec = CoreShareLinkCodec(MobileCoreLinks),
            nodeWizard = AndroidNodeWizard(),
            phpHosting = phpHosting,
        )
    }
}

val Context.openFlux: OpenFluxApplication get() = applicationContext as OpenFluxApplication
