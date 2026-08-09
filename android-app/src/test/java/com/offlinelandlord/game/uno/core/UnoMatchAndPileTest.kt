package com.offlinelandlord.game.uno.core

import com.offlinelandlord.game.uno.core.UnoTestFixtures.allCards
import com.offlinelandlord.game.uno.core.UnoTestFixtures.engine
import com.offlinelandlord.game.uno.core.UnoTestFixtures.number
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnoMatchAndPileTest {
    private val top = number("top", UnoColor.RED, 2)

    @Test
    fun pointsRoundBelowTargetStopsAtRoundFinished() {
        val engine = pointsRoundEngine()
        engine.applyAction("p0", UnoAction.PlayCard("last"))
        assertEquals(UnoPhase.ROUND_FINISHED, engine.state.phase)
        assertEquals("p0", engine.state.roundWinnerId)
        assertNull(engine.state.matchWinnerId)
        assertEquals(9, engine.state.scores.getValue("p0"))
    }

    @Test
    fun pointsScoresAccumulateAcrossRounds() {
        val engine = pointsRoundEngine(scores = mapOf("p0" to 100, "p1" to 20))
        engine.applyAction("p0", UnoAction.PlayCard("last"))
        assertEquals(109, engine.state.scores.getValue("p0"))
        engine.applyAction("p1", UnoAction.StartNextRound)
        assertEquals(109, engine.state.scores.getValue("p0"))
        assertEquals(20, engine.state.scores.getValue("p1"))
    }

    @Test
    fun reachingPointsTargetFinishesMatch() {
        val engine = pointsRoundEngine(
            targetScore = 500,
            scores = mapOf("p0" to 495, "p1" to 0),
        )
        engine.applyAction("p0", UnoAction.PlayCard("last"))
        assertEquals(UnoPhase.MATCH_FINISHED, engine.state.phase)
        assertEquals("p0", engine.state.matchWinnerId)
        assertEquals(504, engine.state.scores.getValue("p0"))
    }

    @Test
    fun nextPointsRoundBuildsFresh108CardState() {
        val engine = pointsRoundEngine()
        engine.applyAction("p0", UnoAction.PlayCard("last"))
        assertTrue(engine.applyAction("p0", UnoAction.StartNextRound).success)
        val state = engine.state
        assertEquals(2, state.roundNumber)
        assertEquals(108, allCards(state).size)
        assertEquals(108, allCards(state).map { it.cardId }.distinct().size)
        assertTrue(state.players.all { it.hand.size >= 7 })
        assertEquals(1, state.discardPile.size)
    }

    @Test
    fun nextRoundClearsUnoAndColorTransientState() {
        val engine = pointsRoundEngine()
        engine.applyAction("p0", UnoAction.PlayCard("last"))
        engine.applyAction("p1", UnoAction.StartNextRound)
        val state = engine.state
        assertNull(state.catchWindow)
        assertNull(state.unoDeclaredPlayerId)
        assertNull(state.drawnCardId)
        assertNull(state.pendingRoundWinnerId)
        if (state.phase == UnoPhase.TURN) {
            assertEquals(state.discardPile.last().color, state.activeColor)
        } else {
            assertEquals(UnoPhase.CHOOSE_COLOR, state.phase)
            assertNull(state.activeColor)
        }
    }

    @Test
    fun nextRoundBaseStartingSeatRotatesClockwise() {
        val engine = pointsRoundEngine(baseStartingSeat = 0)
        engine.applyAction("p0", UnoAction.PlayCard("last"))
        engine.applyAction("p0", UnoAction.StartNextRound)
        assertEquals(1, engine.state.baseStartingSeat)
    }

    @Test
    fun quickMatchDoesNotAllowStartingAnotherRound() {
        val engine = engine(
            listOf(listOf(number("last", UnoColor.RED, 5)), listOf(number("p1", UnoColor.BLUE, 9))),
            top,
        )
        engine.applyAction("p0", UnoAction.PlayCard("last"))
        val result = engine.applyAction("p0", UnoAction.StartNextRound)
        assertFalse(result.success)
        assertEquals(UnoErrorCode.NEXT_ROUND_NOT_AVAILABLE, result.error?.code)
    }

    @Test
    fun drawingWithEmptyPileRecyclesAllButTopDiscard() {
        val oldOne = number("old-one", UnoColor.YELLOW, 4)
        val oldTwo = number("old-two", UnoColor.GREEN, 6)
        val hand0 = number("hand-zero", UnoColor.BLUE, 8)
        val hand1 = number("hand-one", UnoColor.YELLOW, 1)
        val engine = engine(
            hands = listOf(listOf(hand0), listOf(hand1)),
            top = top,
            drawPile = emptyList(),
            discardPile = listOf(oldOne, oldTwo, top),
        )
        val beforeIds = allCards(engine.state).map { it.cardId }.toSet()
        assertTrue(engine.applyAction("p0", UnoAction.DrawCard).success)
        val after = engine.state
        assertEquals(listOf(top), after.discardPile)
        assertEquals(beforeIds, allCards(after).map { it.cardId }.toSet())
        assertEquals(beforeIds.size, allCards(after).size)
    }

    @Test
    fun recycledPileHasNoDuplicateOrLostCardIds() {
        val discards = (1..5).map { number("old-$it", UnoColor.YELLOW, it) }
        val engine = engine(
            hands = listOf(
                listOf(number("h0", UnoColor.BLUE, 8)),
                listOf(number("h1", UnoColor.GREEN, 9)),
            ),
            top = top,
            drawPile = emptyList(),
            discardPile = discards + top,
        )
        val before = allCards(engine.state).map { it.cardId }
        engine.applyAction("p0", UnoAction.DrawCard)
        val after = allCards(engine.state).map { it.cardId }
        assertEquals(before.toSet(), after.toSet())
        assertEquals(after.size, after.distinct().size)
    }

    @Test
    fun legalQueryAndAvailableActionsShareEngineRules() {
        val playable = number("playable", UnoColor.RED, 7)
        val blocked = number("blocked", UnoColor.BLUE, 8)
        val engine = engine(
            listOf(listOf(playable, blocked), listOf(number("p1", UnoColor.YELLOW, 1))),
            top,
        )
        assertEquals(listOf(playable), engine.legalPlayableCards("p0"))
        assertTrue(engine.canPlayCard("p0", playable.cardId))
        assertFalse(engine.canPlayCard("p0", blocked.cardId))
        assertTrue(UnoActionType.PLAY_CARD in engine.availableActions("p0"))
        assertTrue(UnoActionType.DRAW_CARD in engine.availableActions("p0"))
    }

    private fun pointsRoundEngine(
        targetScore: Int = 500,
        scores: Map<String, Int> = mapOf("p0" to 0, "p1" to 0),
        baseStartingSeat: Int = 0,
    ): UnoEngine = engine(
        hands = listOf(
            listOf(number("last", UnoColor.RED, 5)),
            listOf(number("opponent-nine", UnoColor.BLUE, 9)),
        ),
        top = top,
        matchMode = UnoMatchMode.POINTS,
        targetScore = targetScore,
        scores = scores,
        baseStartingSeat = baseStartingSeat,
    )
}
