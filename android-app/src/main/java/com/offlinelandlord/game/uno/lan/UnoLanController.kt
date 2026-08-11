package com.offlinelandlord.game.uno.lan

import com.offlinelandlord.game.network.ConnectionState
import com.offlinelandlord.game.network.LocalAddressFinder
import com.offlinelandlord.game.network.uno.v5.UnoHostSession
import com.offlinelandlord.game.network.uno.v5.UnoLanDiscovery
import com.offlinelandlord.game.network.uno.v5.UnoLanRoom
import com.offlinelandlord.game.network.uno.v5.UnoLanRoomAdvertiser
import com.offlinelandlord.game.network.uno.v5.UnoLanUiState
import com.offlinelandlord.game.network.uno.v5.UnoV5ActionPayload
import com.offlinelandlord.game.network.uno.v5.UnoV5ActionType
import com.offlinelandlord.game.network.uno.v5.UnoV5Client
import com.offlinelandlord.game.network.uno.v5.UnoV5ErrorCode
import com.offlinelandlord.game.network.uno.v5.UnoV5GameMode
import com.offlinelandlord.game.network.uno.v5.UnoV5HostServer
import com.offlinelandlord.game.network.uno.v5.UnoV5RoomConfig
import com.offlinelandlord.game.network.uno.v5.UnoV5RoomView
import com.offlinelandlord.game.network.uno.v5.UnoV5PayloadCodec
import com.offlinelandlord.game.network.protocol.v5.V5WireType
import java.io.Closeable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Compose-facing LAN controller. The HostSession remains the only game authority. */
class UnoLanController(private val scope: CoroutineScope) : Closeable {
    private val _uiState = MutableStateFlow(UnoLanUiState())
    val uiState: StateFlow<UnoLanUiState> = _uiState.asStateFlow()

    private var client: UnoV5Client? = null
    private var hostSession: UnoHostSession? = null
    private var hostServer: UnoV5HostServer? = null
    private var advertiser: UnoLanRoomAdvertiser? = null
    private var receiveJob: Job? = null
    private var operationJob: Job? = null
    private var intentionallyClosed = false

    fun createRoom(displayName: String, maxPlayers: Int, gameMode: UnoV5GameMode) {
        if (maxPlayers !in 2..4) { showError("玩家人数必须为 2 到 4 人"); return }
        closeRoom(resetUi = false)
        intentionallyClosed = false
        _uiState.value = UnoLanUiState(isHost = true, isBusy = true, connectionState = ConnectionState.CONNECTING)
        operationJob = scope.launch(Dispatchers.IO) {
            runCatching {
                val session = UnoHostSession(hostName = displayName.trim().ifBlank { "房主" }, config = UnoV5RoomConfig(maxPlayers, gameMode))
                val server = UnoV5HostServer(session).also { it.start(0) }
                val localClient = UnoV5Client()
                val accepted = localClient.resume("127.0.0.1", server.port, session.roomCode, session.hostPlayerId, session.hostResumeToken, displayName)
                check(accepted.success && accepted.value != null) { accepted.detail ?: "房主本地连接失败" }
                hostSession = session
                hostServer = server
                client = localClient
                advertiser = UnoLanRoomAdvertiser(session, "${displayName.ifBlank { "房主" }}的 UNO 房间", server.port).also { it.start() }
                _uiState.update { it.copy(room = accepted.value, roomName = "${displayName.ifBlank { "房主" }}的 UNO 房间", hostAddress = LocalAddressFinder.bestIpv4Address(), hostPort = server.port, selfPlayerId = localClient.playerId, isHost = true, isBusy = false, connectionState = ConnectionState.CONNECTED, errorMessage = null) }
                startReader(localClient)
            }.onFailure { error -> closeRoom(false); showError("创建 UNO 房间失败：${error.message.orEmpty()}") }
        }
    }

    fun joinRoom(host: String, port: Int, roomCode: String, displayName: String) {
        validateJoin(host, port, roomCode)?.let { showError(it); return }
        closeRoom(resetUi = false)
        intentionallyClosed = false
        _uiState.value = UnoLanUiState(isBusy = true, connectionState = ConnectionState.CONNECTING)
        operationJob = scope.launch(Dispatchers.IO) {
            val newClient = UnoV5Client()
            client = newClient
            val result = newClient.connect(host.trim(), port, roomCode.trim(), displayName.trim().ifBlank { "玩家" })
            if (result.success && result.value != null) {
                _uiState.update { it.copy(room = result.value, hostAddress = host.trim(), hostPort = port, selfPlayerId = newClient.playerId, isBusy = false, connectionState = ConnectionState.CONNECTED, errorMessage = null) }
                startReader(newClient)
            } else {
                newClient.close(); client = null
                _uiState.update { it.copy(isBusy = false, connectionState = ConnectionState.FAILED, errorMessage = friendlyError(result.error, result.detail)) }
            }
        }
    }

    fun joinDiscovered(room: UnoLanRoom, displayName: String) = joinRoom(room.host, room.port, room.roomCode, displayName)

    fun discoverRooms() {
        if (_uiState.value.isDiscovering) return
        _uiState.update { it.copy(isDiscovering = true, errorMessage = null) }
        operationJob = scope.launch(Dispatchers.IO) {
            val rooms = runCatching { UnoLanDiscovery.discover() }.getOrElse { showError("发现 UNO 房间失败：${it.message.orEmpty()}"); emptyList() }
            _uiState.update { it.copy(rooms = rooms, isDiscovering = false) }
        }
    }

    fun refreshRooms() = discoverRooms()
    fun ready() = sendRoomAction(UnoV5ActionType.READY)
    fun unready() = sendRoomAction(UnoV5ActionType.UNREADY)
    fun addBot() = sendRoomAction(UnoV5ActionType.ADD_BOT)
    fun removeBot(playerId: String) = sendRoomAction(UnoV5ActionType.REMOVE_BOT, playerId)
    fun startGame() = sendRoomAction(UnoV5ActionType.START_GAME)
    fun playCard(cardId: String) = sendGameAction(UnoV5ActionPayload(UnoV5ActionType.PLAY_CARD, cardId = cardId))
    fun drawCard() = sendGameAction(UnoV5ActionPayload(UnoV5ActionType.DRAW_CARD))
    fun playDrawnCard(cardId: String) = sendGameAction(UnoV5ActionPayload(UnoV5ActionType.PLAY_DRAWN_CARD, cardId = cardId))
    fun passAfterDraw() = sendGameAction(UnoV5ActionPayload(UnoV5ActionType.PASS_AFTER_DRAW))
    fun chooseColor(color: String) = sendGameAction(UnoV5ActionPayload(UnoV5ActionType.CHOOSE_COLOR, color = color))
    fun declareUno() = sendGameAction(UnoV5ActionPayload(UnoV5ActionType.DECLARE_UNO))
    fun catchUno(target: String?) = sendGameAction(UnoV5ActionPayload(UnoV5ActionType.CATCH_UNO, targetPlayerId = target))
    fun startNextRound() = sendGameAction(UnoV5ActionPayload(UnoV5ActionType.START_NEXT_ROUND))

    fun dismissError() = _uiState.update { it.copy(errorMessage = null) }
    fun dismissEvent() = _uiState.update { it.copy(eventMessage = null) }
    fun leaveRoom() = closeRoom(true)

    private fun sendRoomAction(action: UnoV5ActionType, targetPlayerId: String? = null) = sendAction(UnoV5ActionPayload(action, targetPlayerId = targetPlayerId))
    private fun sendGameAction(action: UnoV5ActionPayload) = sendAction(action)

    private fun sendAction(action: UnoV5ActionPayload) {
        val activeClient = client
        if (activeClient == null || !activeClient.connected) { showError("当前未连接房间"); return }
        val state = _uiState.value
        val roomAction = action.action in setOf(UnoV5ActionType.READY, UnoV5ActionType.UNREADY, UnoV5ActionType.ADD_BOT, UnoV5ActionType.REMOVE_BOT, UnoV5ActionType.START_GAME)
        if (!roomAction && action.action !in state.game?.legalActions.orEmpty()) { showError("当前操作不可用"); return }
        _uiState.update { it.copy(isBusy = true, errorMessage = null) }
        runCatching { activeClient.sendAction(action, state.room?.revision) }
            .onFailure { showError("发送操作失败：${it.message.orEmpty()}") }
    }

    private fun startReader(activeClient: UnoV5Client) {
        receiveJob?.cancel()
        receiveJob = scope.launch(Dispatchers.IO) {
            while (isActive && !intentionallyClosed && client === activeClient) {
                try {
                    val envelope = activeClient.receive() ?: error("连接已断开")
                    when (envelope.type) {
                        V5WireType.STATE -> UnoV5PayloadCodec.decodeRoom(envelope.payload)?.let(::applyRoom)
                        V5WireType.ERROR -> {
                            UnoV5PayloadCodec.decodeRoom(envelope.payload)?.let(::applyRoom)
                            _uiState.update { it.copy(isBusy = false, errorMessage = friendlyError(envelope.message?.let { name -> runCatching { UnoV5ErrorCode.valueOf(name) }.getOrNull() }, envelope.message)) }
                        }
                        else -> Unit
                    }
                } catch (_: Throwable) {
                    if (!intentionallyClosed && client === activeClient) reconnect(activeClient)
                    break
                }
            }
        }
    }

    private suspend fun reconnect(activeClient: UnoV5Client) {
        _uiState.update { it.copy(connectionState = ConnectionState.RECONNECTING, isReconnecting = true, isBusy = false, eventMessage = "连接已断开，正在尝试恢复…") }
        repeat(5) { attempt ->
            if (intentionallyClosed || client !== activeClient) return
            delay((attempt + 1) * 900L)
            val result = activeClient.reconnect()
            if (result.success && result.value != null) {
                applyRoom(result.value)
                _uiState.update { it.copy(connectionState = ConnectionState.CONNECTED, isReconnecting = false, eventMessage = "连接已恢复，已回到原座位") }
                startReader(activeClient)
                return
            }
        }
        _uiState.update { it.copy(connectionState = ConnectionState.FAILED, isReconnecting = false, errorMessage = "无法恢复房间连接，请检查同一热点后重试") }
    }

    private fun applyRoom(room: UnoV5RoomView) = _uiState.update { it.copy(room = room, selfPlayerId = it.selfPlayerId ?: client?.playerId, isBusy = false, connectionState = ConnectionState.CONNECTED, isReconnecting = false) }

    private fun validateJoin(host: String, port: Int, code: String): String? = when {
        host.isBlank() -> "请输入房主 IP"
        ':' in host -> "房主 IP 不要包含端口"
        port !in 1..65535 -> "端口必须为 1 到 65535"
        code.length != 6 || code.any { !it.isDigit() } -> "请输入正确的六位房间码"
        else -> null
    }

    private fun friendlyError(code: UnoV5ErrorCode?, detail: String?): String = when (code) {
        UnoV5ErrorCode.ROOM_NOT_FOUND -> "找不到房间"
        UnoV5ErrorCode.ROOM_FULL -> "房间已满"
        UnoV5ErrorCode.GAME_ALREADY_STARTED -> "游戏已经开始"
        UnoV5ErrorCode.NOT_YOUR_TURN -> "还没轮到你"
        UnoV5ErrorCode.ILLEGAL_ACTION -> "当前操作无效"
        UnoV5ErrorCode.STALE_REVISION -> "状态已更新，请重新操作"
        UnoV5ErrorCode.INVALID_RESUME_TOKEN -> "无法恢复原房间"
        UnoV5ErrorCode.NOT_HOST -> "仅房主可以操作"
        UnoV5ErrorCode.NOT_READY -> "还有玩家未准备"
        UnoV5ErrorCode.PLAYER_NOT_FOUND -> "玩家状态不存在"
        UnoV5ErrorCode.NOT_ENOUGH_PLAYERS -> "座位还没有坐满"
        else -> detail?.takeIf { it.isNotBlank() } ?: "网络操作失败"
    }

    private fun showError(message: String) = _uiState.update { it.copy(isBusy = false, errorMessage = message) }

    private fun closeRoom(resetUi: Boolean) {
        intentionallyClosed = true
        operationJob?.cancel(); receiveJob?.cancel()
        advertiser?.close(); advertiser = null
        client?.close(); client = null
        hostServer?.close(); hostServer = null
        hostSession = null
        if (resetUi) _uiState.value = UnoLanUiState()
    }

    override fun close() = closeRoom(true)
}
