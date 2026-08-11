package com.offlinelandlord.game.uno.singleplayer

import com.offlinelandlord.game.uno.core.UnoActionType
import com.offlinelandlord.game.uno.core.UnoCard
import com.offlinelandlord.game.uno.core.UnoColor
import com.offlinelandlord.game.uno.core.UnoDirection
import com.offlinelandlord.game.uno.core.UnoMatchMode
import com.offlinelandlord.game.uno.core.UnoPhase

const val UNO_HUMAN_PLAYER_ID = "p0"

data class UnoSinglePlayerConfig(
    val playerCount: Int = 2,
    val matchMode: UnoMatchMode = UnoMatchMode.QUICK,
) {
    init {
        require(playerCount in 2..4) { "UNO single-player requires 2 to 4 total players" }
    }
}

data class UnoUiPlayer(
    val playerId: String,
    val name: String,
    val seat: Int,
    val remainingCardCount: Int,
    val score: Int,
    val isHuman: Boolean,
    val isCurrentPlayer: Boolean,
    val isRoundWinner: Boolean,
    val isMatchWinner: Boolean,
)

data class UnoUiState(
    val gameStarted: Boolean = false,
    val config: UnoSinglePlayerConfig = UnoSinglePlayerConfig(),
    val humanHand: List<UnoCard> = emptyList(),
    val players: List<UnoUiPlayer> = emptyList(),
    val topDiscardCard: UnoCard? = null,
    val drawPileCount: Int = 0,
    val activeColor: UnoColor? = null,
    val direction: UnoDirection = UnoDirection.CLOCKWISE,
    val phase: UnoPhase? = null,
    val currentPlayerId: String? = null,
    val currentPlayerName: String? = null,
    val roundNumber: Int = 1,
    val targetScore: Int = 500,
    val lastRoundScore: Int = 0,
    val roundWinnerId: String? = null,
    val matchWinnerId: String? = null,
    val availableActions: Set<UnoActionType> = emptySet(),
    val legalCardIds: Set<String> = emptySet(),
    val catchTargetPlayerId: String? = null,
    val colorChooserPlayerId: String? = null,
    val hasDeclaredUno: Boolean = false,
    val isActionInProgress: Boolean = false,
    val isBotThinking: Boolean = false,
    val eventMessage: String? = null,
    val errorMessage: String? = null,
) {
    val opponents: List<UnoUiPlayer> get() = players.filterNot(UnoUiPlayer::isHuman)
    val ranking: List<UnoUiPlayer> get() = players.sortedWith(compareByDescending<UnoUiPlayer> { it.score }.thenBy { it.seat })
    val isHumanTurn: Boolean get() = currentPlayerId == UNO_HUMAN_PLAYER_ID
    val canDraw: Boolean get() = !isActionInProgress && UnoActionType.DRAW_CARD in availableActions
    val canPassAfterDraw: Boolean get() = !isActionInProgress && UnoActionType.PASS_AFTER_DRAW in availableActions
    val canDeclareUno: Boolean get() = !isActionInProgress && UnoActionType.DECLARE_UNO in availableActions
    val canCatchUno: Boolean get() = !isActionInProgress && UnoActionType.CATCH_UNO in availableActions
    val canStartNextRound: Boolean get() = !isActionInProgress && UnoActionType.START_NEXT_ROUND in availableActions
    val mustChooseColor: Boolean get() = colorChooserPlayerId == UNO_HUMAN_PLAYER_ID && phase == UnoPhase.CHOOSE_COLOR
}
