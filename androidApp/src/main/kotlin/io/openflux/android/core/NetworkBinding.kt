package io.openflux.android.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.ParcelFileDescriptor
import io.openflux.bridge.mobile.NetworkBinder
import io.openflux.desktop.model.NetworkKind
import kotlinx.coroutines.delay
import java.util.concurrent.ConcurrentHashMap

/**
 * The networks a profile's carriers are bound to (bonding: mobile data and
 * Wi-Fi at once). Android drops mobile data while Wi-Fi is up unless an app
 * asks for it, so every network named is requested for as long as the
 * connection runs; the core binds each carrier's sockets to its network
 * through [bindSocket].
 */
internal class NetworkBinding(context: Context, kinds: Set<NetworkKind>) : NetworkBinder, AutoCloseable {
    private val cm = context.getSystemService(ConnectivityManager::class.java)
    private val networks = ConcurrentHashMap<String, Network>()
    private val callbacks = mutableListOf<ConnectivityManager.NetworkCallback>()
    private val wanted = kinds - NetworkKind.Default

    init {
        for (kind in wanted) {
            val transport = when (kind) {
                NetworkKind.Cellular -> NetworkCapabilities.TRANSPORT_CELLULAR
                NetworkKind.Wifi -> NetworkCapabilities.TRANSPORT_WIFI
                NetworkKind.Ethernet -> NetworkCapabilities.TRANSPORT_ETHERNET
                NetworkKind.Default -> continue
            }
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addTransportType(transport)
                .build()
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    networks[kind.cli] = network
                }

                override fun onLost(network: Network) {
                    networks.remove(kind.cli, network)
                }
            }
            cm.requestNetwork(request, callback)
            callbacks += callback
        }
    }

    /** The networks up now, as the core names them. */
    val available: Set<String> get() = networks.keys.toSet()

    /** Waits up to [timeoutMs] for every network asked for to come up. */
    suspend fun await(timeoutMs: Long) {
        var waited = 0L
        while (networks.size < wanted.size && waited < timeoutMs) {
            delay(100)
            waited += 100
        }
    }

    override fun bindSocket(network: String, fd: Long) {
        val net = networks[network] ?: throw Exception("сеть «${label(network)}» не подключена")
        // fromFd duplicates the descriptor; the binding marks the socket
        // itself, which both descriptors share.
        ParcelFileDescriptor.fromFd(fd.toInt()).use { net.bindSocket(it.fileDescriptor) }
    }

    override fun close() {
        callbacks.forEach { runCatching { cm.unregisterNetworkCallback(it) } }
        callbacks.clear()
        networks.clear()
    }

    companion object {
        fun label(cli: String): String = NetworkKind.entries.firstOrNull { it.cli == cli }?.label ?: cli
    }
}
