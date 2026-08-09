package com.offlinelandlord.game.network

import com.offlinelandlord.game.core.ActionResult
import com.offlinelandlord.game.core.PlayerAction
import com.offlinelandlord.game.core.PlayerGameView
import com.offlinelandlord.game.network.landlord.v5.LandlordV5PayloadCodec
import com.offlinelandlord.game.network.protocol.v5.V5ProtocolCodec
import com.offlinelandlord.game.network.protocol.v5.V5WireEnvelope
import com.offlinelandlord.game.network.protocol.v5.V5WireType
import com.offlinelandlord.game.network.transport.LanEndpoint
import com.offlinelandlord.game.network.transport.TcpClientTransport
import com.offlinelandlord.game.shared.GameType
import java.io.Closeable
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    FAILED,
}

/**
 * Landlord TCP client. New callers use V5 by default; V4 is retained for a room discovered
 * from an older V3.7 host.
 */
class LanGameClient(
    val protocol: LandlordProtocolVersion = LandlordProtocolVersion.V5,
) : Closeable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val connectMutex = Mutex()
    private val _viewState = MutableStateFlow<PlayerGameView?>(null)
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    private val _lastError = MutableStateFlow<String?>(null)

    private var transport: TcpClientTransport? = null
    private var readJob: Job? = null
    private var intentionallyClosed = false
    private var host = ""
    private var port = 0
    private var roomCode = ""
    private var playerName = ""
    private var playerId: String? = null
    private var resumeToken: String? = null

    val viewState: StateFlow<PlayerGameView?> = _viewState.asStateFlow()
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    suspend fun connect(host: String, port: Int, roomCode: String, playerName: String): ActionResult {
        this.host = host.trim()
        this.port = port
        this.roomCode = roomCode.trim()
        this.playerName = playerName.trim().ifBlank { "玩家" }
        intentionallyClosed = false

        if (this.host.isBlank()) return failBeforeConnecting("房主 IP 不能为空")
        if (this.port !in 1..65535) return failBeforeConnecting("端口必须是 1～65535，当前为 ${this.port}")
        if (this.roomCode.length != 6 || this.roomCode.any { !it.isDigit() }) {
            return failBeforeConnecting("请输入正确的六位房间码")
        }
        _connectionState.value = ConnectionState.CONNECTING

        val result = connectMutex.withLock { connectOnce() }
        if (result.success) startReader()
        return result
    }

    suspend fun sendAction(action: PlayerAction): ActionResult = withContext(Dispatchers.IO) {
        if (_connectionState.value != ConnectionState.CONNECTED) {
            return@withContext ActionResult.error("尚未连接房主")
        }
        runCatching {
            val requestId = UUID.randomUUID().toString()
            when (protocol) {
                LandlordProtocolVersion.V4 -> sendV4(
                    WireEnvelope(
                        type = WireType.ACTION,
                        requestId = requestId,
                        playerId = playerId,
                        expectedRevision = _viewState.value?.revision,
                        action = action,
                    ),
                )
                LandlordProtocolVersion.V5 -> sendV5(
                    V5WireEnvelope(
                        gameType = GameType.LANDLORD,
                        type = V5WireType.ACTION,
                        requestId = requestId,
                        playerId = playerId,
                        expectedRevision = _viewState.value?.revision,
                        payload = LandlordV5PayloadCodec.encodeAction(action),
                    ),
                )
            }
            ActionResult.ok()
        }.getOrElse { ActionResult.error("发送失败：${it.message.orEmpty()}") }
    }

    private suspend fun connectOnce(): ActionResult = withContext(Dispatchers.IO) {
        closeSocketOnly()
        runCatching {
            val targetHost = this@LanGameClient.host
            val targetPort = this@LanGameClient.port
            val newTransport = TcpClientTransport()
            transport = newTransport
            newTransport.connect(
                endpoint = LanEndpoint(targetHost, targetPort),
                connectTimeoutMillis = 5_000,
                readTimeoutMillis = 10_000,
            )

            val requestId = UUID.randomUUID().toString()
            when (protocol) {
                LandlordProtocolVersion.V4 -> joinUsingV4(requestId)
                LandlordProtocolVersion.V5 -> joinUsingV5(requestId)
            }
            newTransport.setReadTimeoutMillis(0)
            _connectionState.value = ConnectionState.CONNECTED
            _lastError.value = null
            ActionResult.ok()
        }.getOrElse {
            closeSocketOnly()
            _connectionState.value = ConnectionState.FAILED
            _lastError.value = connectionFailureMessage(it)
            ActionResult.error(_lastError.value.orEmpty())
        }
    }

    private suspend fun joinUsingV4(requestId: String) {
        sendV4(
            WireEnvelope(
                type = WireType.JOIN,
                protocolVersion = WIRE_PROTOCOL_VERSION,
                requestId = requestId,
                playerName = playerName,
                roomCode = roomCode,
                resumeToken = resumeToken,
            ),
        )
        val responseLine = transport?.receive() ?: error("房主没有返回加入结果")
        val response = wireJson.decodeFromString(WireEnvelope.serializer(), responseLine)
        if (response.type == WireType.ERROR) error(response.message ?: "加入房间失败")
        if (response.type != WireType.JOIN_ACCEPTED) error("房主返回了未知响应")
        if (response.protocolVersion != WIRE_PROTOCOL_VERSION) error("房间版本不兼容，请使用离线斗地主 V2")

        playerId = response.playerId ?: error("房主没有分配玩家编号")
        resumeToken = response.resumeToken ?: error("房主没有分配恢复令牌")
        _viewState.value = response.view
    }

    private suspend fun joinUsingV5(requestId: String) {
        sendV5(
            V5WireEnvelope(
                gameType = GameType.LANDLORD,
                type = V5WireType.JOIN,
                requestId = requestId,
                roomCode = roomCode,
                resumeToken = resumeToken,
                payload = LandlordV5PayloadCodec.encodeJoin(playerName),
            ),
        )
        val responseLine = transport?.receive() ?: error("房主没有返回加入结果")
        val response = V5ProtocolCodec.decode(responseLine) ?: error("房主返回了无效的 V5 响应")
        if (response.gameType != GameType.LANDLORD) error("房主返回了不支持的游戏类型")
        if (response.type == V5WireType.ERROR) error(response.message ?: "加入房间失败")
        if (response.type != V5WireType.JOIN_ACCEPTED) error("房主返回了未知响应")

        playerId = response.playerId ?: error("房主没有分配玩家编号")
        resumeToken = response.resumeToken ?: error("房主没有分配恢复令牌")
        _viewState.value = LandlordV5PayloadCodec.decodeJoinAccepted(response.payload)?.view
    }

    private fun connectionFailureMessage(error: Throwable): String = when (error) {
        is SocketTimeoutException -> "连接 $host:$port 超时，请确认仍连接房主热点"
        is ConnectException -> "无法连接房主 $host:$port，请确认房主房间仍然打开"
        is UnknownHostException -> "房主 IP 格式不正确，请只填写数字地址"
        else -> error.message?.takeIf { it.isNotBlank() } ?: "连接房主失败"
    }

    private fun failBeforeConnecting(message: String): ActionResult {
        _connectionState.value = ConnectionState.FAILED
        _lastError.value = message
        return ActionResult.error(message)
    }

    private fun startReader() {
        readJob?.cancel()
        readJob = scope.launch {
            try {
                while (isActive && !intentionallyClosed) {
                    val line = transport?.receive() ?: break
                    when (protocol) {
                        LandlordProtocolVersion.V4 -> handleV4Incoming(line)
                        LandlordProtocolVersion.V5 -> handleV5Incoming(line)
                    }
                }
            } catch (_: Exception) {
                // Reconnection below handles transient hotspot interruptions.
            }
            if (!intentionallyClosed) reconnect()
        }
    }

    private fun handleV4Incoming(line: String) {
        val envelope = wireJson.decodeFromString(WireEnvelope.serializer(), line)
        when (envelope.type) {
            WireType.STATE -> envelope.view?.let { _viewState.value = it }
            WireType.ERROR -> {
                _lastError.value = envelope.message
                envelope.view?.let { _viewState.value = it }
            }
            WireType.PING -> sendV4(WireEnvelope(WireType.PONG, requestId = envelope.requestId))
            else -> Unit
        }
    }

    private fun handleV5Incoming(line: String) {
        val envelope = V5ProtocolCodec.decode(line) ?: error("收到了无效的 V5 数据")
        if (envelope.gameType != GameType.LANDLORD) error("收到了不支持的游戏数据")
        when (envelope.type) {
            V5WireType.STATE -> LandlordV5PayloadCodec.decodeView(envelope.payload)?.let { _viewState.value = it }
            V5WireType.ERROR -> {
                _lastError.value = envelope.message
                LandlordV5PayloadCodec.decodeView(envelope.payload)?.let { _viewState.value = it }
            }
            V5WireType.PING -> sendV5(
                V5WireEnvelope(
                    gameType = GameType.LANDLORD,
                    type = V5WireType.PONG,
                    requestId = envelope.requestId,
                ),
            )
            else -> Unit
        }
    }

    private suspend fun reconnect() {
        _connectionState.value = ConnectionState.RECONNECTING
        closeSocketOnly()
        repeat(5) { attempt ->
            if (intentionallyClosed) return
            delay((attempt + 1) * 1_000L)
            val result = connectMutex.withLock { connectOnce() }
            if (result.success) {
                startReader()
                return
            }
        }
        _connectionState.value = ConnectionState.FAILED
        _lastError.value = "无法重新连接房主，请确认仍连接同一热点"
    }

    private fun sendV4(envelope: WireEnvelope) {
        sendRaw(wireJson.encodeToString(WireEnvelope.serializer(), envelope))
    }

    private fun sendV5(envelope: V5WireEnvelope) {
        sendRaw(
            com.offlinelandlord.game.network.protocol.v5.v5WireJson.encodeToString(
                V5WireEnvelope.serializer(),
                envelope,
            ),
        )
    }

    private fun sendRaw(line: String) {
        transport?.send(line) ?: error("连接已经关闭")
    }

    private fun closeSocketOnly() {
        transport?.close()
        transport = null
    }

    override fun close() {
        intentionallyClosed = true
        readJob?.cancel()
        closeSocketOnly()
        _connectionState.value = ConnectionState.DISCONNECTED
        scope.cancel()
    }

    companion object {
        /** Keeps discovery's compatibility choice inside the network layer, away from UI code. */
        fun forDiscoveredRoom(room: DiscoveredRoom): LanGameClient = LanGameClient(room.protocol)
    }
}
