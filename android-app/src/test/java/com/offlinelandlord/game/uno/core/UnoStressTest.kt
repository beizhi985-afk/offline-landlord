package com.offlinelandlord.game.uno.core

import com.offlinelandlord.game.uno.core.UnoTestFixtures.allCards
import com.offlinelandlord.game.uno.core.UnoTestFixtures.players
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnoStressTest {
    @Test(timeout = 120_000)
    fun twoThousandRandomQuickRoundsFinishWithCardConservation() {
        val driverRandom = Random(20260810)
        repeat(2_000) { gameIndex ->
            val playerCount = 2 + gameIndex % 3
            val engine = requireNotNull(
                UnoEngine.start(
                    players(playerCount),
                    Random(driverRandom.nextInt()),
                    UnoMatchMode.QUICK,
                ).engine,
            )
            var actions = 0
            while (engine.state.phase != UnoPhase.MATCH_FINISHED) {
                assertStateInvariant(engine.state)
                playOneLegalStep(engine, driverRandom)
                actions++
                assertTrue("Game $gameIndex exceeded action limit", actions < 20_000)
            }
            val finished = engine.state
            assertStateInvariant(finished)
            assertNotNull(finished.roundWinnerId)
            assertNotNull(finished.matchWinnerId)
        }
    }

    private fun playOneLegalStep(engine: UnoEngine, random: Random) {
        val state = engine.state
        val current = requireNotNull(state.currentPlayerId)
        val catch = state.catchWindow
        if (catch != null && random.nextInt(4) == 0) {
            val catcher = state.players.first { it.playerId != catch.targetPlayerId }.playerId
            check(engine.applyAction(catcher, UnoAction.CatchUno(catch.targetPlayerId)).success)
            return
        }

        when (state.phase) {
            UnoPhase.CHOOSE_COLOR -> {
                val chooser = requireNotNull(state.colorChooserPlayerId)
                check(engine.applyAction(chooser, UnoAction.ChooseColor(UnoColor.entries.random(random))).success)
            }

            UnoPhase.AFTER_DRAW -> {
                val legal = engine.legalPlayableCards(current)
                val action = if (legal.isNotEmpty() && random.nextInt(4) != 0) {
                    UnoAction.PlayDrawnCard(legal.single().cardId)
                } else {
                    UnoAction.PassAfterDraw
                }
                check(engine.applyAction(current, action).success)
            }

            UnoPhase.TURN -> {
                val handSize = state.players.first { it.playerId == current }.hand.size
                if (handSize == 2 &&
                    UnoActionType.DECLARE_UNO in engine.availableActions(current) &&
                    random.nextBoolean()
                ) {
                    check(engine.applyAction(current, UnoAction.DeclareUno).success)
                    return
                }
                val legal = engine.legalPlayableCards(current)
                val action = if (legal.isNotEmpty() && random.nextInt(4) != 0) {
                    UnoAction.PlayCard(legal.random(random).cardId)
                } else {
                    UnoAction.DrawCard
                }
                check(engine.applyAction(current, action).success)
            }

            UnoPhase.ROUND_FINISHED,
            UnoPhase.MATCH_FINISHED,
            -> error("Quick match reached an unexpected phase")
        }
    }

    private fun assertStateInvariant(state: UnoGameState) {
        val cards = allCards(state)
        assertEquals(108, cards.size)
        assertEquals(108, cards.map { it.cardId }.distinct().size)
        if (state.phase == UnoPhase.TURN || state.phase == UnoPhase.AFTER_DRAW) {
            assertNotNull(state.currentPlayerId)
            assertTrue(state.players.any { it.playerId == state.currentPlayerId })
        }
        if (state.phase == UnoPhase.TURN) assertNotNull(state.activeColor)
        if (state.phase == UnoPhase.ROUND_FINISHED) assertNotNull(state.roundWinnerId)
        if (state.phase == UnoPhase.MATCH_FINISHED) {
            assertNotNull(state.roundWinnerId)
            assertNotNull(state.matchWinnerId)
        }
    }
}
