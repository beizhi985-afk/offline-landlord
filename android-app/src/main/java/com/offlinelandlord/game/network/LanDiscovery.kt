package com.offlinelandlord.game.network

import java.io.Closeable
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.Collections
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DiscoveredRoom(
    val host: String,
    val port: Int,
    val roomCode: String,
    val roomName: String,
)

class RoomAdvertiser(
    private val roomCode: String,
    roomName: String,
    private val tcpPort: Int,
) : Closeable {
    private val safeRoomName = roomName.replace('|', ' ').take(30)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var socket: DatagramSocket? = null
    private var job: Job? = null

    fun start() {
        if (job != null) return
        job = scope.launch {
            val datagramSocket = DatagramSocket(null).apply {
                reuseAddress = true
                bind(InetSocketAddress(DISCOVERY_PORT))
            }
            socket = datagramSocket
            val buffer = ByteArray(512)
            while (isActive) {
                try {
                    val request = DatagramPacket(buffer, buffer.size)
                    datagramSocket.receive(request)
                    val text = request.data.decodeToString(0, request.length)
                    if (text != DISCOVERY_REQUEST) continue
                    val responseText = "$DISCOVERY_RESPONSE|$roomCode|$safeRoomName|$tcpPort"
                    val responseBytes = responseText.encodeToByteArray()
                    datagramSocket.send(
                        DatagramPacket(responseBytes, responseBytes.size, request.address, request.port),
                    )
                } catch (_: SocketException) {
                    break
                }
            }
        }
    }

    override fun close() {
        socket?.close()
        job?.cancel()
        scope.cancel()
    }
}

object RoomDiscovery {
    suspend fun discover(timeoutMillis: Long = 1_800): List<DiscoveredRoom> = withContext(Dispatchers.IO) {
        val socket = DatagramSocket().apply {
            broadcast = true
            soTimeout = 180
        }
        try {
            val bytes = DISCOVERY_REQUEST.encodeToByteArray()
            broadcastAddresses().forEach { address ->
                runCatching {
                    socket.send(DatagramPacket(bytes, bytes.size, address, DISCOVERY_PORT))
                }
            }

            val rooms = linkedMapOf<String, DiscoveredRoom>()
            val deadline = System.currentTimeMillis() + timeoutMillis
            val buffer = ByteArray(1024)
            while (System.currentTimeMillis() < deadline) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)
                    val parts = packet.data.decodeToString(0, packet.length).split('|')
                    if (parts.size != 4 || parts[0] != DISCOVERY_RESPONSE) continue
                    val port = parts[3].toIntOrNull() ?: continue
                    val host = packet.address.hostAddress ?: continue
                    val room = DiscoveredRoom(host, port, parts[1], parts[2])
                    rooms["$host:$port:${room.roomCode}"] = room
                } catch (_: SocketTimeoutException) {
                    // Poll until the overall deadline so multiple rooms can answer.
                }
            }
            rooms.values.toList()
        } finally {
            socket.close()
        }
    }

    private fun broadcastAddresses(): Set<InetAddress> {
        val addresses = linkedSetOf(InetAddress.getByName("255.255.255.255"))
        runCatching {
            Collections.list(NetworkInterface.getNetworkInterfaces())
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.interfaceAddresses }
                .mapNotNullTo(addresses) { it.broadcast }
        }
        return addresses
    }
}

object LocalAddressFinder {
    fun bestIpv4Address(): String {
        val addresses = runCatching {
            Collections.list(NetworkInterface.getNetworkInterfaces())
                .filter { it.isUp && !it.isLoopback }
                .flatMap { Collections.list(it.inetAddresses) }
                .filterIsInstance<Inet4Address>()
                .filterNot { it.isLoopbackAddress }
        }.getOrDefault(emptyList())

        return addresses.sortedWith(
            compareByDescending<Inet4Address> { it.isSiteLocalAddress }
                .thenByDescending { it.hostAddress?.startsWith("192.168.") == true }
                .thenBy { it.hostAddress },
        ).firstOrNull()?.hostAddress ?: "未检测到，请先开启热点"
    }
}

private const val DISCOVERY_PORT = 39172
private const val DISCOVERY_REQUEST = "DDZ_DISCOVER_V1"
private const val DISCOVERY_RESPONSE = "DDZ_HOST_V1"

