package com.offlinelandlord.game.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.offlinelandlord.game.core.PlayerAction
import com.offlinelandlord.game.network.ConnectionState
import com.offlinelandlord.game.network.DiscoveredRoom
import com.offlinelandlord.game.network.HostGameSession
import com.offlinelandlord.game.network.LanGameClient
import com.offlinelandlord.game.network.LanGameServer
import com.offlinelandlord.game.network.RoomDiscovery
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class GameViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(AppUiState())
    private var hostSession: HostGameSession? = null
    private var client: LanGameClient? = null
    private val sessionJobs = mutableListOf<Job>()

    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    fun createRoom(playerName: String) {
        leaveRoom()
        runCatching {
            HostGameSession(playerName.trim().ifBlank { "房主" }).also { session ->
                session.start()
                hostSession = session
                _uiState.value = AppUiState(
                    gameView = session.viewState.value,
                    isHost = true,
                    hostAddress = session.hostAddress,
                    port = session.port,
                    connectionState = ConnectionState.CONNECTED,
                )
                sessionJobs += viewModelScope.launch {
                    session.viewState.collect { view ->
                        _uiState.update {
                            it.copy(
                                gameView = view,
                                hostAddress = session.hostAddress,
                                port = session.port,
                            )
                        }
                    }
                }
            }
        }.onFailure { error ->
            _uiState.update { it.copy(errorMessage = "创建房间失败：${error.message.orEmpty()}") }
        }
    }

    fun discoverRooms() {
        _uiState.update { it.copy(isDiscovering = true, errorMessage = null) }
        viewModelScope.launch {
            val rooms = runCatching { RoomDiscovery.discover() }.getOrElse { error ->
                _uiState.update { it.copy(errorMessage = "发现房间失败：${error.message.orEmpty()}") }
                emptyList()
            }
            _uiState.update { state ->
                state.copy(
                    discoveredRooms = rooms,
                    isDiscovering = false,
                    errorMessage = if (rooms.isEmpty()) "没有发现房间，请确认三台手机连接同一热点" else state.errorMessage,
                )
            }
        }
    }

    fun joinDiscovered(room: DiscoveredRoom, playerName: String) {
        joinRoom(room.host, room.port, room.roomCode, playerName)
    }

    fun joinRoom(host: String, port: Int, roomCode: String, playerName: String) {
        if (host.isBlank() || roomCode.isBlank()) {
            _uiState.update { it.copy(errorMessage = "请输入房主 IP 和房间码") }
            return
        }
        leaveRoom()
        _uiState.update { it.copy(isBusy = true, errorMessage = null, connectionState = ConnectionState.CONNECTING) }

        val newClient = LanGameClient()
        client = newClient
        sessionJobs += viewModelScope.launch {
            val result = newClient.connect(host.trim(), port, roomCode.trim(), playerName)
            _uiState.update { state ->
                state.copy(
                    isBusy = false,
                    errorMessage = if (result.success) null else result.message,
                )
            }
        }
        sessionJobs += viewModelScope.launch {
            newClient.viewState.collect { view -> _uiState.update { it.copy(gameView = view) } }
        }
        sessionJobs += viewModelScope.launch {
            newClient.connectionState.collect { connection ->
                _uiState.update { it.copy(connectionState = connection) }
            }
        }
        sessionJobs += viewModelScope.launch {
            newClient.lastError.collect { error ->
                if (!error.isNullOrBlank()) _uiState.update { it.copy(errorMessage = error) }
            }
        }
    }

    fun setReady(ready: Boolean) = sendAction(PlayerAction.ready(ready))

    fun bid(value: Int) = sendAction(PlayerAction.bid(value))

    fun play(cardIds: List<String>) = sendAction(PlayerAction.play(cardIds))

    fun pass() = sendAction(PlayerAction.pass())

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun leaveRoom() {
        sessionJobs.forEach { it.cancel() }
        sessionJobs.clear()
        hostSession?.close()
        hostSession = null
        client?.close()
        client = null
        _uiState.value = AppUiState()
    }

    private fun sendAction(action: PlayerAction) {
        val host = hostSession
        if (host != null) {
            viewModelScope.launch {
                val result = host.sendAction(action)
                if (!result.success) _uiState.update { it.copy(errorMessage = result.message) }
            }
            return
        }

        val result = client?.sendAction(action)
            ?: return _uiState.update { it.copy(errorMessage = "尚未加入房间") }
        if (!result.success) _uiState.update { it.copy(errorMessage = result.message) }
    }

    override fun onCleared() {
        leaveRoom()
        super.onCleared()
    }
}

