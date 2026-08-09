package com.offlinelandlord.game.uno.bot

import com.offlinelandlord.game.uno.core.UnoEngine
import com.offlinelandlord.game.uno.core.UnoGameState
import com.offlinelandlord.game.uno.core.UnoMatchMode
import com.offlinelandlord.game.uno.core.UnoPhase
import com.offlinelandlord.game.uno.core.UnoTestFixtures.allCards
import com.offlinelandlord.game.uno.core.UnoTestFixtures.players
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnoBotStressTest {
    @Test(timeout = 180_000)
    fun fiveThousandNormalBotQuickGamesFinishLegally() {
        repeat(5_000) { gameIndex ->
            val playerCount = 2 + gameIndex % 3
            val game = requireNotNull(
                UnoEngine.start(players(playerCount), Random(10_000 + gameIndex)).engine,
            )
            val result = UnoBotGameRunner(game, bots(playerCount, gameIndex)).runUntilBlocked(
                maxActionCount = 20_000,
                onActionApplied = ::assertCardConservation,
            )
            assertEquals("quick game $gameIndex", UnoBotRunStatus.COMPLETED, result.status)
            assertEquals(UnoPhase.MATCH_FINISHED, game.state.phase)
            assertNotNull(game.state.matchWinnerId)
            assertCardConservation(game.state)
        }
    }

    @Test(timeout = 180_000)
    fun fiveHundredNormalBotPointMatchesReachFiveHundred() {
        repeat(500) { matchIndex ->
            val playerCount = 2 + matchIndex % 3
            val game = requireNotNull(
                UnoEngine.start(
                    players = players(playerCount),
                    random = Random(30_000 + matchIndex),
                    matchMode = UnoMatchMode.POINTS,
                    targetScore = 500,
                ).engine,
            )
            val result = UnoBotGameRunner(game, bots(playerCount, 50_000 + matchIndex)).runUntilBlocked(
                maxActionCount = 250_000,
                onActionApplied = ::assertCardConservation,
            )
            assertEquals("points match $matchIndex", UnoBotRunStatus.COMPLETED, result.status)
            val state = game.state
            assertEquals(UnoPhase.MATCH_FINISHED, state.phase)
            assertNotNull(state.matchWinnerId)
            assertTrue(state.scores.values.all { it >= 0 })
            assertTrue(state.scores.values.any { it >= 500 })
            assertCardConservation(state)
        }
    }

    private fun bots(count: Int, seed: Int): List<UnoBot> =
        (0 until count).map { seat -> UnoBot("p$seat", Random(seed * 31 + seat)) }

    private fun assertCardConservation(state: UnoGameState) {
        val cards = allCards(state)
        assertEquals(108, cards.size)
        assertEquals(108, cards.map { it.cardId }.distinct().size)
    }
}
