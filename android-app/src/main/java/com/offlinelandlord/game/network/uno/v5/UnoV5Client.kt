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

/** UNO V5 client. It owns only the socket; authoritative state remains on the HostSession. */
class UnoV5Client : Closeable {
    private val transport = TcpClientTransport()
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    var playerId: String? = null
        private set
    var resumeToken: String? = null
        private set
    var roomCode: String? = null
        private set
    val connected: Boolean get() = transportConnected

    private var transportConnected = false
    private var savedHost: String? = null
    private var savedPort: Int? = null
    private var savedDisplayName: String = "玩家"

    suspend fun connect(host: String, port: Int, roomCode: String, displayName: String): UnoSessionResult<UnoV5RoomView> =
        connectInternal(host, port, roomCode, displayName, null, null)

    suspend fun resume(
        host: String,
        port: Int,
        roomCode: String,
        playerId: String,
        resumeToken: String,
        displayName: String = "玩家",
    ): UnoSessionResult<UnoV5RoomView> = connectInternal(host, port, roomCode, displayName, playerId, resumeToken)

    suspend fun reconnect(): UnoSessionResult<UnoV5RoomView> {
        val host = savedHost ?: return failure(UnoV5ErrorCode.ROOM_NOT_FOUND)
        val port = savedPort ?: return failure(UnoV5ErrorCode.ROOM_NOT_FOUND)
        val code = roomCode ?: return failure(UnoV5ErrorCode.ROOM_NOT_FOUND)
        val id = playerId ?: return failure(UnoV5ErrorCode.PLAYER_NOT_FOUND)
        val token = resumeToken ?: return failure(UnoV5ErrorCode.INVALID_RESUME_TOKEN)
        return connectInternal(host, port, code, savedDisplayName, id, token)
    }

    fun sendAction(
        payload: UnoV5ActionPayload,
        expectedRevision: Long? = null,
        requestId: String = UUID.randomUUID().toString(),
    ) {
        send(UnoV5PayloadCodec.envelope(V5WireType.ACTION, UnoV5PayloadCodec.encodeAction(payload), requestId, playerId, roomCode, resumeToken, expectedRevision))
    }

    suspend fun receive(): V5WireEnvelope? = V5ProtocolCodec.decode(transport.receive() ?: return null)

    private suspend fun connectInternal(
        host: String,
        port: Int,
        roomCode: String,
        displayName: String,
        resumePlayerId: String?,
        resumeToken: String?,
    ): UnoSessionResult<UnoV5RoomView> {
        return runCatching {
            closeSocketOnly()
            transport.connect(LanEndpoint(host, port), connectTimeoutMillis = 5_000, readTimeoutMillis = 10_000)
            transportConnected = true
            this.roomCode = roomCode
            savedHost = host
            savedPort = port
            savedDisplayName = displayName
            val requestId = UUID.randomUUID().toString()
            send(UnoV5PayloadCodec.envelope(
                V5WireType.JOIN,
                UnoV5PayloadCodec.encodeJoin(displayName),
                requestId,
                resumePlayerId,
                roomCode,
                resumeToken,
            ))
            val response = V5ProtocolCodec.decode(transport.receive() ?: error("未收到房间响应"))
                ?: error("收到无效的 V5 响应")
            if (response.type == V5WireType.ERROR) {
                val code = response.message?.let { runCatching { UnoV5ErrorCode.valueOf(it) }.getOrNull() }
                    ?: UnoV5ErrorCode.ILLEGAL_ACTION
                return@runCatching failure<UnoV5RoomView>(code, response.message)
            }
            if (response.type != V5WireType.JOIN_ACCEPTED) error("房主返回了未知响应")
            val accepted = response.payload?.let { json.decodeFromJsonElement(UnoV5JoinAcceptedPayload.serializer(), it) }
                ?: error("房主未返回加入结果")
            playerId = accepted.playerId
            this.resumeToken = accepted.resumeToken
            transport.setReadTimeoutMillis(0)
            UnoSessionResult(true, accepted.room)
        }.getOrElse { error ->
            closeSocketOnly()
            failure(UnoV5ErrorCode.ROOM_NOT_FOUND, error.message)
        }
    }

    private fun send(envelope: V5WireEnvelope) {
        transport.send(json.encodeToString(V5WireEnvelope.serializer(), envelope))
    }

    private fun closeSocketOnly() {
        transport.close()
        transportConnected = false
    }

    private fun <T> failure(code: UnoV5ErrorCode, detail: String? = null): UnoSessionResult<T> =
        UnoSessionResult(false, error = code, detail = detail)

    override fun close() = closeSocketOnly()
}
