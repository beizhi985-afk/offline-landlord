package com.offlinelandlord.game.network.uno.v5

import com.offlinelandlord.game.network.protocol.v5.V5ProtocolCodec
import com.offlinelandlord.game.network.protocol.v5.V5WireEnvelope
import com.offlinelandlord.game.network.protocol.v5.V5WireType
import com.offlinelandlord.game.network.transport.LanEndpoint
import com.offlinelandlord.game.network.transport.TcpClientTransport
import java.io.Closeable
import java.util.UUID
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Minimal UNO V5 TCP client for integration tests and the future LAN UI adapter. */
class UnoV5Client : Closeable {
    private val transport = TcpClientTransport()
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
    var playerId: String? = null
        private set
    var resumeToken: String? = null
        private set
    var roomCode: String? = null
        private set

    suspend fun connect(host: String, port: Int, roomCode: String, displayName: String): UnoSessionResult<UnoV5RoomView> {
        transport.connect(LanEndpoint(host, port), connectTimeoutMillis = 5_000, readTimeoutMillis = 10_000)
        this.roomCode = roomCode
        val requestId = UUID.randomUUID().toString()
        send(UnoV5PayloadCodec.envelope(V5WireType.JOIN, UnoV5PayloadCodec.encodeJoin(displayName), requestId, roomCode = roomCode))
        val response = V5ProtocolCodec.decode(transport.receive() ?: return UnoSessionResult(false, error = UnoV5ErrorCode.ROOM_NOT_FOUND))
            ?: return UnoSessionResult(false, error = UnoV5ErrorCode.INVALID_ACTION)
        if (response.type == V5WireType.ERROR) return UnoSessionResult(false, error = response.message?.let { runCatching { UnoV5ErrorCode.valueOf(it) }.getOrNull() } ?: UnoV5ErrorCode.ILLEGAL_ACTION, detail = response.message)
        val accepted = response.payload?.let { runCatching { json.decodeFromJsonElement(UnoV5JoinAcceptedPayload.serializer(), it) }.getOrNull() }
            ?: return UnoSessionResult(false, error = UnoV5ErrorCode.INVALID_ACTION)
        playerId = accepted.playerId
        resumeToken = accepted.resumeToken
        transport.setReadTimeoutMillis(0)
        return UnoSessionResult(true, accepted.room)
    }

    fun sendAction(payload: UnoV5ActionPayload, expectedRevision: Long? = null, requestId: String = UUID.randomUUID().toString()) {
        send(UnoV5PayloadCodec.envelope(V5WireType.ACTION, UnoV5PayloadCodec.encodeAction(payload), requestId, playerId, roomCode, resumeToken, expectedRevision))
    }

    suspend fun receive(): V5WireEnvelope? = V5ProtocolCodec.decode(transport.receive() ?: return null)

    private fun send(envelope: V5WireEnvelope) { transport.send(json.encodeToString(V5WireEnvelope.serializer(), envelope)) }
    override fun close() = transport.close()
}
