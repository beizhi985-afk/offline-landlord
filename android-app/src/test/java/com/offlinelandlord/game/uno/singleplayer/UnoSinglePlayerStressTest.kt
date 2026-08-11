package com.offlinelandlord.game.uno.singleplayer

import com.offlinelandlord.game.uno.core.UnoAction
import com.offlinelandlord.game.uno.core.UnoColor
import com.offlinelandlord.game.uno.core.UnoMatchMode
import com.offlinelandlord.game.uno.core.UnoPhase
import kotlinx.coroutines.runBlocking
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnoSinglePlayerStressTest {
    @Test(timeout = 180_000)
    fun oneThousandControllerQuickGamesFinish() = runBlocking {
        repeat(1_000) { gameIndex ->
            val count = 2 + gameIndex % 3
            val controller = UnoSinglePlayerController(this, UnoBotDelayProvider.Immediate)
            controller.startGame(UnoSinglePlayerConfig(count, UnoMatchMode.QUICK), Random(70_000 + gameIndex))
            driveMatch(controller, maxHumanActions = 20_000)
            val state = controller.uiState.value
            assertEquals("quick $gameIndex", UnoPhase.MATCH_FINISHED, state.phase)
            assertNotNull(state.matchWinnerId)
            controller.close()
        }
    }

    @Test(timeout = 180_000)
    fun oneHundredControllerPointsMatchesReachFiveHundred() = runBlocking {
        repeat(100) { matchIndex ->
            val count = 2 + matchIndex % 3
            val controller = UnoSinglePlayerController(this, UnoBotDelayProvider.Immediate)
            controller.startGame(UnoSinglePlayerConfig(count, UnoMatchMode.POINTS), Random(90_000 + matchIndex))
            driveMatch(controller, maxHumanActions = 250_000)
            val state = controller.uiState.value
            assertEquals("points $matchIndex", UnoPhase.MATCH_FINISHED, state.phase)
            assertNotNull(state.matchWinnerId)
            assertTrue(state.players.any { it.score >= 500 })
            controller.close()
        }
    }

    private suspend fun driveMatch(controller: UnoSinglePlayerController, maxHumanActions: Int) {
        var actions = 0
        controller.awaitIdle()
        while (controller.uiState.value.phase != UnoPhase.MATCH_FINISHED && actions < maxHumanActions) {
            val state = controller.uiState.value
            val action = when {
                state.canStartNextRound -> UnoAction.StartNextRound
                state.canCatchUno -> UnoAction.CatchUno(requireNotNull(state.catchTargetPlayerId))
                state.mustChooseColor -> UnoAction.ChooseColor(chooseColor(state.humanHand))
                state.canDeclareUno -> UnoAction.DeclareUno
                state.legalCardIds.isNotEmpty() -> {
                    val cardId = state.legalCardIds.first()
                    if (state.phase == UnoPhase.AFTER_DRAW) UnoAction.PlayDrawnCard(cardId) else UnoAction.PlayCard(cardId)
                }
                state.canDraw -> UnoAction.DrawCard
                state.canPassAfterDraw -> UnoAction.PassAfterDraw
                else -> error("Controller blocked at phase=${state.phase}, current=${state.currentPlayerId}")
            }
            assertTrue(controller.submitHumanActionAndWait(action))
            actions++
        }
        assertTrue("Controller exceeded $maxHumanActions human actions", actions < maxHumanActions)
    }

    private fun chooseColor(hand: List<com.offlinelandlord.game.uno.core.UnoCard>): UnoColor =
        UnoColor.entries.maxBy { color -> hand.count { it.color == color } }
}
