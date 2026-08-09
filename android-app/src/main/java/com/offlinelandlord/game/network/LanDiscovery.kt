package com.offlinelandlord.game.network

import com.offlinelandlord.game.network.landlord.v5.LandlordV5DiscoveryConfigCodec
import com.offlinelandlord.game.network.protocol.v5.V5DiscoveryCodec
import com.offlinelandlord.game.network.protocol.v5.V5RoomAdvertisement
import com.offlinelandlord.game.network.transport.LanBroadcastAddresses
import com.offlinelandlord.game.network.transport.UdpDatagramTransport
import com.offlinelandlord.game.shared.GameType
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

/** A Landlord room suitable for the existing room list UI. */
data class DiscoveredRoom(
    val host: String,
    val port: Int,
    val roomCode: String,
    val roomName: String,
    val totalRounds: Int,
    val doublingEnabled: Boolean,
    val protocol: LandlordProtocolVersion = LandlordProtocolVersion.V4,
)

internal data class LandlordV4DiscoveryPayload(
    val roomCode: String,
    val roomName: String,
    val tcpPort: Int,
    val totalRounds: Int,
    val doublingEnabled: Boolean,
)

/** Historical V4 text codec. Keep its request, response, and field ordering unchanged. */
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

/**
 * A Landlord host answers both discovery requests: V5 for new clients and V4 for V3.7 clients.
 * The V4 response is deliberately confined to this Landlord compatibility adapter.
 */
class RoomAdvertiser(
    private val roomCode: String,
    private val roomName: String,
    private val tcpPort: Int,
    private val totalRounds: Int,
    private val doublingEnabled: Boolean,
    private val playerCount: () -> Int = { 1 },
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
                    val response = when {
                        LandlordV4DiscoveryCodec.isDiscoveryRequest(text) -> {
                            LandlordV4DiscoveryCodec.encodeResponse(
                                roomCode = roomCode,
                                roomName = roomName,
                                tcpPort = tcpPort,
                                totalRounds = totalRounds,
                                doublingEnabled = doublingEnabled,
                            )
                        }
                        V5DiscoveryCodec.isDiscoveryRequest(text) -> V5DiscoveryCodec.encodeResponse(
                            V5RoomAdvertisement(
                                gameType = GameType.LANDLORD,
                                roomCode = roomCode,
                                roomName = roomName,
                                hostPort = tcpPort,
                                playerCount = playerCount().coerceIn(0, LANDLORD_MAX_PLAYERS),
                                maxPlayers = LANDLORD_MAX_PLAYERS,
                                gameConfig = LandlordV5DiscoveryConfigCodec.encode(
                                    totalRounds = totalRounds,
                                    doublingEnabled = doublingEnabled,
                                ),
                            ),
                        )
                        else -> null
                    } ?: continue
                    datagramTransport.send(response.encodeToByteArray(), request.address, request.port)
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
    /**
     * New Landlord clients ask for both protocols. A V5 entry replaces its equivalent V4 entry,
     * while a V4-only host remains joinable through the compatibility client.
     */
    suspend fun discover(timeoutMillis: Long = 1_800): List<DiscoveredRoom> = withContext(Dispatchers.IO) {
        val transport = UdpDatagramTransport.open(
            broadcast = true,
            receiveTimeoutMillis = 180,
        )
        try {
            val requests = listOf(
                V5DiscoveryCodec.discoveryRequest.encodeToByteArray(),
                LandlordV4DiscoveryCodec.discoveryRequest.encodeToByteArray(),
            )
            LanBroadcastAddresses.all().forEach { address ->
                requests.forEach { request ->
                    runCatching { transport.send(request, address, DISCOVERY_PORT) }
                }
            }

            val rooms = linkedMapOf<String, DiscoveredRoom>()
            val deadline = System.currentTimeMillis() + timeoutMillis
            while (System.currentTimeMillis() < deadline) {
                try {
                    val packet = transport.receive(2_048)
                    val host = packet.address.hostAddress ?: continue
                    val room = decodeLandlordDiscoveredRoom(host, packet.bytes.decodeToString()) ?: continue
                    mergeDiscoveredRoom(rooms, room)
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

/** Decodes only room advertisements a Landlord entry may display. UNO remains hidden/unsupported. */
internal fun decodeLandlordDiscoveredRoom(host: String, message: String): DiscoveredRoom? {
    V5DiscoveryCodec.decodeResponse(message)?.let { advertisement ->
        if (advertisement.gameType != GameType.LANDLORD) return null
        val config = LandlordV5DiscoveryConfigCodec.decode(advertisement.gameConfig) ?: return null
        return DiscoveredRoom(
            host = host,
            port = advertisement.hostPort,
            roomCode = advertisement.roomCode,
            roomName = advertisement.roomName,
            totalRounds = config.totalRounds,
            doublingEnabled = config.doublingEnabled,
            protocol = LandlordProtocolVersion.V5,
        )
    }

    val payload = LandlordV4DiscoveryCodec.decodeResponse(message) ?: return null
    return DiscoveredRoom(
        host = host,
        port = payload.tcpPort,
        roomCode = payload.roomCode,
        roomName = payload.roomName,
        totalRounds = payload.totalRounds,
        doublingEnabled = payload.doublingEnabled,
        protocol = LandlordProtocolVersion.V4,
    )
}

/** V5 supersedes V4 only for the same host/port/room code triple. */
internal fun mergeDiscoveredRoom(
    rooms: MutableMap<String, DiscoveredRoom>,
    candidate: DiscoveredRoom,
) {
    val key = "${candidate.host}:${candidate.port}:${candidate.roomCode}"
    val existing = rooms[key]
    if (existing == null || candidate.protocol.wireVersion > existing.protocol.wireVersion) {
        rooms[key] = candidate
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
private const val LANDLORD_MAX_PLAYERS = 3
