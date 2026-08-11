package com.offlinelandlord.game.uno.singleplayer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.offlinelandlord.game.uno.core.UnoColor
import kotlinx.coroutines.flow.StateFlow

class UnoGameViewModel : ViewModel() {
    private val controller = UnoSinglePlayerController(
        scope = viewModelScope,
        botDelayProvider = UnoBotDelayProvider.fixed(450),
    )
    val uiState: StateFlow<UnoUiState> = controller.uiState

    private var activeConfig: UnoSinglePlayerConfig? = null

    fun ensureGame(config: UnoSinglePlayerConfig) {
        if (activeConfig == config && uiState.value.gameStarted) return
        activeConfig = config
        controller.startGame(config)
    }

    fun playCard(cardId: String) = controller.playCard(cardId)
    fun drawCard() = controller.drawCard()
    fun passAfterDraw() = controller.passAfterDraw()
    fun declareUno() = controller.declareUno()
    fun catchUno() = controller.catchUno()
    fun chooseColor(color: UnoColor) = controller.chooseColor(color)
    fun startNextRound() = controller.startNextRound()
    fun restartMatch() = controller.restartMatch()
    fun dismissEvent() = controller.dismissEvent()
    fun dismissError() = controller.dismissError()

    fun leaveGame() {
        activeConfig = null
        controller.clearGame()
    }

    override fun onCleared() {
        controller.close()
    }
}
