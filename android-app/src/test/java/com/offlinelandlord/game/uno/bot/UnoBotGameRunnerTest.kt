package com.offlinelandlord.game.uno.bot

import com.offlinelandlord.game.uno.core.UnoColor
import com.offlinelandlord.game.uno.core.UnoEngine
import com.offlinelandlord.game.uno.core.UnoMatchMode
import com.offlinelandlord.game.uno.core.UnoPhase
import com.offlinelandlord.game.uno.core.UnoTestFixtures.engine
import com.offlinelandlord.game.uno.core.UnoTestFixtures.number
import com.offlinelandlord.game.uno.core.UnoTestFixtures.players
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnoBotGameRunnerTest {
    @Test
    fun twoBotsCompleteQuickGame() {
        assertQuickGameCompletes(2, 101)
    }

    @Test
    fun threeBotsCompleteQuickGame() {
        assertQuickGameCompletes(3, 102)
    }

    @Test
    fun fourBotsCompleteQuickGame() {
        assertQuickGameCompletes(4, 103)
    }

    @Test
    fun botsCompleteFiveHundredPointMatch() {
        val game = requireNotNull(
            UnoEngine.start(players(3), Random(400), UnoMatchMode.POINTS, targetScore = 500).engine,
        )
        val result = UnoBotGameRunner(game, bots(3, 500)).runUntilBlocked(200_000)
        assertEquals(UnoBotRunStatus.COMPLETED, result.status)
        assertEquals(UnoPhase.MATCH_FINISHED, game.state.phase)
        assertNotNull(game.state.matchWinnerId)
        assertTrue(game.state.scores.values.any { it >= 500 })
        assertTrue(game.state.roundNumber > 1)
    }

    @Test
    fun runnerStopsWhenCurrentPlayerIsHuman() {
        val game = requireNotNull(UnoEngine.start(players(3), Random(51)).engine)
        val current = requireNotNull(game.state.currentPlayerId)
        val registered = game.state.players.filter { it.playerId != current }.map {
            UnoBot(it.playerId, Random(it.seat))
        }
        val result = UnoBotGameRunner(game, registered).runUntilBlocked(100)
        assertEquals(UnoBotRunStatus.WAITING_FOR_EXTERNAL_PLAYER, result.status)
        assertEquals(0, result.actionCount)
    }

    @Test
    fun botCanCatchUnoBeforeRunnerWaitsForHumanTurn() {
        val game = engine(
            hands = listOf(
                listOf(number("human", UnoColor.RED, 5)),
                listOf(number("bot", UnoColor.BLUE, 4)),
                listOf(number("caught", UnoColor.GREEN, 1)),
            ),
            top = number("top", UnoColor.RED, 2),
            currentSeat = 0,
            catchTargetId = "p2",
            drawPile = listOf(
                number("penalty-a", UnoColor.YELLOW, 3),
                number("penalty-b", UnoColor.YELLOW, 4),
            ),
        )
        val result = UnoBotGameRunner(game, listOf(UnoBot("p1", Random(1)))).runUntilBlocked(10)
        assertEquals(UnoBotRunStatus.WAITING_FOR_EXTERNAL_PLAYER, result.status)
        assertEquals(1, result.actionCount)
        assertEquals(3, game.state.players.single { it.playerId == "p2" }.hand.size)
        assertNull(game.state.catchWindow)
    }

    @Test
    fun runnerSupportsDeclareThenPlayAsTwoConsecutiveActions() {
        val game = engine(
            hands = listOf(
                listOf(number("playable", UnoColor.RED, 5), number("remaining", UnoColor.BLUE, 8)),
                listOf(number("human-a", UnoColor.GREEN, 1), number("human-b", UnoColor.YELLOW, 3)),
            ),
            top = number("top", UnoColor.RED, 2),
            currentSeat = 0,
        )
        val result = UnoBotGameRunner(game, listOf(UnoBot("p0", Random(1)))).runUntilBlocked(10)
        assertEquals(UnoBotRunStatus.WAITING_FOR_EXTERNAL_PLAYER, result.status)
        assertEquals(2, result.actionCount)
        assertEquals(1, game.state.players.single { it.playerId == "p0" }.hand.size)
        assertNull(game.state.catchWindow)
        assertEquals("p1", game.state.currentPlayerId)
    }

    @Test
    fun runnerReportsActionLimitInsteadOfLoopingForever() {
        val game = requireNotNull(UnoEngine.start(players(2), Random(88)).engine)
        val result = UnoBotGameRunner(game, bots(2, 99)).runUntilBlocked(1)
        assertEquals(UnoBotRunStatus.ACTION_LIMIT_REACHED, result.status)
        assertEquals(1, result.actionCount)
    }

    @Test(expected = IllegalArgumentException::class)
    fun runnerRejectsDuplicateBotRegistration() {
        val game = requireNotNull(UnoEngine.start(players(2), Random(1)).engine)
        UnoBotGameRunner(game, listOf(UnoBot("p0"), UnoBot("p0")))
    }

    private fun assertQuickGameCompletes(playerCount: Int, seed: Int) {
        val game = requireNotNull(UnoEngine.start(players(playerCount), Random(seed)).engine)
        val result = UnoBotGameRunner(game, bots(playerCount, seed + 1)).runUntilBlocked(20_000)
        assertEquals(UnoBotRunStatus.COMPLETED, result.status)
        assertEquals(UnoPhase.MATCH_FINISHED, game.state.phase)
        assertNotNull(game.state.matchWinnerId)
    }

    private fun bots(count: Int, seed: Int): List<UnoBot> =
        (0 until count).map { UnoBot("p$it", Random(seed + it)) }
}
