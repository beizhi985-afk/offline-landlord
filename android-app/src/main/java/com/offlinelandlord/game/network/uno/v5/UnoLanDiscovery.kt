package com.offlinelandlord.game.network.uno.v5

import com.offlinelandlord.game.network.protocol.v5.V5DiscoveryCodec
import com.offlinelandlord.game.network.protocol.v5.V5RoomAdvertisement
import com.offlinelandlord.game.network.transport.LanBroadcastAddresses
import com.offlinelandlord.game.network.transport.UdpDatagramTransport
import com.offlinelandlord.game.shared.GameType
import java.io.Closeable
import java.net.SocketException
import java.net.SocketTimeoutException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private const val UNO_DISCOVERY_PORT = 39172

class UnoLanRoomAdvertiser(
    private val session: UnoHostSession,
    private val roomName: String,
    private val tcpPort: Int,
) : Closeable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var transport: UdpDatagramTransport? = null
    private var job: Job? = null

    fun start() {
        if (job != null) return
        job = scope.launch {
            val socket = UdpDatagramTransport.bind(UNO_DISCOVERY_PORT)
            transport = socket
            while (isActive) {
                try {
                    val request = socket.receive(512)
                    if (!V5DiscoveryCodec.isDiscoveryRequest(request.bytes.decodeToString())) continue
                    val view = session.viewFor(session.hostPlayerId) ?: continue
                    val advertisement = V5RoomAdvertisement(
                        type = V5DiscoveryCodec.discoveryResponse,
                        gameType = GameType.UNO,
                        roomCode = view.roomCode,
                        roomName = roomName,
                        hostPort = tcpPort,
                        playerCount = view.players.size,
                        maxPlayers = view.maxPlayers,
                        gameConfig = JsonObject(
                            mapOf(
                                "gameMode" to JsonPrimitive(view.gameMode.name),
                                "status" to JsonPrimitive(view.status.name),
                            ),
                        ),
                    )
                    socket.send(V5DiscoveryCodec.encodeResponse(advertisement).encodeToByteArray(), request.address, request.port)
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

object UnoLanDiscovery {
    suspend fun discover(timeoutMillis: Long = 1_800): List<UnoLanRoom> = withContext(Dispatchers.IO) {
        val socket = UdpDatagramTransport.open(broadcast = true, receiveTimeoutMillis = 180)
        try {
            LanBroadcastAddresses.all().forEach { address ->
                runCatching {
                    socket.send(V5DiscoveryCodec.discoveryRequest.encodeToByteArray(), address, UNO_DISCOVERY_PORT)
                }
            }
            val rooms = linkedMapOf<String, UnoLanRoom>()
            val deadline = System.currentTimeMillis() + timeoutMillis
            while (System.currentTimeMillis() < deadline) {
                try {
                    val packet = socket.receive(2_048)
                    val host = packet.address.hostAddress ?: continue
                    val advertisement = V5DiscoveryCodec.decodeResponse(packet.bytes.decodeToString()) ?: continue
                    if (advertisement.gameType != GameType.UNO) continue
                    val mode = advertisement.gameConfig["gameMode"]?.toString()?.trim('"')
                        ?.let { runCatching { UnoV5GameMode.valueOf(it) }.getOrNull() } ?: continue
                    val status = advertisement.gameConfig["status"]?.toString()?.trim('"')
                        ?.let { runCatching { UnoV5RoomStatus.valueOf(it) }.getOrNull() } ?: continue
                    val room = UnoLanRoom(host, advertisement.hostPort, advertisement.roomCode, advertisement.roomName, advertisement.playerCount, advertisement.maxPlayers, mode, status)
                    rooms["${room.host}:${room.port}:${room.roomCode}"] = room
                } catch (_: SocketTimeoutException) {
                    // Keep polling until the overall deadline.
                }
            }
            rooms.values.sortedWith(compareBy<UnoLanRoom> { it.status != UnoV5RoomStatus.WAITING }.thenBy { it.roomName })
        } finally {
            socket.close()
        }
    }
}
