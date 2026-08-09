package com.offlinelandlord.game.network

import com.offlinelandlord.game.network.transport.LanBroadcastAddresses
import com.offlinelandlord.game.network.transport.UdpDatagramTransport
import java.io.Closeable
import java.net.Inet4Address
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
    val totalRounds: Int,
    val doublingEnabled: Boolean,
)

internal data class LandlordV4DiscoveryPayload(
    val roomCode: String,
    val roomName: String,
    val tcpPort: Int,
    val totalRounds: Int,
    val doublingEnabled: Boolean,
)

internal object LandlordV4DiscoveryCodec {
    const val discoveryRequest = "DDZ_DISCOVER_V4"
    private const val discoveryResponse = "DDZ_HOST_V4"

    fun isDiscoveryRequest(message: String): Boolean = message == discoveryRequest

    fun encodeResponse(
        roomCode: String,
        roomName: String,
        tcpPort: Int,
        totalRounds: Int,
        doublingEnabled: Boolean,
    ): String {
        val safeRoomName = roomName.replace('|', ' ').take(30)
        return "$discoveryResponse|$roomCode|$safeRoomName|$tcpPort|$totalRounds|$doublingEnabled"
    }

    fun decodeResponse(message: String): LandlordV4DiscoveryPayload? {
        val parts = message.split('|')
        if (parts.size != 6 || parts[0] != discoveryResponse) return null
        val port = parts[3].toIntOrNull() ?: return null
        val totalRounds = parts[4].toIntOrNull()?.takeIf { it == 12 || it == 24 } ?: return null
        val doublingEnabled = parts[5].toBooleanStrictOrNull() ?: return null
        return LandlordV4DiscoveryPayload(
            roomCode = parts[1],
            roomName = parts[2],
            tcpPort = port,
            totalRounds = totalRounds,
            doublingEnabled = doublingEnabled,
        )
    }
}

class RoomAdvertiser(
    private val roomCode: String,
    private val roomName: String,
    private val tcpPort: Int,
    private val totalRounds: Int,
    private val doublingEnabled: Boolean,
) : Closeable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var transport: UdpDatagramTransport? = null
    private var job: Job? = null

    fun start() {
        if (job != null) return
        job = scope.launch {
            val datagramTransport = UdpDatagramTransport.bind(DISCOVERY_PORT)
            transport = datagramTransport
            while (isActive) {
                try {
                    val request = datagramTransport.receive(512)
                    val text = request.bytes.decodeToString()
                    if (!LandlordV4DiscoveryCodec.isDiscoveryRequest(text)) continue
                    val responseBytes = LandlordV4DiscoveryCodec.encodeResponse(
                        roomCode = roomCode,
                        roomName = roomName,
                        tcpPort = tcpPort,
                        totalRounds = totalRounds,
                        doublingEnabled = doublingEnabled,
                    ).encodeToByteArray()
                    datagramTransport.send(responseBytes, request.address, request.port)
                } catch (_: SocketException) {
                    break
                }
            }
        }
    }

    override fun close() {
        transport?.close()
        job?.cancel()
        scope.cancel()
    }
}

object RoomDiscovery {
    suspend fun discover(timeoutMillis: Long = 1_800): List<DiscoveredRoom> = withContext(Dispatchers.IO) {
        val transport = UdpDatagramTransport.open(
            broadcast = true,
            receiveTimeoutMillis = 180,
        )
        try {
            val bytes = LandlordV4DiscoveryCodec.discoveryRequest.encodeToByteArray()
            LanBroadcastAddresses.all().forEach { address ->
                runCatching { transport.send(bytes, address, DISCOVERY_PORT) }
            }

            val rooms = linkedMapOf<String, DiscoveredRoom>()
            val deadline = System.currentTimeMillis() + timeoutMillis
            while (System.currentTimeMillis() < deadline) {
                try {
                    val packet = transport.receive(1024)
                    val payload = LandlordV4DiscoveryCodec.decodeResponse(packet.bytes.decodeToString()) ?: continue
                    val host = packet.address.hostAddress ?: continue
                    val room = DiscoveredRoom(
                        host = host,
                        port = payload.tcpPort,
                        roomCode = payload.roomCode,
                        roomName = payload.roomName,
                        totalRounds = payload.totalRounds,
                        doublingEnabled = payload.doublingEnabled,
                    )
                    rooms["$host:${room.port}:${room.roomCode}"] = room
                } catch (_: SocketTimeoutException) {
                    // Poll until the overall deadline so multiple rooms can answer.
                }
            }
            rooms.values.toList()
        } finally {
            transport.close()
        }
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
