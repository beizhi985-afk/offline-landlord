package com.offlinelandlord.game.network

import com.offlinelandlord.game.core.ActionResult
import com.offlinelandlord.game.core.JoinOutcome
import com.offlinelandlord.game.core.PlayerAction
import com.offlinelandlord.game.core.PlayerGameView
import com.offlinelandlord.game.network.transport.TcpServerTransport
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap

class LanGameServer(
    private val roomCode: String,
    private val onJoin: suspend (name: String, resumeToken: String?) -> JoinOutcome,
    private val onAction: suspend (playerId: String, action: PlayerAction, revision: Long?) -> ActionResult,
    private val onDisconnect: suspend (playerId: String) -> Unit,
    private val viewFor: (playerId: String) -> PlayerGameView?,
) : Closeable {
    private val connectionPlayers = ConcurrentHashMap<String, String>()
    private val playerConnections = ConcurrentHashMap<String, String>()
    private val transport = TcpServerTransport(
        onMessage = ::handleMessage,
        onDisconnect = ::handleDisconnect,
    )

    val port: Int
        get() = transport.port

    fun start(preferredPort: Int = DEFAULT_TCP_PORT) {
        transport.start(preferredPort)
    }

    fun broadcastStates() {
        playerConnections.forEach { (playerId, connectionId) ->
            val view = viewFor(playerId) ?: return@forEach
            runCatching { send(connectionId, WireEnvelope(WireType.STATE, view = view)) }
                .onFailure { transport.disconnect(connectionId) }
        }
    }

    private suspend fun handleMessage(connectionId: String, rawMessage: String) {
        val playerId = connectionPlayers[connectionId]
        if (playerId == null) {
            handleJoin(connectionId, rawMessage)
        } else {
            handlePlayerMessage(connectionId, playerId, rawMessage)
        }
    }

    private suspend fun handleJoin(connectionId: String, rawMessage: String) {
        val joinEnvelope = runCatching {
            wireJson.decodeFromString(WireEnvelope.serializer(), rawMessage)
        }.getOrElse {
            transport.disconnect(connectionId)
            return
        }
        if (joinEnvelope.type != WireType.JOIN || joinEnvelope.roomCode != roomCode) {
            send(connectionId, WireEnvelope(WireType.ERROR, message = "房间码不正确"))
            transport.disconnect(connectionId)
            return
        }
        if (joinEnvelope.protocolVersion != WIRE_PROTOCOL_VERSION) {
            send(connectionId, WireEnvelope(WireType.ERROR, message = "房间版本不兼容，请安装离线斗地主 V3.3"))
            transport.disconnect(connectionId)
            return
        }

        val outcome = onJoin(joinEnvelope.playerName.orEmpty(), joinEnvelope.resumeToken)
        if (!outcome.success || outcome.playerId == null || outcome.resumeToken == null) {
            send(connectionId, WireEnvelope(WireType.ERROR, message = outcome.message))
            transport.disconnect(connectionId)
            return
        }

        val playerId = outcome.playerId
        connectionPlayers[connectionId] = playerId
        val oldConnectionId = playerConnections.put(playerId, connectionId)
        if (oldConnectionId != null && oldConnectionId != connectionId) {
            transport.disconnect(oldConnectionId)
        }
        transport.setReadTimeoutMillis(connectionId, 0)
        send(
            connectionId,
            WireEnvelope(
                type = WireType.JOIN_ACCEPTED,
                protocolVersion = WIRE_PROTOCOL_VERSION,
                requestId = joinEnvelope.requestId,
                playerId = playerId,
                resumeToken = outcome.resumeToken,
                roomCode = roomCode,
                view = viewFor(playerId),
                message = outcome.message,
            ),
        )
        broadcastStates()
    }

    private suspend fun handlePlayerMessage(connectionId: String, playerId: String, rawMessage: String) {
        val envelope = runCatching {
            wireJson.decodeFromString(WireEnvelope.serializer(), rawMessage)
        }.getOrElse {
            send(connectionId, WireEnvelope(WireType.ERROR, message = "无法解析操作"))
            return
        }
        when (envelope.type) {
            WireType.ACTION -> {
                val action = envelope.action
                if (action == null) {
                    send(connectionId, WireEnvelope(WireType.ERROR, requestId = envelope.requestId, message = "缺少操作内容"))
                    return
                }
                val actionResult = onAction(playerId, action, envelope.expectedRevision)
                if (!actionResult.success) {
                    send(
                        connectionId,
                        WireEnvelope(
                            type = WireType.ERROR,
                            requestId = envelope.requestId,
                            message = actionResult.message,
                            view = viewFor(playerId),
                        ),
                    )
                }
            }
            WireType.PING -> send(connectionId, WireEnvelope(WireType.PONG, requestId = envelope.requestId))
            else -> Unit
        }
    }

    private suspend fun handleDisconnect(connectionId: String) {
        val playerId = connectionPlayers.remove(connectionId) ?: return
        if (playerConnections.remove(playerId, connectionId)) onDisconnect(playerId)
    }

    private fun send(connectionId: String, envelope: WireEnvelope) {
        transport.send(
            connectionId,
            wireJson.encodeToString(WireEnvelope.serializer(), envelope),
        )
    }

    override fun close() {
        transport.close()
        connectionPlayers.clear()
        playerConnections.clear()
    }

    companion object {
        const val DEFAULT_TCP_PORT = 39173
    }
}
