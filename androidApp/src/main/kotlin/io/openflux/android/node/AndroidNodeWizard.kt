package io.openflux.android.node

import io.openflux.android.web.WebPage
import io.openflux.bridge.mobile.Mobile
import io.openflux.desktop.model.LogLevel
import io.openflux.desktop.model.LogLine
import io.openflux.desktop.model.NewChannel
import io.openflux.desktop.model.NodeDocuments
import io.openflux.desktop.model.NodePlan
import io.openflux.desktop.model.NodeWizardException
import io.openflux.desktop.model.ServerProbe
import io.openflux.desktop.model.SshTarget
import io.openflux.desktop.model.YandexDisk
import io.openflux.desktop.model.YandexDocument
import io.openflux.desktop.service.NodeWizardService
import io.openflux.desktop.ui.BrowserPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.coroutineContext

/**
 * The wizard's server side through the core's Node* calls (SSH, the pinned
 * installer), and the channel's Yandex document created in a WebView. The
 * calls block, so they run off the main thread, one at a time.
 */
class AndroidNodeWizard : NodeWizardService {
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Mutex()
    private val _documentPage = MutableStateFlow<BrowserPage?>(null)
    override val documentPage: StateFlow<BrowserPage?> = _documentPage.asStateFlow()

    private val lineIds = AtomicLong()
    private val _logs = MutableStateFlow<List<LogLine>>(emptyList())
    override val logs: StateFlow<List<LogLine>> = _logs.asStateFlow()

    override suspend fun connect(target: SshTarget): ServerProbe {
        val reply = call("nodeConnect", "${target.host}:${target.port} (${target.user})") {
            Mobile.nodeConnect(
                target.host, target.port.toLong(), target.user,
                target.password, target.privateKey, target.passphrase, target.hostKey,
            )
        }
        return json.decodeFromJsonElement(ServerProbe.serializer(), reply.getValue("probe"))
    }

    override suspend fun newChannel(): NewChannel {
        val reply = call("nodeNewChannel") { Mobile.nodeNewChannel() }
        return NewChannel(reply.string("id"), reply.string("key"))
    }

    override suspend fun plan(channel: String, withCookies: Boolean): NodePlan {
        val reply = call("nodePlan", "channel=$channel withCookies=$withCookies") { Mobile.nodePlan(channel, 0, withCookies) }
        return json.decodeFromJsonElement(NodePlan.serializer(), reply.getValue("plan"))
    }

    override suspend fun apply(channel: NewChannel, documentUrl: String, port: Int, sudoPassword: String, cookieHeader: String) {
        call("nodeApply", "channel=${channel.id} port=$port") {
            Mobile.nodeApply(channel.id, documentUrl, channel.key, port.toLong(), sudoPassword, cookieHeader)
        }
    }

    override suspend fun remove(channel: String, sudoPassword: String) {
        call("nodeRemove", "channel=$channel") { Mobile.nodeRemove(channel, sudoPassword) }
    }

    override suspend fun checkDocument(documentUrl: String) {
        call("nodeCheckDocument") { Mobile.nodeCheckDocument(documentUrl) }
    }

    override suspend fun shareLink(name: String, documentUrl: String, key: String, host: String, port: Int): String =
        withContext(Dispatchers.IO) {
            log(LogLevel.Debug, "мастер: → nodeShareLink $host:$port")
            try {
                Mobile.nodeShareLink(name, documentUrl, key, host, port.toLong()).also {
                    log(LogLevel.Debug, "мастер: ← nodeShareLink $host:$port ok")
                }
            } catch (e: Exception) {
                log(LogLevel.Error, "мастер: ← nodeShareLink $host:$port ошибка: ${e.message}")
                throw NodeWizardException(e.message ?: "Не удалось собрать ссылку канала")
            }
        }

    override suspend fun resolve(host: String): Set<String> = withContext(Dispatchers.IO) {
        runCatching { InetAddress.getAllByName(host).mapNotNull { it.hostAddress }.toSet() }
            .onSuccess { log(LogLevel.Debug, "мастер: resolve $host -> ${it.joinToString()}") }
            .onFailure { log(LogLevel.Warning, "мастер: resolve $host не удался: ${it.message}") }
            .getOrDefault(emptySet())
    }

    /**
     * Opens Yandex in a WebView for the user to sign in, then runs the Disk
     * web client's own calls from the Disk page to create the document with
     * edit access by link. Every cookie is wiped on the way out: the sign-in
     * stays only in the header handed back for the node.
     */
    override suspend fun createDocument(fileName: String, onStep: (String) -> Unit): YandexDocument {
        require(Regex("^[a-z0-9-]{1,64}$").matches(fileName)) { "Неверное имя документа" }
        cancelDocument()
        WebPage.clearCookies()
        val page = WebPage(YandexDisk.START_URL, scripts = true)
        _documentPage.value = page
        try {
            onStep(YandexDisk.SIGN_IN)
            val deadline = System.currentTimeMillis() + YandexDisk.SIGN_IN_TIMEOUT_MS
            while (true) {
                coroutineContext.ensureActive()
                if (page.closed) throw NodeWizardException("Вход в Яндекс отменён")
                if (System.currentTimeMillis() > deadline) throw NodeWizardException("Время на вход в Яндекс вышло")
                if (page.loading || !page.url.startsWith(YandexDisk.DISK_CLIENT)) {
                    delay(1000)
                    continue
                }
                onStep("Создаю документ на Яндекс Диске…")
                val raw = runCatching { page.evaluate(YandexDisk.script(fileName)) }.getOrNull()
                val result = raw?.let { runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull() }
                when (result?.get("state")?.jsonPrimitive?.content) {
                    "done" -> {
                        val url = result["url"]?.jsonPrimitive?.content?.let(NodeDocuments::clean)
                            ?: throw NodeWizardException("Яндекс вернул неожиданную ссылку на документ")
                        return YandexDocument(url, page.cookieHeader(url))
                    }
                    "fail" -> throw NodeWizardException(
                        "Не получилось создать документ: " + (result["error"]?.jsonPrimitive?.content ?: "ошибка Яндекса"),
                    )
                    else -> Unit // still loading, signed out, or the page moved on
                }
                onStep(YandexDisk.SIGN_IN)
                delay(1500)
            }
        } finally {
            _documentPage.value = null
            page.close()
            WebPage.clearCookies()
        }
    }

    override fun cancelDocument() {
        (_documentPage.value as? WebPage)?.close()
    }

    override fun close() {
        cancelDocument()
        log(LogLevel.Info, "мастер: закрываю ядро")
        // Closes SSH, which removes the downloaded installer from the server.
        Thread { Mobile.nodeDisconnect() }.start()
    }

    override fun clearLogs() {
        _logs.value = emptyList()
    }

    override fun note(text: String, level: LogLevel) = log(level, text)

    private fun log(level: LogLevel, text: String) {
        // Negative ids: never collide with ConnectionService's own (positive) ids when merged for the Logs tab.
        val line = LogLine(-lineIds.incrementAndGet(), System.currentTimeMillis(), text, level)
        _logs.update { (it + line).takeLast(MAX_LOG_LINES) }
    }

    private suspend fun call(method: String, summary: String = "", block: () -> String): JsonObject = withContext(Dispatchers.IO) {
        lock.withLock {
            val label = if (summary.isEmpty()) method else "$method $summary"
            log(LogLevel.Debug, "мастер: → $label")
            val reply = runCatching { Json.parseToJsonElement(block()).jsonObject }
                .getOrElse {
                    log(LogLevel.Error, "мастер: ← $label ядро не ответило: ${it.message}")
                    throw NodeWizardException("Ядро OpenFlux не ответило мастеру")
                }
            if (reply["ok"]?.jsonPrimitive?.booleanOrNull != true) {
                val error = reply["error"]?.jsonPrimitive?.content ?: "Ошибка мастера"
                log(LogLevel.Error, "мастер: ← $label ошибка: $error")
                throw NodeWizardException(
                    error,
                    hostKey = reply["hostKey"]?.jsonPrimitive?.content?.takeIf { it.isNotEmpty() },
                    trust = reply.flag("trust"),
                    mismatch = reply.flag("mismatch"),
                    sudo = reply.flag("sudo"),
                    captcha = reply.flag("captcha"),
                )
            }
            log(LogLevel.Debug, "мастер: ← $label ok")
            reply
        }
    }

    private fun JsonObject.string(name: String) =
        this[name]?.jsonPrimitive?.content ?: throw NodeWizardException("Ядро не вернуло $name")

    private fun JsonObject.flag(name: String) = this[name]?.jsonPrimitive?.booleanOrNull == true

    companion object {
        private const val MAX_LOG_LINES = 2000
    }
}
