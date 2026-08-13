package com.offlinelandlord.game.network.uno.v5

import com.offlinelandlord.game.network.protocol.v5.V5ProtocolCodec
import com.offlinelandlord.game.network.protocol.v5.V5WireEnvelope
import com.offlinelandlord.game.network.protocol.v5.V5WireType
import com.offlinelandlord.game.network.transport.TcpServerTransport
import java.io.Closeable
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Thin TCP adapter. It owns sockets only; [UnoHostSession] remains the authority. */
class UnoV5HostServer(
    val session: UnoHostSession,
    handshakeReadTimeoutMillis: Int = 10_000,
) : Closeable {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
    private val connectionPlayers = ConcurrentHashMap<String, String>()
    private val playerConnections = ConcurrentHashMap<String, String>()
    private val replacedConnections = ConcurrentHashMap.newKeySet<String>()
    private val transport = TcpServerTransport(::onMessage, ::onDisconnect, handshakeReadTimeoutMillis)

    val port: Int get() = transport.port

    fun start(preferredPort: Int = 0) = transport.start(preferredPort)

    private suspend fun onMessage(connectionId: String, raw: String) {
        val envelope = V5ProtocolCodec.decode(raw) ?: return
        if (envelope.gameType != com.offlinelandlord.game.shared.GameType.UNO) {
            sendError(connectionId, envelope.requestId, UnoV5ErrorCode.INVALID_GAME_TYPE)
            return
        }
        when (envelope.type) {
            V5WireType.JOIN -> handleJoin(connectionId, envelope)
            V5WireType.ACTION -> handleAction(connectionId, envelope)
            V5WireType.PING -> send(connectionId, UnoV5PayloadCodec.envelope(V5WireType.PONG, requestId = envelope.requestId))
            else -> Unit
        }
    }

    private suspend fun handleJoin(connectionId: String, envelope: V5WireEnvelope) {
        if (envelope.roomCode != session.roomCode) {
            sendError(connectionId, envelope.requestId, UnoV5ErrorCode.ROOM_NOT_FOUND)
            return
        }
        val result = if (envelope.playerId != null && envelope.resumeToken != null) {
            session.reconnect(envelope.playerId, envelope.resumeToken)
        } else {
            val payload = UnoV5PayloadCodec.decodeJoin(envelope.payload)
            if (payload == null) {
                sendError(connectionId, envelope.requestId, UnoV5ErrorCode.INVALID_ACTION)
                return
            }
            session.join(payload.displayName)
        }
        if (!result.success || result.value == null) {
            sendError(connectionId, envelope.requestId, result.error ?: UnoV5ErrorCode.ILLEGAL_ACTION, result.detail)
            return
        }
        val accepted = result.value
        playerConnections.put(accepted.playerId, connectionId)?.let { oldConnection ->
            if (oldConnection != connectionId) {
                replacedConnections += oldConnection
                connectionPlayers.remove(oldConnection)
                transport.disconnect(oldConnection)
            }
        }
        connectionPlayers[connectionId] = accepted.playerId
        // The 10-second transport timeout protects only the unauthenticated JOIN handshake.
        // An authenticated UNO human may stay idle for any length of time without being disconnected.
        transport.setReadTimeoutMillis(connectionId, 0)
        send(connectionId, UnoV5PayloadCodec.envelope(V5WireType.JOIN_ACCEPTED, UnoV5PayloadCodec.encodeJoinAccepted(accepted), envelope.requestId, accepted.playerId, session.roomCode, accepted.resumeToken))
        broadcastStates()
    }

    private suspend fun handleAction(connectionId: String, envelope: V5WireEnvelope) {
        val playerId = connectionPlayers[connectionId]
            ?: run { sendError(connectionId, envelope.requestId, UnoV5ErrorCode.PLAYER_NOT_FOUND); return }
        val payload = UnoV5PayloadCodec.decodeAction(envelope.payload)
            ?: run { sendError(connectionId, envelope.requestId, UnoV5ErrorCode.INVALID_ACTION); return }
        val roomAction = when (payload.action) {
            UnoV5ActionType.READY -> session.ready(playerId, envelope.requestId ?: UUID.randomUUID().toString())
            UnoV5ActionType.UNREADY -> session.unready(playerId, envelope.requestId ?: UUID.randomUUID().toString())
            UnoV5ActionType.START_GAME -> session.startGame(playerId, envelope.requestId ?: UUID.randomUUID().toString())
            UnoV5ActionType.ADD_BOT -> session.addBot(playerId)
            UnoV5ActionType.REMOVE_BOT -> session.removeBot(playerId, payload.targetPlayerId.orEmpty())
            else -> null
        }
        if (roomAction != null) {
            if (!roomAction.success) {
                sendError(connectionId, envelope.requestId, roomAction.error ?: UnoV5ErrorCode.ILLEGAL_ACTION, roomAction.detail)
                roomAction.value?.let { sendState(connectionId, it) }
            } else {
                broadcastStates()
            }
            return
        }
        val result = session.submitAction(playerId, envelope.requestId ?: UUID.randomUUID().toString(), envelope.expectedRevision, payload)
        if (!result.success) {
            sendError(connectionId, envelope.requestId, result.error ?: UnoV5ErrorCode.ILLEGAL_ACTION, result.detail)
            result.value?.let { sendState(connectionId, it) }
            return
        }
        broadcastStates()
    }

    private suspend fun onDisconnect(connectionId: String) {
        val playerId = connectionPlayers.remove(connectionId)
            ?: run { replacedConnections.remove(connectionId); return }
        if (replacedConnections.remove(connectionId)) return
        playerConnections.remove(playerId, connectionId)
        session.disconnect(playerId)
        broadcastStates()
    }

    private suspend fun broadcastStates() {
        connectionPlayers.forEach { (connectionId, playerId) ->
            session.viewFor(playerId)?.let { sendState(connectionId, it) }
        }
    }

    private fun sendState(connectionId: String, room: UnoV5RoomView) = send(connectionId, UnoV5PayloadCodec.envelope(V5WireType.STATE, UnoV5PayloadCodec.encodeRoom(room), roomCode = session.roomCode, playerId = room.game?.selfPlayerId))

    private fun sendError(connectionId: String, requestId: String?, code: UnoV5ErrorCode, detail: String? = null) = send(connectionId, UnoV5PayloadCodec.envelope(V5WireType.ERROR, UnoV5PayloadCodec.encodeError(code, detail), requestId = requestId, roomCode = session.roomCode, message = code.name))

    private fun send(connectionId: String, envelope: V5WireEnvelope) {
        runCatching { transport.send(connectionId, json.encodeToString(V5WireEnvelope.serializer(), envelope)) }
    }

    override fun close() {
        transport.close()
        session.close()
    }
}
