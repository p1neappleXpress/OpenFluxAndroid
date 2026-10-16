package io.openflux.android.platform

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import io.openflux.android.MainActivity
import io.openflux.android.OpenFluxApplication
import io.openflux.android.R
import io.openflux.desktop.model.ConnectionState
import io.openflux.desktop.model.isActive
import io.openflux.app.shell.connectChosen
import io.openflux.app.shell.stopConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * The quick-settings tile "OpenFlux": one tap connects the selected profile or disconnects, and the tile shows
 * where things stand. When Android still has to ask for the VPN permission (or there is no profile yet), the
 * tap opens the app instead, which can ask.
 */
class ConnectTileService : TileService() {
    private var scope: CoroutineScope? = null

    private val app get() = application as OpenFluxApplication

    override fun onStartListening() {
        super.onStartListening()
        val container = app.container
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        scope = s
        s.launch {
            combine(container.connection.state, container.profiles.profiles, container.settings.settings) { state, profiles, settings ->
                val selected = profiles.firstOrNull { it.id == settings.selectedProfileId } ?: profiles.firstOrNull()
                Triple(state, selected?.name, profiles.isNotEmpty())
            }.collect { (state, name, hasProfile) -> render(state, name, hasProfile) }
        }
    }

    override fun onStopListening() {
        scope?.cancel()
        scope = null
        super.onStopListening()
    }

    private fun render(state: ConnectionState, profileName: String?, hasProfile: Boolean) {
        val tile = qsTile ?: return
        tile.label = "OpenFlux"
        tile.state = if (state.isActive) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.icon = Icon.createWithResource(this, R.drawable.ic_power)
        if (Build.VERSION.SDK_INT >= 29) {
            tile.subtitle = when (state) {
                is ConnectionState.Connected -> profileName ?: "Подключено"
                is ConnectionState.Connecting -> "Подключение…"
                is ConnectionState.Reconnecting -> "Переподключение…"
                is ConnectionState.Disconnecting -> "Отключение…"
                is ConnectionState.Failed -> "Не удалось подключиться"
                ConnectionState.Idle -> if (hasProfile) profileName ?: "Отключено" else "Добавьте подключение"
            }
        }
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        val container = app.container
        if (container.connection.state.value.isActive) {
            stopConnection(container)
            return
        }
        val hasProfile = container.profiles.profiles.value.isNotEmpty()
        // The permission dialog and an empty list are the app's to deal with.
        if (!hasProfile || VpnService.prepare(this) != null) {
            openApp(connectAfter = hasProfile)
            return
        }
        connectChosen(container)
    }

    private fun openApp(connectAfter: Boolean) {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(MainActivity.EXTRA_CONNECT, connectAfter)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
