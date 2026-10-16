package io.openflux.android

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import io.openflux.android.web.WebBrowserViews
import io.openflux.desktop.model.ConnectionState
import io.openflux.desktop.model.isActive
import io.openflux.desktop.model.ThemeMode
import io.openflux.app.platform.NoScrollbars
import io.openflux.app.platform.Shortcuts
import io.openflux.app.shell.OpenFluxApp
import io.openflux.app.shell.connectChosen

class MainActivity : ComponentActivity() {
    private val app get() = openFlux

    private val vpnConsent = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        app.bridge.onVpnConsent(it.resultCode == RESULT_OK)
    }
    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { app.bridge.onPicked(it) }
    private val scanner = registerForActivityResult(ScanContract()) { app.bridge.onScanned(it.contents) }
    private val notifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        app.bridge.attach(this)
        val container = app.container
        if (savedInstanceState == null) {
            handleLink(intent)
            connectFromTile(intent)
            // "Подключаться при запуске", once per launch of the app, not per screen rotation.
            if (container.settings.settings.value.autoConnect && container.connection.state.value == ConnectionState.Idle) {
                connectChosen(container)
            }
        }
        setContent {
            val settings by container.settings.settings.collectAsState()
            val dark = when (settings.theme) {
                ThemeMode.System -> isSystemInDarkTheme()
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }
            // System bar icons follow the app's theme, which may differ from the system's.
            LaunchedEffect(dark) {
                val bars = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
            }
            OpenFluxApp(container, NoScrollbars, remember { Shortcuts() }, WebBrowserViews)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleLink(intent)
        connectFromTile(intent)
    }

    override fun onStart() {
        super.onStart()
        app.visible = true
        app.connection.onAppVisible()
    }

    override fun onStop() {
        app.visible = false
        super.onStop()
    }

    override fun onDestroy() {
        app.bridge.detach(this)
        super.onDestroy()
    }

    /** The quick-settings tile opened the app to connect: the VPN permission is asked here, then the profile connects. */
    private fun connectFromTile(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_CONNECT, false) != true) return
        intent.removeExtra(EXTRA_CONNECT)
        val container = app.container
        if (!container.connection.state.value.isActive) connectChosen(container)
    }

    /** An openflux:// link from the system camera or a chat goes to the import dialog. */
    private fun handleLink(intent: Intent?) {
        val link = intent?.takeIf { it.action == Intent.ACTION_VIEW }?.dataString ?: return
        if (app.signIn.deliver(link)) return
        if (link.startsWith("openflux://") || io.openflux.desktop.model.OpenFluxBot.looksLike(link)) app.container.incomingLink.value = link
    }

    companion object {
        /** Set by the quick-settings tile: connect the selected profile once the app is open. */
        const val EXTRA_CONNECT = "io.openflux.android.CONNECT"
    }

    internal fun launchVpnConsent(intent: Intent) = vpnConsent.launch(intent)

    internal fun launchPicker(types: Array<String>) = picker.launch(types)

    internal fun launchScanner() = scanner.launch(
        ScanOptions()
            .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            .setPrompt("Наведите камеру на QR-код OpenFlux")
            .setBeepEnabled(false)
            .setOrientationLocked(false),
    )

    internal fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
