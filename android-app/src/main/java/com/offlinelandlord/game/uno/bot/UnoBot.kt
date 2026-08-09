package com.offlinelandlord.game.uno.bot

import com.offlinelandlord.game.uno.core.UnoAction
import com.offlinelandlord.game.uno.core.UnoActionType
import com.offlinelandlord.game.uno.core.UnoEngine
import kotlin.random.Random

class UnoBot(
    val playerId: String,
    random: Random = Random.Default,
    private val strategy: UnoBotStrategy = NormalUnoBotStrategy(random),
) {
    fun observe(engine: UnoEngine): UnoBotObservation? =
        engine.viewFor(playerId)?.let(UnoBotObservation::from)

    fun chooseAction(engine: UnoEngine): UnoAction? {
        val observation = observe(engine) ?: return null
        val available = engine.availableActions(playerId)
        val legalCards = engine.legalPlayableCards(playerId)
        val selected = strategy.chooseAction(observation, available, legalCards) ?: return null
        return selected.takeIf { isEngineApproved(it, available, legalCards, observation) }
    }

    private fun isEngineApproved(
        action: UnoAction,
        available: Set<UnoActionType>,
        legalCards: List<com.offlinelandlord.game.uno.core.UnoCard>,
        observation: UnoBotObservation,
    ): Boolean = when (action) {
        is UnoAction.PlayCard ->
            UnoActionType.PLAY_CARD in available && legalCards.any { it.cardId == action.cardId }

        UnoAction.DrawCard -> UnoActionType.DRAW_CARD in available
        is UnoAction.PlayDrawnCard ->
            UnoActionType.PLAY_DRAWN_CARD in available && legalCards.any { it.cardId == action.cardId }

        UnoAction.PassAfterDraw -> UnoActionType.PASS_AFTER_DRAW in available
        UnoAction.DeclareUno -> UnoActionType.DECLARE_UNO in available
        is UnoAction.CatchUno ->
            UnoActionType.CATCH_UNO in available && action.targetPlayerId == observation.catchTargetPlayerId

        is UnoAction.ChooseColor -> UnoActionType.CHOOSE_COLOR in available
        UnoAction.StartNextRound -> UnoActionType.START_NEXT_ROUND in available
    }
}
