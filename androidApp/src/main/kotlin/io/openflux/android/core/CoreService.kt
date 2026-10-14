package io.openflux.android.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import io.openflux.android.MainActivity
import io.openflux.android.R
import io.openflux.android.openFlux

/**
 * The foreground service the core runs under while connected: the VPN
 * (the TUN interface only a VpnService may create), the local proxy or the
 * exit node. [AndroidConnectionService] drives it; this class only holds
 * what must belong to a service.
 */
class CoreService : VpnService() {
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val connection = openFlux.connection
        if (intent?.action == ACTION_STOP) {
            connection.disconnect()
            return START_NOT_STICKY
        }
        // startForegroundService() must be answered with startForeground() right away.
        createChannels(this)
        val notification = notification("Подключение…")
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        connection.onServiceStarted(this)
        return START_NOT_STICKY
    }

    /** Another app took the VPN over, or the user turned it off in the system settings. */
    override fun onRevoke() {
        openFlux.connection.onVpnRevoked()
    }

    override fun onDestroy() {
        releaseWakeLock()
        openFlux.connection.onServiceDestroyed(this)
        super.onDestroy()
    }

    /**
     * The TUN interface: all IPv4 through it, DNS to [dns] (answered by the
     * packet tunnel), this app itself outside, since the core's own traffic
     * must not loop back into the tunnel. Android Auto stays outside too: its
     * phone-to-car link breaks when routed through a VPN.
     */
    fun establish(mtu: Int, dns: String): ParcelFileDescriptor? {
        val builder = Builder()
            .setSession("OpenFlux")
            .setMtu(mtu)
            .addAddress("10.10.10.2", 24)
            .addRoute("0.0.0.0", 0)
            .addDnsServer(dns)
            .addDisallowedApplication(packageName)
            .setConfigureIntent(openAppIntent(this))
        for (app in BYPASS_APPS) {
            // Throws for a package that isn't installed.
            try { builder.addDisallowedApplication(app) } catch (e: PackageManager.NameNotFoundException) {}
        }
        if (Build.VERSION.SDK_INT >= 29) builder.setBlocking(true)
        return builder.establish()
    }

    /** An exit node keeps serving clients with the screen off. */
    fun holdWakeLock() {
        if (wakeLock != null) return
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "OpenFlux:exit")
            .apply { acquire() }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    fun update(text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(text))
    }

    fun finish() {
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun notification(text: String): Notification {
        val stop = PendingIntent.getService(
            this, 0, Intent(this, CoreService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Builder(this, CORE_CHANNEL)
            .setContentTitle("OpenFlux")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_openflux_notification)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent(this))
            .addAction(Notification.Action.Builder(Icon.createWithResource(this, R.drawable.ic_power), "Отключить", stop).build())
            .build()
    }

    companion object {
        const val ACTION_STOP = "io.openflux.android.STOP"
        private const val NOTIFICATION_ID = 7
        private const val CAPTCHA_NOTIFICATION_ID = 9
        private const val CORE_CHANNEL = "openflux_core"
        private const val CAPTCHA_CHANNEL = "openflux_captcha"

        /** Apps kept outside the VPN: Android Auto. */
        private val BYPASS_APPS = listOf("com.google.android.projection.gearhead")

        fun createChannels(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CORE_CHANNEL, "Подключение OpenFlux", NotificationManager.IMPORTANCE_LOW))
            manager.createNotificationChannel(NotificationChannel(CAPTCHA_CHANNEL, "Проверка Яндекса", NotificationManager.IMPORTANCE_HIGH))
        }

        fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        /** A Yandex check waits while the app is in the background: tapping opens it. */
        fun notifyCaptcha(context: Context, remote: Boolean, login: Boolean) {
            createChannels(context)
            val title = when {
                remote && login -> "OpenFlux: ноде нужен вход в Яндекс"
                remote -> "OpenFlux: нода просит пройти проверку"
                login -> "OpenFlux: нужен вход в Яндекс"
                else -> "OpenFlux: нужна проверка Яндекса"
            }
            val notification = Notification.Builder(context, CAPTCHA_CHANNEL)
                .setContentTitle(title)
                .setContentText("Нажмите, чтобы открыть проверку")
                .setSmallIcon(R.drawable.ic_openflux_notification)
                .setAutoCancel(true)
                .setContentIntent(openAppIntent(context))
                .build()
            runCatching { context.getSystemService(NotificationManager::class.java).notify(CAPTCHA_NOTIFICATION_ID, notification) }
        }

        fun cancelCaptcha(context: Context) {
            context.getSystemService(NotificationManager::class.java).cancel(CAPTCHA_NOTIFICATION_ID)
        }
    }
}
