package io.openflux.android.core

import android.os.ParcelFileDescriptor
import io.openflux.bridge.mobile.Mobile
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

/**
 * Moves IPv4 packets between Android's TUN interface and the core: TCP and
 * UDP go through the tunnel (Mobile.send / Mobile.read), DNS queries are
 * answered by [dnsServer] directly from the phone, as the Java app did.
 * [onFailure] reports a broken interface once.
 */
internal class PacketTunnel(
    private val tun: ParcelFileDescriptor,
    private val dnsServer: String,
    private val sent: AtomicLong,
    private val received: AtomicLong,
    private val onProblem: (String) -> Unit,
    private val onFailure: (String) -> Unit,
) {
    @Volatile private var active = true
    private val input = FileInputStream(tun.fileDescriptor)
    private val output = FileOutputStream(tun.fileDescriptor)
    private val outputLock = Any()
    private val dnsWorkers = Executors.newFixedThreadPool(4)

    fun start() {
        thread(name = "openflux-tun-out", isDaemon = true) { readOutgoing() }
        thread(name = "openflux-tun-in", isDaemon = true) { writeIncoming() }
    }

    fun close() {
        if (!active) return
        active = false
        dnsWorkers.shutdownNow()
        // Closing the descriptor wakes the reader blocked on it.
        runCatching { tun.close() }
    }

    private fun readOutgoing() {
        val buffer = ByteArray(32767)
        try {
            while (active) {
                val length = input.read(buffer)
                if (length <= 0) {
                    // A non-blocking interface (Android before 10) has nothing yet.
                    Thread.sleep(2)
                    continue
                }
                val packet = buffer.copyOf(length)
                when {
                    isIpv4UdpDns(packet) -> runCatching { dnsWorkers.execute { forwardDns(packet) } }
                    isIpv4Tcp(packet) || isIpv4Udp(packet) -> {
                        val error = Mobile.send(packet)
                        if (error.isNullOrEmpty()) sent.addAndGet(packet.size.toLong())
                    }
                }
            }
        } catch (e: IOException) {
            if (active) onFailure("Чтение VPN-интерфейса: ${e.message}")
        } catch (_: InterruptedException) {
        }
    }

    private fun writeIncoming() {
        try {
            while (active) {
                val packet = Mobile.read()
                if (packet == null || packet.isEmpty()) {
                    Thread.sleep(2)
                    continue
                }
                inject(packet)
                received.addAndGet(packet.size.toLong())
            }
        } catch (e: IOException) {
            if (active) onFailure("Запись в VPN-интерфейс: ${e.message}")
        } catch (_: InterruptedException) {
        }
    }

    /**
     * Writes a packet to the interface. A full interface queue (ENOBUFS, EAGAIN, ENOMEM) is congestion, not a broken
     * interface: wait a moment and try again, and drop the packet after a few tries as a busy network card would
     * (TCP sends it again). Only other failures end the tunnel.
     */
    private fun inject(packet: ByteArray) {
        var attempt = 0
        while (active) {
            try {
                synchronized(outputLock) { if (active) output.write(packet) }
                return
            } catch (e: IOException) {
                if (!isCongestion(e)) throw e
                if (++attempt > WRITE_RETRIES) {
                    if (active && droppedAtInterface.getAndIncrement() % 200L == 0L) {
                        onProblem("VPN-интерфейс переполнен: пакеты отбрасываются (${e.message})")
                    }
                    return
                }
                Thread.sleep(attempt.toLong())
            }
        }
    }

    private val droppedAtInterface = AtomicLong()

    /** Answers the captured query by relaying it to [dnsServer] over plain UDP. */
    private fun forwardDns(request: ByteArray) {
        val ipHeader = (request[0].toInt() and 0x0f) * 4
        val dnsOffset = ipHeader + 8
        val udpLength = unsignedShort(request, ipHeader + 4)
        if (dnsOffset > request.size || udpLength < 8 || ipHeader + udpLength > request.size) return
        val query = request.copyOfRange(dnsOffset, ipHeader + udpLength)
        try {
            val answer = queryDns(query)
            if (answer == null || answer.isEmpty()) {
                if (active) onProblem("DNS: сервер $dnsServer не ответил")
                return
            }
            inject(dnsResponse(request, answer))
        } catch (e: IOException) {
            if (active) onProblem("DNS: ${e.message}")
        }
    }

    private fun queryDns(query: ByteArray): ByteArray? = try {
        DatagramSocket().use { socket ->
            socket.soTimeout = 5000
            socket.send(DatagramPacket(query, query.size, InetAddress.getByName(dnsServer), 53))
            val buffer = ByteArray(4096)
            val response = DatagramPacket(buffer, buffer.size)
            socket.receive(response)
            buffer.copyOf(response.length)
        }
    } catch (_: SocketTimeoutException) {
        null
    }

    private companion object {
        const val WRITE_RETRIES = 8

        fun isCongestion(e: IOException): Boolean {
            val m = e.message.orEmpty()
            return m.contains("ENOBUFS") || m.contains("EAGAIN") || m.contains("ENOMEM")
        }

        fun isIpv4Tcp(p: ByteArray) = p.size >= 20 && (p[0].toInt() ushr 4) == 4 && (p[9].toInt() and 0xff) == 6

        /** Non-DNS UDP: the exit forwards it like any other packet. */
        fun isIpv4Udp(p: ByteArray) =
            p.size >= 20 && (p[0].toInt() ushr 4) == 4 && (p[9].toInt() and 0xff) == 17 && !isIpv4UdpDns(p)

        fun isIpv4UdpDns(p: ByteArray): Boolean {
            if (p.size < 28 || (p[0].toInt() ushr 4) != 4 || (p[9].toInt() and 0xff) != 17) return false
            val header = (p[0].toInt() and 0x0f) * 4
            return header >= 20 && p.size >= header + 8 && unsignedShort(p, header + 2) == 53
        }

        fun dnsResponse(request: ByteArray, dns: ByteArray): ByteArray {
            val requestHeader = (request[0].toInt() and 0x0f) * 4
            val response = ByteArray(20 + 8 + dns.size)
            response[0] = 0x45
            response[1] = request[1]
            putShort(response, 2, response.size)
            response[4] = request[4]
            response[5] = request[5]
            response[8] = 64
            response[9] = 17
            System.arraycopy(request, 16, response, 12, 4)
            System.arraycopy(request, 12, response, 16, 4)
            putShort(response, 10, checksum(response, 0, 20))
            putShort(response, 20, 53)
            putShort(response, 22, unsignedShort(request, requestHeader))
            putShort(response, 24, 8 + dns.size)
            // A zero UDP checksum is valid for IPv4.
            putShort(response, 26, 0)
            System.arraycopy(dns, 0, response, 28, dns.size)
            return response
        }

        fun checksum(bytes: ByteArray, offset: Int, length: Int): Int {
            var sum = 0L
            var i = offset
            while (i < offset + length) {
                val high = bytes[i].toInt() and 0xff
                val low = if (i + 1 < offset + length) bytes[i + 1].toInt() and 0xff else 0
                sum += ((high shl 8) or low).toLong()
                while (sum and 0xffff0000L != 0L) sum = (sum and 0xffffL) + (sum ushr 16)
                i += 2
            }
            return sum.inv().toInt() and 0xffff
        }

        fun unsignedShort(bytes: ByteArray, offset: Int) =
            ((bytes[offset].toInt() and 0xff) shl 8) or (bytes[offset + 1].toInt() and 0xff)

        fun putShort(bytes: ByteArray, offset: Int, value: Int) {
            bytes[offset] = (value ushr 8).toByte()
            bytes[offset + 1] = value.toByte()
        }
    }
}
