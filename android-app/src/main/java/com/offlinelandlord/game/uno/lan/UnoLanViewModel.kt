package com.offlinelandlord.game.uno.lan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.offlinelandlord.game.network.uno.v5.UnoLanRoom
import com.offlinelandlord.game.network.uno.v5.UnoLanUiState
import com.offlinelandlord.game.network.uno.v5.UnoV5GameMode
import kotlinx.coroutines.flow.StateFlow

class UnoLanViewModel : ViewModel() {
    private val controller = UnoLanController(viewModelScope)
    val uiState: StateFlow<UnoLanUiState> = controller.uiState

    fun createRoom(name: String, players: Int, mode: UnoV5GameMode) = controller.createRoom(name, players, mode)
    fun joinRoom(host: String, port: Int, code: String, name: String) = controller.joinRoom(host, port, code, name)
    fun joinDiscovered(room: UnoLanRoom, name: String) = controller.joinDiscovered(room, name)
    fun discoverRooms() = controller.discoverRooms()
    fun refreshRooms() = controller.refreshRooms()
    fun ready() = controller.ready()
    fun unready() = controller.unready()
    fun addBot() = controller.addBot()
    fun removeBot(playerId: String) = controller.removeBot(playerId)
    fun startGame() = controller.startGame()
    fun playCard(cardId: String) = controller.playCard(cardId)
    fun drawCard() = controller.drawCard()
    fun playDrawnCard(cardId: String) = controller.playDrawnCard(cardId)
    fun passAfterDraw() = controller.passAfterDraw()
    fun chooseColor(color: String) = controller.chooseColor(color)
    fun declareUno() = controller.declareUno()
    fun catchUno(target: String?) = controller.catchUno(target)
    fun startNextRound() = controller.startNextRound()
    fun dismissError() = controller.dismissError()
    fun leaveRoom() = controller.leaveRoom()

    override fun onCleared() = controller.close()
}
