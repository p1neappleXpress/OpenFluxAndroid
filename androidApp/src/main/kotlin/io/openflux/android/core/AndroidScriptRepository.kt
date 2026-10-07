package io.openflux.android.core

import android.content.Context
import io.openflux.bridge.mobile.Mobile
import io.openflux.desktop.data.FileScriptRepository
import io.openflux.desktop.data.ShippedScript
import java.io.File

/**
 * The Android app's registry of installed JS script transports. The core is a
 * library here, so reading a transport is an in-process call; the transports
 * shipped in the APK (assets/scripts) are installed when the experimental features
 * are turned on and replace the installed copies when a build brings newer ones.
 */
class AndroidScriptRepository(context: Context) :
    FileScriptRepository(File(context.applicationContext.filesDir, "scripts"), { data, sig, key -> Mobile.inspectTransport(data, sig, key) }) {

    private val appContext = context.applicationContext

    val officialKey: String get() = Mobile.officialScriptKey()

    /**
     * Installs the transports shipped in the APK (assets/scripts, the signed .flux packages of
     * OpenFluxTransports) or upgrades the installed copies of them. Not at construction: it runs
     * the script engine, so it waits for the user to turn the experimental features on (see
     * OpenFluxApplication). A transport this build brings that the phone has never been offered
     * is installed too; one the user deleted stays deleted, one they switched off stays off.
     */
    fun syncBundled() {
        syncShipped(shipped(), officialKey) { shippedVersion, installedVersion ->
            Mobile.compareScriptVersions(shippedVersion, installedVersion) > 0
        }
    }

    /** The on-disk file + pinned key for a carrier; null when the script is gone. */
    internal fun carrier(id: String): CoreSpecs.ScriptCarrier? {
        val s = byId(id) ?: return null
        return CoreSpecs.ScriptCarrier(File(dir, s.fileName).absolutePath, s.pubkeyHex, s.id, s.primaryParam?.key)
    }

    /** The packages shipped with this build: .flux files, or bare .js with a detached .sig. */
    private fun shipped(): List<ShippedScript> {
        val assets = appContext.assets
        val names = runCatching { assets.list("scripts")?.toList().orEmpty() }.getOrDefault(emptyList())
            .filter { it.endsWith(".flux") || it.endsWith(".js") }.sorted()
        return names.mapNotNull { file ->
            runCatching {
                val data = assets.open("scripts/$file").use { it.readBytes() }
                val sig = if (file.endsWith(".js")) assets.open("scripts/$file.sig").use { it.readBytes() } else ByteArray(0)
                ShippedScript(file, data, sig)
            }.getOrNull()
        }
    }
}
