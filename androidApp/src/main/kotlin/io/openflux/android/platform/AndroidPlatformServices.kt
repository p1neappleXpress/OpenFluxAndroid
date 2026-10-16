package io.openflux.android.platform

import io.openflux.desktop.data.CupsRooms
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import io.openflux.android.ActivityBridge
import io.openflux.android.BuildConfig
import io.openflux.desktop.service.PlatformKind
import io.openflux.desktop.service.PlatformServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom

class AndroidPlatformServices(
    private val context: Context,
    private val bridge: ActivityBridge,
) : PlatformServices {
    private val random = SecureRandom()
    private val clipboard get() = context.getSystemService(ClipboardManager::class.java)

    override val kind = PlatformKind.Android
    override val appVersion: String = BuildConfig.VERSION_NAME
    override val utcOffsetSeconds: Int get() = java.util.TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 1000
    override val deviceName: String
        get() = listOf(android.os.Build.MANUFACTURER, android.os.Build.MODEL).filter { it.isNotBlank() }.joinToString(" ")
            .replaceFirstChar { it.uppercase() }.ifBlank { "Телефон" }
    override val coreVersion: String = BuildConfig.CORE_VERSION
    override val clientRepo: String = RELEASE_REPO

    override val officialScriptKey: String get() = io.openflux.bridge.mobile.Mobile.officialScriptKey()

    override fun inspectTransport(data: ByteArray, sig: ByteArray, pubkeyHex: String): String =
        io.openflux.bridge.mobile.Mobile.inspectTransport(data, sig, pubkeyHex)

    override fun scriptSettings(data: ByteArray, sig: ByteArray, pubkeyHex: String, valuesJson: String, lang: String): String =
        io.openflux.bridge.mobile.Mobile.scriptSettings(data, sig, pubkeyHex, valuesJson, lang)

    override fun checkScriptUpdate(installedJson: String, channel: String): String =
        io.openflux.bridge.mobile.Mobile.checkScriptUpdate(installedJson, channel)

    override fun applyScriptUpdate(installedJson: String, channel: String, dir: String, allowWireBreak: Boolean): String =
        io.openflux.bridge.mobile.Mobile.applyScriptUpdate(installedJson, channel, dir, allowWireBreak)

    override fun rollbackScript(installedJson: String, dir: String): String =
        io.openflux.bridge.mobile.Mobile.rollbackScript(installedJson, dir)

    override fun scriptFingerprint(pubkeyHex: String): String =
        io.openflux.bridge.mobile.Mobile.scriptFingerprint(pubkeyHex)

    override suspend fun fetchBytes(url: String): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 10000
            c.readTimeout = 15000
            c.instanceFollowRedirects = true
            c.setRequestProperty("User-Agent", "OpenFlux-Android")
            // A transport package is a few KB; an answer past the cap is not one.
            if (c.responseCode !in 200..299) return@runCatching null
            c.inputStream.use { body ->
                val out = java.io.ByteArrayOutputStream()
                val buf = ByteArray(8192)
                while (true) {
                    val n = body.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    if (out.size() > MAX_FETCH_BYTES) return@runCatching null
                }
                out.toByteArray()
            }
        }.getOrNull()
    }

    override suspend fun readBytes(pathOrUri: String): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            if (pathOrUri.startsWith("content://")) {
                context.contentResolver.openInputStream(Uri.parse(pathOrUri))?.use { it.readBytes() }
            } else {
                java.io.File(pathOrUri).readBytes()
            }
        }.getOrNull()
    }
    override val systemProxySupported = false
    /** The VPN: the whole phone through the node. */
    override val fullTunnelSupported = true
    override val elevated = true

    override fun restartElevated() = false

    override fun clipboardText(): String? =
        runCatching { clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString() }.getOrNull()

    override fun setClipboardText(text: String) {
        clipboard.setPrimaryClip(ClipData.newPlainText("OpenFlux", text))
    }

    override val shareSupported = true

    override fun shareText(text: String): Boolean = runCatching {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        context.startActivity(Intent.createChooser(send, "OpenFlux").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.isSuccess

    override val clipboardImageSupported = false

    override fun qrFromClipboardImage(): String? = null

    override fun qrFromFile(path: String): String? = runCatching { loadBitmap(Uri.parse(path))?.let(::decodeQr) }.getOrNull()

    override suspend fun pickFile(title: String, extensions: List<String>): String? {
        val images = extensions.isNotEmpty() && extensions.all { it in IMAGE_EXTENSIONS }
        return bridge.pickDocument(if (images) arrayOf("image/*") else arrayOf("*/*"))?.toString()
    }

    override val cameraScanSupported: Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)

    override suspend fun scanQr(): String? = bridge.scanQr()

    override fun readTextFile(path: String, maxBytes: Int): String? = runCatching {
        context.contentResolver.openInputStream(Uri.parse(path))?.use { input ->
            val bytes = input.readNBytesCompat(maxBytes + 1)
            if (bytes.size > maxBytes) null else bytes.toString(Charsets.UTF_8)
        }
    }.getOrNull()

    override fun qrMatrix(text: String): List<BooleanArray> {
        val matrix = QRCodeWriter().encode(
            text, BarcodeFormat.QR_CODE, 0, 0,
            mapOf(EncodeHintType.MARGIN to 0, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M),
        )
        return List(matrix.height) { y -> BooleanArray(matrix.width) { x -> matrix[x, y] } }
    }

    override fun openUrl(url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    override fun newSecret(): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    override fun now(): Long = System.currentTimeMillis()

    override suspend fun newCupsRooms(): String = CupsRooms.create()

    // --- The app's own updates ---

    override val updateInstallSupported: Boolean get() = true

    override suspend fun httpText(url: String): String = io.openflux.desktop.data.UpdateDownloads.text(url)

    override fun updateAsset(assets: List<io.openflux.desktop.model.AppAsset>) =
        AndroidUpdate.pick(assets, android.os.Build.SUPPORTED_ABIS.toList())

    override suspend fun downloadUpdate(asset: io.openflux.desktop.model.AppAsset, sha256: String, onProgress: (Long, Long) -> Unit): String {
        val dir = java.io.File(context.cacheDir, "updates")
        // The APK of an earlier update is of no use any more.
        dir.listFiles()?.filter { it.name != asset.name }?.forEach { it.delete() }
        return io.openflux.desktop.data.UpdateDownloads.file(asset.url, dir, asset.name, sha256, onProgress).absolutePath
    }

    override suspend fun installUpdate(path: String): io.openflux.desktop.model.InstallResult =
        withContext(Dispatchers.IO) { AndroidUpdate.install(context, path) }

    override suspend fun latestRelease(): String? = withContext(Dispatchers.IO) {
        releaseTags().firstOrNull { it.startsWith(TAG_PREFIX) }?.removePrefix(TAG_PREFIX)
    }

    /** The newest release of either channel; GitHub lists them newest first. */
    override suspend fun latestNightly(): String? = withContext(Dispatchers.IO) {
        releaseTags().firstOrNull { it.startsWith(TAG_PREFIX) || it.startsWith(NIGHTLY_PREFIX) }
            ?.removePrefix(TAG_PREFIX)
    }

    /** Tags of the published (non-draft) releases, newest first; empty when offline. */
    private fun releaseTags(): List<String> = runCatching {
        val connection = URL("https://api.github.com/repos/$RELEASE_REPO/releases?per_page=30").openConnection() as HttpURLConnection
        connection.connectTimeout = 8000
        connection.readTimeout = 10000
        connection.setRequestProperty("User-Agent", "OpenFlux-Android")
        val body = connection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        Json.parseToJsonElement(body).jsonArray
            .map { it.jsonObject }
            .filter { it["draft"]?.jsonPrimitive?.content != "true" }
            .map { it["tag_name"]?.jsonPrimitive?.content.orEmpty() }
    }.getOrDefault(emptyList())

    /** The picked image, scaled down so a camera photo does not exhaust memory. */
    private fun loadBitmap(uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / sample > MAX_QR_IMAGE || bounds.outHeight / sample > MAX_QR_IMAGE) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }

    private fun decodeQr(image: Bitmap): String? {
        val pixels = IntArray(image.width * image.height)
        image.getPixels(pixels, 0, image.width, 0, 0, image.width, image.height)
        val bitmap = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(image.width, image.height, pixels)))
        return try {
            MultiFormatReader().decode(
                bitmap,
                mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE), DecodeHintType.TRY_HARDER to true),
            ).text
        } catch (_: NotFoundException) {
            null
        }
    }

    private companion object {
        const val MAX_FETCH_BYTES = 4 * 1024 * 1024
        const val RELEASE_REPO = "p1neappleXpress/OpenFluxAndroid"
        const val TAG_PREFIX = "v"
        /** Nightly test builds are tagged nightly-<date>-<commit>, as prereleases. */
        const val NIGHTLY_PREFIX = "nightly-"
        const val MAX_QR_IMAGE = 2048
        val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "bmp", "gif", "webp")
    }
}

/** InputStream.readNBytes arrived in API 33. */
private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (out.size() < limit) {
        val n = read(buffer, 0, minOf(buffer.size, limit - out.size()))
        if (n < 0) break
        out.write(buffer, 0, n)
    }
    return out.toByteArray()
}
