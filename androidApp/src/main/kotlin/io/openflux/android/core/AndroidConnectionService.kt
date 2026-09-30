package io.openflux.android.core

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.webkit.CookieManager
import androidx.core.content.ContextCompat
import io.openflux.android.ActivityBridge
import io.openflux.android.openFlux
import io.openflux.android.web.WebPage
import io.openflux.bridge.mobile.Mobile
import io.openflux.desktop.model.AppSettings
import io.openflux.desktop.model.CaptchaPrompt
import io.openflux.desktop.model.ConnectionMode
import io.openflux.desktop.model.ConnectionState
import io.openflux.desktop.model.ExitAddress
import io.openflux.desktop.model.LogLevel
import io.openflux.desktop.model.LogLine
import io.openflux.desktop.model.Profile
import io.openflux.desktop.model.TrafficStats
import io.openflux.desktop.model.YandexDisk
import io.openflux.desktop.service.ConnectionService
import io.openflux.desktop.service.SettingsRepository
import io.openflux.desktop.ui.BrowserPage
import io.openflux.desktop.ui.Format
import io.openflux.desktop.ui.look
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Proxy
import java.net.URL
import java.util.concurrent.atomic.AtomicLong

/**
 * Runs the gomobile core for one profile at a time, the phone's way: the
 * whole device through a VPN, only a local SOCKS5 proxy, or the phone as an
 * exit node. Turns what the core reports (it is polled: logs, link state,
 * counters, Yandex checks) into the UI's state flows, like the desktop's
 * CoreConnectionService does with its child process.
 */
class AndroidConnectionService(
    private val context: Context,
    private val settings: SettingsRepository,
    private val bridge: ActivityBridge,
) : ConnectionService {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    /** connect/disconnect/failures one at a time. */
    private val lifecycle = Mutex()

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    override val state: StateFlow<ConnectionState> = _state.asStateFlow()
    private val _traffic = MutableStateFlow(TrafficStats())
    override val traffic: StateFlow<TrafficStats> = _traffic.asStateFlow()
    private val _exitAddress = MutableStateFlow<ExitAddress>(ExitAddress.Unknown)
    override val exitAddress: StateFlow<ExitAddress> = _exitAddress.asStateFlow()
    private val _logs = MutableStateFlow<List<LogLine>>(emptyList())
    override val logs: StateFlow<List<LogLine>> = _logs.asStateFlow()
    private val _captcha = MutableStateFlow<CaptchaPrompt?>(null)
    override val captcha: StateFlow<CaptchaPrompt?> = _captcha.asStateFlow()
    private val _exitShareLink = MutableStateFlow<String?>(null)
    override val exitShareLink: StateFlow<String?> = _exitShareLink.asStateFlow()
    private val _socksAddress = MutableStateFlow<String?>(null)
    override val socksAddress: StateFlow<String?> = _socksAddress.asStateFlow()
    private val _captchaPage = MutableStateFlow<BrowserPage?>(null)
    override val captchaPage: StateFlow<BrowserPage?> = _captchaPage.asStateFlow()

    private val lineIds = AtomicLong()

    /** How the core runs for a connection. */
    private enum class Kind { Vpn, Proxy, Exit }

    private class Run(val profile: Profile, val settings: AppSettings, val kind: Kind) {
        val sent = AtomicLong()
        val received = AtomicLong()
        val jobs = mutableListOf<Job>()
        @Volatile var tunnel: PacketTunnel? = null
        @Volatile var stopping = false
        /** The carrier's Start returned without an error. */
        @Volatile var started = false
        @Volatile var connectedSince = 0L
        @Volatile var notice = ""
    }

    @Volatile private var run: Run? = null
    @Volatile private var service: CoreService? = null
    /** A run waiting for its service to come up. */
    @Volatile private var pending: Run? = null

    /** The core's pending check shown now, and how the user left it. */
    @Volatile private var captchaUrl: String? = null
    @Volatile private var captchaSolved = false

    init {
        Mobile.setCookieStorePath(File(context.filesDir, "transport-cookies.json").path)
    }

    // ---- connect / disconnect ----

    override fun connect(profile: Profile) {
        scope.launch {
            lifecycle.withLock {
                run?.let { stop(it, restart = true) }
                begin(profile)
            }
        }
    }

    override fun disconnect() {
        scope.launch { lifecycle.withLock { run?.let { stop(it, restart = false) } } }
    }

    private suspend fun begin(profile: Profile) {
        val current = settings.settings.value
        _exitShareLink.value = null
        _exitAddress.value = ExitAddress.Unknown
        _traffic.value = TrafficStats()
        clearCaptcha()
        profile.problems().firstOrNull()?.let {
            fail(profile, it)
            return
        }
        if (profile.stream && current.mode == ConnectionMode.Exit) {
            fail(profile, "Режим без сервера работает только как клиент: выхода в нём нет, сервер заменяет PHP-хостинг")
            return
        }
        val kind = when {
            current.mode == ConnectionMode.Exit -> Kind.Exit
            current.fullTunnel -> Kind.Vpn
            else -> Kind.Proxy
        }
        if (kind == Kind.Vpn && !bridge.prepareVpn(context)) {
            fail(profile, "Android не разрешил OpenFlux включить VPN")
            return
        }
        bridge.requestNotifications()
        val next = Run(profile, current, kind)
        run = next
        _socksAddress.value = if (kind == Kind.Proxy) proxyAddress(current) else null
        _state.value = ConnectionState.Connecting(profile, current.mode, System.currentTimeMillis())
        log(LogLevel.Info, "Запуск ядра: ${profile.name} (${kind.label})")
        val running = service
        if (running != null) {
            launch(next, running)
        } else {
            pending = next
            ContextCompat.startForegroundService(context, Intent(context, CoreService::class.java))
        }
    }

    private suspend fun stop(current: Run, restart: Boolean) {
        current.stopping = true
        _state.value = ConnectionState.Disconnecting(current.profile)
        halt(current)
        if (!restart) {
            log(LogLevel.Info, "Отключено")
            _state.value = ConnectionState.Idle
            finishService()
        }
    }

    /** Stops the core of [current] and forgets it; the state is the caller's. */
    private fun halt(current: Run) {
        current.stopping = true
        current.jobs.forEach { it.cancel() }
        current.tunnel?.close()
        when (current.kind) {
            Kind.Vpn -> Mobile.stop()
            Kind.Proxy -> Mobile.stopProxy()
            Kind.Exit -> Mobile.stopExit()
        }
        drainLogs(current)
        if (run === current) run = null
        if (pending === current) pending = null
        _socksAddress.value = null
        _exitAddress.value = ExitAddress.Unknown
        _exitShareLink.value = null
        _traffic.value = TrafficStats()
        clearCaptcha()
    }

    /** Ends [current] with [message], unless it was replaced or stopped meanwhile. */
    private fun failRun(current: Run, message: String) {
        scope.launch {
            lifecycle.withLock {
                if (run !== current || current.stopping) return@withLock
                halt(current)
                fail(current.profile, message)
                finishService()
            }
        }
    }

    private fun fail(profile: Profile, message: String) {
        log(LogLevel.Error, message)
        _state.value = ConnectionState.Failed(profile, message)
    }

    // ---- the service ----

    /** Lets the service go; a connect from now on starts a fresh one. */
    private fun finishService() {
        val current = service ?: return
        service = null
        current.finish()
    }

    internal fun onServiceStarted(started: CoreService) {
        service = started
        val next = pending
        pending = null
        when {
            next != null -> launch(next, started)
            run == null -> finishService() // started again by the system without a connection
        }
    }

    internal fun onServiceDestroyed(destroyed: CoreService) {
        if (service !== destroyed) return
        service = null
        run?.let { if (!it.stopping) failRun(it, "Android остановил службу OpenFlux") }
    }

    internal fun onVpnRevoked() {
        run?.let { if (it.kind == Kind.Vpn) failRun(it, "VPN выключен: его забрало другое приложение или система") }
    }

    /** Back on screen: a check that waited in a notification shows in the app. */
    fun onAppVisible() {
        CoreService.cancelCaptcha(context)
    }

    private fun launch(current: Run, host: CoreService) {
        if (current.kind == Kind.Exit) host.holdWakeLock()
        current.jobs += scope.launch { monitor(current, host) }
        current.jobs += scope.launch {
            try {
                execute(current, host)
            } catch (e: Exception) {
                if (isActive) failRun(current, e.message ?: "Ядро не запустилось")
            }
        }
    }

    // ---- running the core ----

    private suspend fun execute(current: Run, host: CoreService) {
        var error = startCarrier(current)
        // Some transports (Volga) fail Start outright on a Yandex check; a
        // retry replays the cookies the user got.
        while (error.isNotEmpty() && !current.stopping && awaitCaptcha(current)) error = startCarrier(current)
        if (current.stopping) return
        if (error.isNotEmpty()) {
            failRun(current, error)
            return
        }
        current.started = true
        when (current.kind) {
            Kind.Vpn -> openTunnel(current, host)
            Kind.Exit -> publishExitLink(current)
            Kind.Proxy -> Unit
        }
    }

    private fun startCarrier(current: Run): String {
        val profile = current.profile
        val secret = profile.secret
        Mobile.setDebugLevel(current.settings.debugLevel.toLong())
        // The mode without a server: a PHP node on a web hosting, over cups.online or a Mail.ru document.
        if (profile.stream) {
            val type = profile.transport.cliName
            val url = profile.value.trim()
            return when (current.kind) {
                Kind.Vpn -> Mobile.startStreamPacket(type, url)
                Kind.Proxy -> Mobile.startStreamProxy(type, url, proxyAddress(current.settings), "", "", "")
                Kind.Exit -> "Режим без сервера работает только как клиент"
            }.orEmpty()
        }
        return if (profile.session) {
            val specs = CoreSpecs.session(profile, exit = current.kind == Kind.Exit, directPort = current.settings.exitDirectPort)
            when (current.kind) {
                Kind.Vpn -> Mobile.startSession(specs, secret)
                Kind.Proxy -> Mobile.startSessionProxy(specs, secret, proxyAddress(current.settings), "", "", "")
                Kind.Exit -> Mobile.startSessionExit(specs, secret)
            }
        } else {
            val (type, url, token, uid) = CoreSpecs.classic(profile)
            val codec = profile.codec.cliName
            when (current.kind) {
                Kind.Vpn -> Mobile.start(type, url, secret, codec, token, uid)
                Kind.Proxy -> Mobile.startProxy(type, url, secret, codec, token, uid, proxyAddress(current.settings), "", "", "")
                Kind.Exit -> Mobile.startExit(type, url, secret, codec, token, uid)
            }
        }.orEmpty()
    }

    /** VPN: once the carrier reaches the exit, route the phone's traffic into it. */
    private suspend fun openTunnel(current: Run, host: CoreService) {
        var waited = 0L
        while (!current.stopping && !Mobile.isConnected()) {
            // Only the phone's own checks hold connecting up; the node's are
            // passed once the tunnel runs.
            if (Mobile.pendingCaptchaURL().isNotEmpty() && Mobile.pendingCaptchaProxy().isEmpty()) {
                if (!awaitCaptcha(current)) {
                    if (!current.stopping) failRun(current, "Проверка Яндекса не пройдена")
                    return
                }
                waited = 0
                continue
            }
            if (waited >= CONNECT_TIMEOUT_MS) {
                failRun(current, "Транспорт не подключился за ${CONNECT_TIMEOUT_MS / 1000} секунд")
                return
            }
            delay(250)
            waited += 250
        }
        if (current.stopping) return
        // Read before the VPN takes the default route: the network's own resolver.
        val dns = networkDns()
        val tun = runCatching { host.establish(MTU, dns) }.getOrElse {
            failRun(current, "VPN-интерфейс не создан: ${it.message}")
            return
        } ?: run {
            failRun(current, "Android не создал VPN-интерфейс: разрешение на VPN отозвано")
            return
        }
        val tunnel = PacketTunnel(
            tun, dns, current.sent, current.received,
            onProblem = { current.notice = it },
            onFailure = { failRun(current, it) },
        )
        if (current.stopping) {
            tunnel.close()
            return
        }
        current.tunnel = tunnel
        tunnel.start()
        log(LogLevel.Info, "VPN включён, DNS $dns")
    }

    /** Exit mode: the link clients scan, with direct at this phone's address. */
    private fun publishExitLink(current: Run) {
        val host = current.settings.exitShareHost.trim().ifEmpty { localAddress().orEmpty() }
        runCatching { Mobile.exitShareLink(host, current.profile.name) }
            .onSuccess { if (run === current) _exitShareLink.value = it }
            .onFailure { log(LogLevel.Warning, "Ссылка для клиентов: ${it.message}") }
    }

    /**
     * Polls the core every second for the run's life: log lines, Yandex
     * checks, whether the carrier is up, and the counters behind the speeds.
     */
    private suspend fun monitor(current: Run, host: CoreService) {
        var lastUp = 0L
        var lastDown = 0L
        var lastAt = 0L
        var lastNotification = ""
        while (!current.stopping) {
            drainLogs(current)
            checkCaptcha()
            if (current.started) {
                val up: Boolean
                val totalUp: Long
                val totalDown: Long
                when (current.kind) {
                    Kind.Vpn -> {
                        up = current.tunnel != null && Mobile.isConnected()
                        totalUp = current.sent.get()
                        totalDown = current.received.get()
                    }
                    Kind.Proxy -> {
                        if (!Mobile.proxyIsRunning()) {
                            failRun(current, "Прокси остановился")
                            return
                        }
                        up = Mobile.proxyIsConnected()
                        totalUp = Mobile.proxyBytesSent()
                        totalDown = Mobile.proxyBytesReceived()
                    }
                    Kind.Exit -> {
                        up = Mobile.exitIsRunning()
                        totalUp = Mobile.exitBytesSent()
                        totalDown = Mobile.exitBytesReceived()
                    }
                }
                val now = System.currentTimeMillis()
                val seconds = if (lastAt == 0L) 1.0 else ((now - lastAt) / 1000.0).coerceAtLeast(0.2)
                val upSpeed = if (lastAt == 0L) 0 else ((totalUp - lastUp) / seconds).toLong().coerceAtLeast(0)
                val downSpeed = if (lastAt == 0L) 0 else ((totalDown - lastDown) / seconds).toLong().coerceAtLeast(0)
                lastUp = totalUp; lastDown = totalDown; lastAt = now
                _traffic.value = TrafficStats(
                    upSpeed, downSpeed, totalUp, totalDown,
                    activeTransport = Mobile.currentTransport().orEmpty(),
                    activeTransports = Mobile.currentTransports().orEmpty().split(',').filter(String::isNotEmpty),
                    live = true,
                )
                // A VPN counts as connected once its interface is up.
                if (current.kind != Kind.Vpn || current.tunnel != null) {
                    if (up) markConnected(current) else markReconnecting(current)
                }
            }
            val text = notificationText(current)
            if (text != lastNotification) {
                lastNotification = text
                host.update(text)
            }
            delay(POLL_MS)
        }
    }

    private fun markConnected(current: Run) {
        if (run !== current || current.stopping) return
        val first = current.connectedSince == 0L
        if (first) current.connectedSince = System.currentTimeMillis()
        if (_state.value !is ConnectionState.Connected) {
            _state.value = ConnectionState.Connected(current.profile, current.settings.mode, current.connectedSince)
            if (first) log(LogLevel.Success, if (current.kind == Kind.Exit) "Нода запущена" else "Подключено к ноде")
            else log(LogLevel.Info, "Связь с нодой восстановлена")
            if (current.kind != Kind.Exit) refreshExitAddress()
        }
    }

    private fun markReconnecting(current: Run) {
        if (run !== current || current.stopping) return
        if (_state.value is ConnectionState.Connected) {
            _state.value = ConnectionState.Reconnecting(current.profile, current.settings.mode, current.connectedSince)
            log(
                LogLevel.Warning,
                if (current.kind == Kind.Vpn) "Связь с нодой потеряна: трафик держится в VPN, ядро переподключается"
                else "Связь с нодой потеряна, ядро переподключается",
            )
        }
    }

    private fun notificationText(current: Run): String {
        val look = _state.value.look().title
        val traffic = _traffic.value
        val via = traffic.activeTransport.takeIf { it.isNotEmpty() }?.let { " · $it" }.orEmpty()
        return when (_state.value) {
            is ConnectionState.Connected -> "$look · ↓ ${Format.speed(traffic.downBytesPerSec)}  ↑ ${Format.speed(traffic.upBytesPerSec)}$via"
            else -> if (current.notice.isNotEmpty()) "$look · ${current.notice}" else look
        }
    }

    // ---- exit address ----

    override fun refreshExitAddress() {
        val checked = run ?: return
        if (checked.kind == Kind.Exit) return
        // OpenFlux stays outside its own VPN, so without a proxy of its own it
        // cannot ask ipify through the tunnel; only a browser the user opens can.
        if (checked.kind != Kind.Proxy) {
            if (run === checked) _exitAddress.value = ExitAddress.Unavailable("откройте api.ipify.org в браузере")
            return
        }
        _exitAddress.value = ExitAddress.Checking
        scope.launch {
            val result = runCatching {
                val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress(LOOPBACK, checked.settings.socksPort))
                val connection = URL("https://api.ipify.org").openConnection(proxy) as HttpURLConnection
                connection.connectTimeout = 20_000
                connection.readTimeout = 30_000
                val body = connection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }.trim()
                require(IP.matches(body)) { "неожиданный ответ" }
                ExitAddress.Known(body)
            }.getOrElse { ExitAddress.Unavailable(it.message ?: "нет ответа") }
            if (run === checked) _exitAddress.value = result
        }
    }

    // ---- Yandex checks ----

    /** Shows a check the core just asked for; forgets one it no longer waits on. */
    private fun checkCaptcha() {
        val url = Mobile.pendingCaptchaURL().orEmpty()
        if (url.isEmpty()) {
            if (captchaUrl != null && _captcha.value?.busy != true) clearCaptcha()
            return
        }
        if (url == captchaUrl) return
        captchaUrl = url
        captchaSolved = false
        val proxy = Mobile.pendingCaptchaProxy().orEmpty()
        val reason = Mobile.pendingCaptchaReason().orEmpty()
        val remote = proxy.isNotEmpty()
        log(LogLevel.Warning, if (remote) "Нода просит пройти проверку Яндекса" else "Яндекс просит пройти проверку")
        _captcha.value = CaptchaPrompt(url, reason, remote)
        openPage(url, proxy)
        if (!context.openFlux.visible) CoreService.notifyCaptcha(context, remote, login = reason == "login")
    }

    /** Waits while the core has a check pending; true if the user passed it. */
    private suspend fun awaitCaptcha(current: Run): Boolean {
        if (Mobile.pendingCaptchaURL().isNullOrEmpty()) return false
        while (!current.stopping && !Mobile.pendingCaptchaURL().isNullOrEmpty()) delay(500)
        return captchaSolved && !current.stopping
    }

    override fun openCaptcha() {
        val prompt = _captcha.value ?: return
        openPage(prompt.url, Mobile.pendingCaptchaProxy().orEmpty())
    }

    private fun openPage(url: String, proxy: String) {
        (_captchaPage.value as? WebPage)?.close()
        val page = WebPage(url, proxy)
        _captchaPage.value = page
        _captcha.update { it?.copy(error = "", progress = "") }
        // A real browser is often let through without any check (it targets the
        // core's bot-like client): a regular page that stays put counts as passed.
        scope.launch {
            var settledSince = 0L
            while (_captchaPage.value === page && !page.closed) {
                val settled = !page.loading && page.url.startsWith("https://") && !YandexDisk.isCheckpoint(page.url)
                val now = System.currentTimeMillis()
                if (!settled) settledSince = 0L
                else if (settledSince == 0L) settledSince = now
                else if (now - settledSince >= SETTLE_MS) {
                    log(LogLevel.Info, "Страница Яндекса открылась без проверки, передаю cookies")
                    submitCaptcha()
                    return@launch
                }
                delay(300)
            }
        }
    }

    override fun submitCaptcha() {
        val prompt = _captcha.value ?: return
        if (prompt.busy) return
        val page = _captchaPage.value as? WebPage
        _captcha.update { it?.copy(busy = true, error = "") }
        scope.launch {
            val cookies = CookieManager.getInstance()
            cookies.flush()
            val header = listOfNotNull(prompt.url, page?.url).distinct()
                .mapNotNull { cookies.getCookie(it)?.takeIf(String::isNotEmpty) }
                .joinToString("; ")
            // Set first: the core drops the pending check inside the call, and a
            // start waiting on it must already see it passed.
            captchaSolved = true
            val error = if (header.isEmpty()) "Нет cookies: пройдите проверку на странице выше"
            else Mobile.submitCaptchaCookies(header).orEmpty()
            if (error.isNotEmpty()) {
                captchaSolved = false
                _captcha.update { it?.copy(busy = false, error = error) }
                return@launch
            }
            log(LogLevel.Success, "Проверка пройдена, cookies переданы ${if (prompt.remote) "ноде" else "ядру"}")
            clearCaptcha()
        }
    }

    override fun dismissCaptcha() {
        captchaSolved = false
        Mobile.cancelCaptcha()
        clearCaptcha()
    }

    private fun clearCaptcha() {
        captchaUrl = null
        _captcha.value = null
        (_captchaPage.value as? WebPage)?.close()
        _captchaPage.value = null
        CoreService.cancelCaptcha(context)
    }

    // ---- logs ----

    override fun clearLogs() {
        _logs.value = emptyList()
    }

    private fun drainLogs(current: Run) {
        val text = Mobile.readLogs().orEmpty()
        if (text.isEmpty()) return
        for (raw in text.lineSequence()) {
            val line = raw.trimEnd()
            if (line.isEmpty()) continue
            // The core itself only emits up to the level startCarrier set via
            // Mobile.setDebugLevel, so nothing left to filter here.
            log(levelOf(line), line)
        }
    }

    private fun log(level: LogLevel, text: String) {
        val line = LogLine(lineIds.incrementAndGet(), System.currentTimeMillis(), text, level)
        _logs.update { (it + line).takeLast(MAX_LOG_LINES) }
    }

    override fun shutdown() {
        run?.let(::halt)
    }

    // ---- helpers ----

    private val Kind.label: String
        get() = when (this) {
            Kind.Vpn -> "VPN"
            Kind.Proxy -> "прокси"
            Kind.Exit -> "выходная нода"
        }

    private fun proxyAddress(settings: AppSettings) = "$LOOPBACK:${settings.socksPort}"

    /** The DNS server of the network the phone uses, before the VPN is up. */
    private fun networkDns(): String = runCatching {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        manager.getLinkProperties(manager.activeNetwork)?.dnsServers?.firstOrNull { it is Inet4Address }?.hostAddress
    }.getOrNull() ?: FALLBACK_DNS

    /** This phone's address on its local network (Wi-Fi first), for clients of the exit. */
    private fun localAddress(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback && !it.isVirtual }
            .sortedBy { if (it.name.startsWith("wlan")) 0 else 1 }
            .flatMap { it.inetAddresses.toList() }
            .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }?.hostAddress
    }.getOrNull()

    companion object {
        private const val LOOPBACK = "127.0.0.1"
        private const val MTU = 1400
        private const val FALLBACK_DNS = "1.1.1.1"
        private const val POLL_MS = 1000L
        private const val CONNECT_TIMEOUT_MS = 30_000L
        private const val SETTLE_MS = 1500L
        private const val MAX_LOG_LINES = 5000
        private val IP = Regex("""^[0-9a-fA-F:.]{3,45}$""")
        /** Lines the app's side of the core writes; the rest is the transports' debug log. */
        private val APP_TAGS = listOf("[ANDROID]", "[NODE]", "[ERROR]", "[SUCCESS]", "[WARN")

        fun levelOf(line: String): LogLevel {
            val lower = line.lowercase()
            return when {
                lower.contains("[error]") || lower.contains("fatal") || lower.contains("panic") -> LogLevel.Error
                lower.contains("warning") || lower.contains("[warn") || lower.contains("captcha required") -> LogLevel.Warning
                lower.contains("[success]") || lower.contains("authenticated peer") -> LogLevel.Success
                APP_TAGS.none { line.startsWith(it) } -> LogLevel.Debug
                else -> LogLevel.Info
            }
        }
    }
}
