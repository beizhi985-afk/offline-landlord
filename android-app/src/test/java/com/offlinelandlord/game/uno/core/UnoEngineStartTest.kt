package com.offlinelandlord.game.uno.core

import com.offlinelandlord.game.uno.core.UnoTestFixtures.players
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnoEngineStartTest {
    @Test
    fun twoPlayersCanStart() {
        assertTrue(UnoEngine.start(players(2), Random(1)).success)
    }

    @Test
    fun threePlayersCanStart() {
        assertTrue(UnoEngine.start(players(3), Random(1)).success)
    }

    @Test
    fun fourPlayersCanStart() {
        assertTrue(UnoEngine.start(players(4), Random(1)).success)
    }

    @Test
    fun onePlayerIsRejected() {
        val result = UnoEngine.start(players(1), Random(1))
        assertFalse(result.success)
        assertEquals(UnoErrorCode.INVALID_PLAYER_COUNT, result.error?.code)
    }

    @Test
    fun fivePlayersAreRejected() {
        val result = UnoEngine.start(players(5), Random(1))
        assertFalse(result.success)
        assertEquals(UnoErrorCode.INVALID_PLAYER_COUNT, result.error?.code)
    }

    @Test
    fun duplicatePlayerIdsAreRejected() {
        val result = UnoEngine.start(listOf(UnoPlayer("same", "A"), UnoPlayer("same", "B")))
        assertEquals(UnoErrorCode.DUPLICATE_PLAYER_ID, result.error?.code)
    }

    @Test
    fun nonPositiveTargetScoreIsRejected() {
        val result = UnoEngine.start(players(2), targetScore = 0)
        assertEquals(UnoErrorCode.INVALID_TARGET_SCORE, result.error?.code)
    }

    @Test
    fun everyPlayerReceivesSevenCards() {
        val state = requireNotNull(UnoEngine.start(players(4), Random(3)).engine).state
        assertTrue(state.players.all { it.hand.size == 7 })
    }

    @Test
    fun drawPileSizeAccountsForHandsInitialDiscardAndInitialPenalty() {
        val state = startedWith(UnoCardType.NUMBER, 4).state
        assertEquals(108 - 4 * 7 - 1, state.drawPile.size)
        assertEquals(1, state.discardPile.size)
    }

    @Test
    fun fixedSeedProducesIdenticalInitialState() {
        val first = requireNotNull(UnoEngine.start(players(3), Random(12345)).engine).state
        val second = requireNotNull(UnoEngine.start(players(3), Random(12345)).engine).state
        assertEquals(first, second)
    }

    @Test
    fun wildDrawFourIsNeverAcceptedAsInitialDiscard() {
        repeat(250) { seed ->
            val state = requireNotNull(UnoEngine.start(players(2 + seed % 3), Random(seed)).engine).state
            assertNotEquals(UnoCardType.WILD_DRAW_FOUR, state.discardPile.last().type)
            val cards = UnoTestFixtures.allCards(state)
            assertEquals(108, cards.size)
            assertEquals(108, cards.map(UnoCard::cardId).distinct().size)
            assertEquals(4, cards.count { it.type == UnoCardType.WILD_DRAW_FOUR })
        }
    }

    @Test
    fun initialWildRequiresBasePlayerToChooseColorThenKeepsTheirTurn() {
        val engine = startedWith(UnoCardType.WILD, 3)
        val before = engine.state
        assertEquals(UnoPhase.CHOOSE_COLOR, before.phase)
        assertNull(before.activeColor)
        assertEquals(before.players[before.baseStartingSeat].playerId, before.colorChooserPlayerId)
        val chooser = requireNotNull(before.colorChooserPlayerId)
        assertTrue(engine.applyAction(chooser, UnoAction.ChooseColor(UnoColor.BLUE)).success)
        assertEquals(chooser, engine.state.currentPlayerId)
        assertEquals(UnoColor.BLUE, engine.state.activeColor)
    }

    @Test
    fun initialSkipSkipsTheBaseStartingPlayer() {
        val state = startedWith(UnoCardType.SKIP, 3).state
        val expected = state.players[(state.baseStartingSeat + 1) % 3].playerId
        assertEquals(expected, state.currentPlayerId)
    }

    @Test
    fun initialReverseTwoPlayersGivesTheFirstTurnToTheOtherPlayer() {
        val state = startedWith(UnoCardType.REVERSE, 2).state
        assertEquals(UnoDirection.CLOCKWISE, state.direction)
        assertEquals(state.players[(state.baseStartingSeat + 1) % 2].playerId, state.currentPlayerId)
    }

    @Test
    fun initialReverseThreePlayersReversesThenMovesToThePlayerBeforeBaseline() {
        val state = startedWith(UnoCardType.REVERSE, 3).state
        assertEquals(UnoDirection.COUNTER_CLOCKWISE, state.direction)
        assertEquals(state.players[(state.baseStartingSeat + 2) % 3].playerId, state.currentPlayerId)
    }

    @Test
    fun initialReverseFourPlayersReversesThenMovesToThePlayerBeforeBaseline() {
        val state = startedWith(UnoCardType.REVERSE, 4).state
        assertEquals(UnoDirection.COUNTER_CLOCKWISE, state.direction)
        assertEquals(state.players[(state.baseStartingSeat + 3) % 4].playerId, state.currentPlayerId)
    }

    @Test
    fun initialDrawTwoPenalizesAndSkipsBasePlayer() {
        val state = startedWith(UnoCardType.DRAW_TWO, 3).state
        assertEquals(9, state.players[state.baseStartingSeat].hand.size)
        assertEquals(state.players[(state.baseStartingSeat + 1) % 3].playerId, state.currentPlayerId)
        assertEquals(108 - 3 * 7 - 1 - 2, state.drawPile.size)
    }

    private fun startedWith(type: UnoCardType, playerCount: Int): UnoEngine {
        repeat(20_000) { seed ->
            val engine = requireNotNull(UnoEngine.start(players(playerCount), Random(seed)).engine)
            if (engine.state.discardPile.last().type == type) return engine
        }
        error("No deterministic seed found for initial $type")
    }
}
