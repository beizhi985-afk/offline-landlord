package com.offlinelandlord.game.network

import com.offlinelandlord.game.core.ActionResult
import com.offlinelandlord.game.core.JoinOutcome
import com.offlinelandlord.game.core.PlayerAction
import com.offlinelandlord.game.core.PlayerGameView
import com.offlinelandlord.game.network.landlord.v5.LandlordV5PayloadCodec
import com.offlinelandlord.game.network.protocol.v5.V5ProtocolCodec
import com.offlinelandlord.game.network.protocol.v5.V5WireEnvelope
import com.offlinelandlord.game.network.protocol.v5.V5WireType
import com.offlinelandlord.game.network.protocol.v5.v5WireJson
import com.offlinelandlord.game.network.transport.TcpServerTransport
import com.offlinelandlord.game.shared.GameType
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap

/**
 * Landlord room server with V4 compatibility and V5 multi-game protocol adapters.
 *
 * The adapters only decode and encode. All player joins, actions, and disconnects converge on
 * the same callbacks supplied by [HostGameSession].
 */
class LanGameServer(
    private val roomCode: String,
    private val onJoin: suspend (name: String, resumeToken: String?) -> JoinOutcome,
    private val onAction: suspend (playerId: String, action: PlayerAction, revision: Long?) -> ActionResult,
    private val onDisconnect: suspend (playerId: String) -> Unit,
    private val viewFor: (playerId: String) -> PlayerGameView?,
) : Closeable {
    private val connectionPlayers = ConcurrentHashMap<String, String>()
    private val playerConnections = ConcurrentHashMap<String, String>()
    private val connectionProtocols = ConcurrentHashMap<String, LandlordProtocolVersion>()
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
            runCatching { sendState(connectionId, view) }
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
        when (V5ProtocolCodec.protocolVersionOf(rawMessage)) {
            WIRE_PROTOCOL_VERSION -> handleV4Join(connectionId, rawMessage)
            LandlordProtocolVersion.V5.wireVersion -> handleV5Join(connectionId, rawMessage)
            else -> transport.disconnect(connectionId)
        }
    }

    private suspend fun handleV4Join(connectionId: String, rawMessage: String) {
        val joinEnvelope = runCatching {
            wireJson.decodeFromString(WireEnvelope.serializer(), rawMessage)
        }.getOrElse {
            transport.disconnect(connectionId)
            return
        }
        if (joinEnvelope.type != WireType.JOIN || joinEnvelope.roomCode != roomCode) {
            sendError(
                connectionId = connectionId,
                protocol = LandlordProtocolVersion.V4,
                requestId = joinEnvelope.requestId,
                message = "房间码不正确",
            )
            transport.disconnect(connectionId)
            return
        }
        if (joinEnvelope.protocolVersion != WIRE_PROTOCOL_VERSION) {
            sendError(
                connectionId = connectionId,
                protocol = LandlordProtocolVersion.V4,
                requestId = joinEnvelope.requestId,
                message = "房间版本不兼容，请安装离线斗地主 V3.3",
            )
            transport.disconnect(connectionId)
            return
        }
        completeJoin(
            connectionId = connectionId,
            protocol = LandlordProtocolVersion.V4,
            playerName = joinEnvelope.playerName.orEmpty(),
            resumeToken = joinEnvelope.resumeToken,
            requestId = joinEnvelope.requestId,
        )
    }

    private suspend fun handleV5Join(connectionId: String, rawMessage: String) {
        val joinEnvelope = V5ProtocolCodec.decode(rawMessage)
        if (joinEnvelope == null) {
            rejectInvalidV5(connectionId, null, "无法解析 V5 加入请求")
            return
        }
        if (joinEnvelope.gameType != GameType.LANDLORD) {
            rejectInvalidV5(
                connectionId,
                joinEnvelope.requestId,
                "暂不支持 ${joinEnvelope.gameType} 联机房间",
                joinEnvelope.gameType,
            )
            return
        }
        if (joinEnvelope.type != V5WireType.JOIN || joinEnvelope.roomCode != roomCode) {
            rejectInvalidV5(connectionId, joinEnvelope.requestId, "房间码不正确")
            return
        }
        val joinPayload = LandlordV5PayloadCodec.decodeJoin(joinEnvelope.payload)
        if (joinPayload == null) {
            rejectInvalidV5(connectionId, joinEnvelope.requestId, "V5 加入请求缺少玩家信息")
            return
        }
        completeJoin(
            connectionId = connectionId,
            protocol = LandlordProtocolVersion.V5,
            playerName = joinPayload.playerName,
            resumeToken = joinEnvelope.resumeToken,
            requestId = joinEnvelope.requestId,
        )
    }

    private suspend fun completeJoin(
        connectionId: String,
        protocol: LandlordProtocolVersion,
        playerName: String,
        resumeToken: String?,
        requestId: String?,
    ) {
        val outcome = onJoin(playerName, resumeToken)
        if (!outcome.success || outcome.playerId == null || outcome.resumeToken == null) {
            sendError(connectionId, protocol, requestId, outcome.message)
            transport.disconnect(connectionId)
            return
        }

        val playerId = outcome.playerId
        connectionPlayers[connectionId] = playerId
        connectionProtocols[connectionId] = protocol
        val oldConnectionId = playerConnections.put(playerId, connectionId)
        if (oldConnectionId != null && oldConnectionId != connectionId) {
            transport.disconnect(oldConnectionId)
        }
        transport.setReadTimeoutMillis(connectionId, 0)
        sendJoinAccepted(
            connectionId = connectionId,
            protocol = protocol,
            requestId = requestId,
            playerId = playerId,
            resumeToken = outcome.resumeToken,
            message = outcome.message,
            view = viewFor(playerId),
        )
        broadcastStates()
    }

    private suspend fun handlePlayerMessage(connectionId: String, playerId: String, rawMessage: String) {
        when (connectionProtocols[connectionId]) {
            LandlordProtocolVersion.V4 -> handleV4PlayerMessage(connectionId, playerId, rawMessage)
            LandlordProtocolVersion.V5 -> handleV5PlayerMessage(connectionId, playerId, rawMessage)
            null -> transport.disconnect(connectionId)
        }
    }

    private suspend fun handleV4PlayerMessage(connectionId: String, playerId: String, rawMessage: String) {
        val envelope = runCatching {
            wireJson.decodeFromString(WireEnvelope.serializer(), rawMessage)
        }.getOrElse {
            sendError(connectionId, LandlordProtocolVersion.V4, null, "无法解析操作")
            return
        }
        when (envelope.type) {
            WireType.ACTION -> {
                val action = envelope.action
                if (action == null) {
                    sendError(connectionId, LandlordProtocolVersion.V4, envelope.requestId, "缺少操作内容")
                    return
                }
                applyPlayerAction(connectionId, playerId, action, envelope.expectedRevision, envelope.requestId)
            }
            WireType.PING -> sendPong(connectionId, LandlordProtocolVersion.V4, envelope.requestId)
            else -> Unit
        }
    }

    private suspend fun handleV5PlayerMessage(connectionId: String, playerId: String, rawMessage: String) {
        val envelope = V5ProtocolCodec.decode(rawMessage)
        if (envelope == null) {
            sendError(connectionId, LandlordProtocolVersion.V5, null, "无法解析 V5 操作")
            return
        }
        if (envelope.gameType != GameType.LANDLORD) {
            sendError(
                connectionId,
                LandlordProtocolVersion.V5,
                envelope.requestId,
                "暂不支持 ${envelope.gameType} 联机房间",
                v5GameType = envelope.gameType,
            )
            return
        }
        when (envelope.type) {
            V5WireType.ACTION -> {
                val action = LandlordV5PayloadCodec.decodeAction(envelope.payload)
                if (action == null) {
                    sendError(connectionId, LandlordProtocolVersion.V5, envelope.requestId, "V5 操作内容无效")
                    return
                }
                applyPlayerAction(connectionId, playerId, action, envelope.expectedRevision, envelope.requestId)
            }
            V5WireType.PING -> sendPong(connectionId, LandlordProtocolVersion.V5, envelope.requestId)
            else -> Unit
        }
    }

    /** The single Landlord business-action path used by both protocol adapters. */
    private suspend fun applyPlayerAction(
        connectionId: String,
        playerId: String,
        action: PlayerAction,
        expectedRevision: Long?,
        requestId: String?,
    ) {
        val result = onAction(playerId, action, expectedRevision)
        if (!result.success) {
            sendError(
                connectionId = connectionId,
                protocol = connectionProtocols[connectionId] ?: return,
                requestId = requestId,
                message = result.message,
                view = viewFor(playerId),
            )
        }
    }

    private fun sendJoinAccepted(
        connectionId: String,
        protocol: LandlordProtocolVersion,
        requestId: String?,
        playerId: String,
        resumeToken: String,
        message: String,
        view: PlayerGameView?,
    ) {
        when (protocol) {
            LandlordProtocolVersion.V4 -> sendV4(
                connectionId,
                WireEnvelope(
                    type = WireType.JOIN_ACCEPTED,
                    protocolVersion = WIRE_PROTOCOL_VERSION,
                    requestId = requestId.orEmpty(),
                    playerId = playerId,
                    resumeToken = resumeToken,
                    roomCode = roomCode,
                    view = view,
                    message = message,
                ),
            )
            LandlordProtocolVersion.V5 -> sendV5(
                connectionId,
                V5WireEnvelope(
                    gameType = GameType.LANDLORD,
                    type = V5WireType.JOIN_ACCEPTED,
                    requestId = requestId,
                    playerId = playerId,
                    roomCode = roomCode,
                    resumeToken = resumeToken,
                    payload = LandlordV5PayloadCodec.encodeJoinAccepted(view),
                    message = message,
                ),
            )
        }
    }

    private fun sendState(connectionId: String, view: PlayerGameView) {
        when (connectionProtocols[connectionId]) {
            LandlordProtocolVersion.V4 -> sendV4(connectionId, WireEnvelope(WireType.STATE, view = view))
            LandlordProtocolVersion.V5 -> sendV5(
                connectionId,
                V5WireEnvelope(
                    gameType = GameType.LANDLORD,
                    type = V5WireType.STATE,
                    payload = LandlordV5PayloadCodec.encodeView(view),
                ),
            )
            null -> Unit
        }
    }

    private fun sendPong(connectionId: String, protocol: LandlordProtocolVersion, requestId: String?) {
        when (protocol) {
            LandlordProtocolVersion.V4 -> sendV4(
                connectionId,
                WireEnvelope(WireType.PONG, requestId = requestId.orEmpty()),
            )
            LandlordProtocolVersion.V5 -> sendV5(
                connectionId,
                V5WireEnvelope(
                    gameType = GameType.LANDLORD,
                    type = V5WireType.PONG,
                    requestId = requestId,
                ),
            )
        }
    }

    private fun sendError(
        connectionId: String,
        protocol: LandlordProtocolVersion,
        requestId: String?,
        message: String,
        view: PlayerGameView? = null,
        v5GameType: GameType = GameType.LANDLORD,
    ) {
        when (protocol) {
            LandlordProtocolVersion.V4 -> sendV4(
                connectionId,
                WireEnvelope(
                    type = WireType.ERROR,
                    requestId = requestId.orEmpty(),
                    message = message,
                    view = view,
                ),
            )
            LandlordProtocolVersion.V5 -> sendV5(
                connectionId,
                V5WireEnvelope(
                    gameType = v5GameType,
                    type = V5WireType.ERROR,
                    requestId = requestId,
                    payload = view?.let(LandlordV5PayloadCodec::encodeView),
                    message = message,
                ),
            )
        }
    }

    private fun rejectInvalidV5(
        connectionId: String,
        requestId: String?,
        message: String,
        gameType: GameType = GameType.LANDLORD,
    ) {
        sendError(connectionId, LandlordProtocolVersion.V5, requestId, message, v5GameType = gameType)
        transport.disconnect(connectionId)
    }

    private fun sendV4(connectionId: String, envelope: WireEnvelope) {
        transport.send(connectionId, wireJson.encodeToString(WireEnvelope.serializer(), envelope))
    }

    private fun sendV5(connectionId: String, envelope: V5WireEnvelope) {
        transport.send(connectionId, v5WireJson.encodeToString(V5WireEnvelope.serializer(), envelope))
    }

    private suspend fun handleDisconnect(connectionId: String) {
        connectionProtocols.remove(connectionId)
        val playerId = connectionPlayers.remove(connectionId) ?: return
        if (playerConnections.remove(playerId, connectionId)) onDisconnect(playerId)
    }

    override fun close() {
        transport.close()
        connectionPlayers.clear()
        playerConnections.clear()
        connectionProtocols.clear()
    }

    companion object {
        const val DEFAULT_TCP_PORT = 39173
    }
}
