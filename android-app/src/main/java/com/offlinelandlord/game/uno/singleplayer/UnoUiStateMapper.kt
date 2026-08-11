package com.offlinelandlord.game.uno.singleplayer

import com.offlinelandlord.game.uno.core.UnoEngine

object UnoUiStateMapper {
    fun from(
        engine: UnoEngine,
        config: UnoSinglePlayerConfig,
        isActionInProgress: Boolean = false,
        isBotThinking: Boolean = false,
        eventMessage: String? = null,
        errorMessage: String? = null,
    ): UnoUiState {
        val view = requireNotNull(engine.viewFor(UNO_HUMAN_PLAYER_ID)) {
            "UNO single-player engine must contain the human player"
        }
        val publicPlayers = view.players.map { player ->
            UnoUiPlayer(
                playerId = player.playerId,
                name = player.playerName,
                seat = player.seat,
                remainingCardCount = player.remainingCardCount,
                score = player.score,
                isHuman = player.playerId == UNO_HUMAN_PLAYER_ID,
                isCurrentPlayer = player.playerId == view.currentPlayerId,
                isRoundWinner = player.playerId == view.roundWinnerId,
                isMatchWinner = player.playerId == view.matchWinnerId,
            )
        }
        return UnoUiState(
            gameStarted = true,
            config = config,
            humanHand = view.ownHand,
            players = publicPlayers,
            topDiscardCard = view.topDiscardCard,
            drawPileCount = view.drawPileCount,
            activeColor = view.activeColor,
            direction = view.direction,
            phase = view.phase,
            currentPlayerId = view.currentPlayerId,
            currentPlayerName = publicPlayers.firstOrNull { it.playerId == view.currentPlayerId }?.name,
            roundNumber = view.roundNumber,
            targetScore = view.targetScore,
            lastRoundScore = view.lastRoundScore,
            roundWinnerId = view.roundWinnerId,
            matchWinnerId = view.matchWinnerId,
            availableActions = engine.availableActions(UNO_HUMAN_PLAYER_ID),
            legalCardIds = engine.legalPlayableCards(UNO_HUMAN_PLAYER_ID).mapTo(linkedSetOf()) { it.cardId },
            catchTargetPlayerId = view.catchTargetPlayerId,
            colorChooserPlayerId = view.colorChooserPlayerId,
            hasDeclaredUno = view.unoDeclaredPlayerId == UNO_HUMAN_PLAYER_ID,
            isActionInProgress = isActionInProgress,
            isBotThinking = isBotThinking,
            eventMessage = eventMessage,
            errorMessage = errorMessage,
        )
    }
}
