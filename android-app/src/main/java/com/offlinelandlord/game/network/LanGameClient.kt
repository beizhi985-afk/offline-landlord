package com.offlinelandlord.game.network

import com.offlinelandlord.game.core.ActionResult
import com.offlinelandlord.game.core.PlayerAction
import com.offlinelandlord.game.core.PlayerGameView
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.Closeable
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket
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

class LanGameClient : Closeable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val connectMutex = Mutex()
    private val writeLock = Any()
    private val _viewState = MutableStateFlow<PlayerGameView?>(null)
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    private val _lastError = MutableStateFlow<String?>(null)

    private var socket: Socket? = null
    private var reader: BufferedReader? = null
    private var writer: BufferedWriter? = null
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
        _connectionState.value = ConnectionState.CONNECTING

        val result = connectMutex.withLock { connectOnce() }
        if (result.success) startReader()
        return result
    }

    fun sendAction(action: PlayerAction): ActionResult {
        if (_connectionState.value != ConnectionState.CONNECTED) return ActionResult.error("尚未连接房主")
        return runCatching {
            send(
                WireEnvelope(
                    type = WireType.ACTION,
                    requestId = UUID.randomUUID().toString(),
                    playerId = playerId,
                    expectedRevision = _viewState.value?.revision,
                    action = action,
                ),
            )
            ActionResult.ok()
        }.getOrElse { ActionResult.error("发送失败：${it.message.orEmpty()}") }
    }

    private suspend fun connectOnce(): ActionResult = withContext(Dispatchers.IO) {
        closeSocketOnly()
        runCatching {
            val newSocket = Socket().apply {
                connect(InetSocketAddress(host, port), 5_000)
                tcpNoDelay = true
                keepAlive = true
                soTimeout = 10_000
            }
            val newReader = BufferedReader(InputStreamReader(newSocket.getInputStream(), Charsets.UTF_8))
            val newWriter = BufferedWriter(OutputStreamWriter(newSocket.getOutputStream(), Charsets.UTF_8))
            socket = newSocket
            reader = newReader
            writer = newWriter

            val requestId = UUID.randomUUID().toString()
            send(
                WireEnvelope(
                    type = WireType.JOIN,
                    protocolVersion = WIRE_PROTOCOL_VERSION,
                    requestId = requestId,
                    playerName = playerName,
                    roomCode = roomCode,
                    resumeToken = resumeToken,
                ),
            )
            val responseLine = newReader.readLine() ?: error("房主没有返回加入结果")
            val response = wireJson.decodeFromString(WireEnvelope.serializer(), responseLine)
            if (response.type == WireType.ERROR) error(response.message ?: "加入房间失败")
            if (response.type != WireType.JOIN_ACCEPTED) error("房主返回了未知响应")
            if (response.protocolVersion != WIRE_PROTOCOL_VERSION) error("房间版本不兼容，请使用离线斗地主 V2")

            playerId = response.playerId ?: error("房主没有分配玩家编号")
            resumeToken = response.resumeToken ?: error("房主没有分配恢复令牌")
            _viewState.value = response.view
            newSocket.soTimeout = 0
            _connectionState.value = ConnectionState.CONNECTED
            _lastError.value = null
            ActionResult.ok(response.message.orEmpty())
        }.getOrElse {
            closeSocketOnly()
            _connectionState.value = ConnectionState.FAILED
            _lastError.value = it.message ?: "连接失败"
            ActionResult.error(_lastError.value.orEmpty())
        }
    }

    private fun startReader() {
        readJob?.cancel()
        readJob = scope.launch {
            try {
                while (isActive && !intentionallyClosed) {
                    val line = reader?.readLine() ?: break
                    val envelope = wireJson.decodeFromString(WireEnvelope.serializer(), line)
                    when (envelope.type) {
                        WireType.STATE -> envelope.view?.let { _viewState.value = it }
                        WireType.ERROR -> {
                            _lastError.value = envelope.message
                            envelope.view?.let { _viewState.value = it }
                        }
                        WireType.PING -> send(WireEnvelope(WireType.PONG, requestId = envelope.requestId))
                        else -> Unit
                    }
                }
            } catch (_: Exception) {
                // Reconnection below handles transient hotspot interruptions.
            }
            if (!intentionallyClosed) reconnect()
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

    private fun send(envelope: WireEnvelope) {
        val line = wireJson.encodeToString(WireEnvelope.serializer(), envelope)
        synchronized(writeLock) {
            val activeWriter = writer ?: error("连接已经关闭")
            activeWriter.write(line)
            activeWriter.newLine()
            activeWriter.flush()
        }
    }

    private fun closeSocketOnly() {
        runCatching { socket?.close() }
        socket = null
        reader = null
        writer = null
    }

    override fun close() {
        intentionallyClosed = true
        readJob?.cancel()
        closeSocketOnly()
        _connectionState.value = ConnectionState.DISCONNECTED
        scope.cancel()
    }
}
