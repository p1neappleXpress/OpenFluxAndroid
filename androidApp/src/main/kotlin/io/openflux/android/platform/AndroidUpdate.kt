package io.openflux.android.platform

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import io.openflux.desktop.model.AppAsset
import io.openflux.desktop.model.InstallResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Which APK of a release this phone installs, and how the system's installer is started. */
object AndroidUpdate {
    /** The APK built for the phone's own CPU (first of [abis] that has one), else the universal one. */
    fun pick(assets: List<AppAsset>, abis: List<String>): AppAsset? {
        for (abi in abis) {
            assets.firstOrNull { it.name.endsWith("-androidApp-$abi-release.apk") }?.let { return it }
        }
        return assets.firstOrNull { it.name.endsWith("-androidApp-universal-release.apk") }
    }

    /**
     * Hands the APK at [path] to the system's installer, which asks the person to confirm (an update of an
     * app they already have, signed by the same key). First time, Android wants "install unknown apps"
     * allowed for OpenFlux: its settings page opens and the person taps "Установить" again.
     */
    suspend fun install(context: Context, path: String): InstallResult {
        val file = File(path)
        if (!file.isFile) return InstallResult(false, "Файл обновления пропал: скачайте заново")
        val pm = context.packageManager
        if (!pm.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return InstallResult(false, "Разрешите OpenFlux устанавливать приложения и нажмите «Повторить».")
        }
        val installer = pm.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(context.packageName)
        if (Build.VERSION.SDK_INT >= 31) params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        val id = installer.createSession(params)
        val done = CompletableDeferred<InstallResult>()
        waiting[id] = done
        installer.openSession(id).use { session ->
            file.inputStream().use { input ->
                session.openWrite("openflux-update.apk", 0, file.length()).use { output ->
                    input.copyTo(output)
                    session.fsync(output)
                }
            }
            val result = PendingIntent.getBroadcast(
                context, id,
                Intent(context, UpdateInstallReceiver::class.java).setPackage(context.packageName),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            session.commit(result.intentSender)
        }
        // The installer answers through [UpdateInstallReceiver]: the confirmation, then done or why not. On success
        // Android restarts the app, so an answer only arrives when it did not work (or was cancelled).
        val answer = withTimeoutOrNull(15 * 60_000L) { done.await() }
        waiting.remove(id)
        return answer ?: InstallResult(true, "Жду подтверждения установки в окне Android.")
    }

    private val waiting = ConcurrentHashMap<Int, CompletableDeferred<InstallResult>>()

    internal fun answer(sessionId: Int, status: Int, message: String?) {
        val result = when (status) {
            PackageInstaller.STATUS_SUCCESS -> InstallResult(true, "Обновлено.")
            PackageInstaller.STATUS_FAILURE_ABORTED -> InstallResult(false, "Установка отменена. Нажмите «Повторить», когда будете готовы.")
            PackageInstaller.STATUS_FAILURE_CONFLICT, PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
                InstallResult(false, "Android не принял обновление: оно подписано другим ключом. Скачайте приложение с GitHub заново.")
            PackageInstaller.STATUS_FAILURE_STORAGE -> InstallResult(false, "Не хватает места для обновления.")
            else -> InstallResult(false, "Android не смог установить обновление${message?.let { ": $it" } ?: ""}.")
        }
        waiting[sessionId]?.complete(result)
    }
}

/** The installer's answer: it needs the person's confirmation, which is a screen it gives us to start. */
class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        if (status != PackageInstaller.STATUS_PENDING_USER_ACTION) {
            AndroidUpdate.answer(intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1), status, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE))
            return
        }
        @Suppress("DEPRECATION")
        val confirm = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java) else intent.getParcelableExtra(Intent.EXTRA_INTENT)
        confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let { runCatching { context.startActivity(it) } }
    }
}
