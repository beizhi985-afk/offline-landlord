package com.offlinelandlord.game.uno.singleplayer

import com.offlinelandlord.game.uno.core.UnoAction
import com.offlinelandlord.game.uno.core.UnoCardType
import com.offlinelandlord.game.uno.core.UnoColor
import com.offlinelandlord.game.uno.core.UnoDirection
import com.offlinelandlord.game.uno.core.UnoMatchMode
import com.offlinelandlord.game.uno.core.UnoPhase
import com.offlinelandlord.game.uno.core.UnoTestFixtures.action
import com.offlinelandlord.game.uno.core.UnoTestFixtures.engine
import com.offlinelandlord.game.uno.core.UnoTestFixtures.number
import com.offlinelandlord.game.uno.core.UnoTestFixtures.wild
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.TestScope
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class UnoSinglePlayerControllerTest {
    @Test
    fun createsTwoPlayerGame() = runTest {
        val controller = controller()
        controller.startGame(UnoSinglePlayerConfig(2), Random(1))
        advanceUntilIdle()
        assertEquals(2, controller.uiState.value.players.size)
    }

    @Test
    fun createsThreePlayerGame() = runTest {
        val controller = controller()
        controller.startGame(UnoSinglePlayerConfig(3), Random(2))
        advanceUntilIdle()
        assertEquals(3, controller.uiState.value.players.size)
    }

    @Test
    fun createsFourPlayerGame() = runTest {
        val controller = controller()
        controller.startGame(UnoSinglePlayerConfig(4), Random(3))
        advanceUntilIdle()
        assertEquals(4, controller.uiState.value.players.size)
    }

    @Test
    fun exactlyOnePlayerIsHumanAndOthersAreBots() = runTest {
        val controller = controller()
        controller.startGame(UnoSinglePlayerConfig(4), Random(4))
        advanceUntilIdle()
        val state = controller.uiState.value
        assertEquals(listOf("你", "机器人1", "机器人2", "机器人3"), state.players.sortedBy { it.seat }.map { it.name })
        assertEquals(1, state.players.count { it.isHuman })
        assertEquals(3, state.opponents.size)
    }

    @Test
    fun quickConfigurationCreatesQuickMatch() = runTest {
        val controller = controller()
        controller.startGame(UnoSinglePlayerConfig(2, UnoMatchMode.QUICK), Random(5))
        advanceUntilIdle()
        assertEquals(UnoMatchMode.QUICK, controller.uiState.value.config.matchMode)
    }

    @Test
    fun pointsConfigurationTargetsFiveHundred() = runTest {
        val controller = controller()
        controller.startGame(UnoSinglePlayerConfig(3, UnoMatchMode.POINTS), Random(6))
        advanceUntilIdle()
        assertEquals(500, controller.uiState.value.targetScore)
        assertEquals(UnoMatchMode.POINTS, controller.uiState.value.config.matchMode)
    }

    @Test
    fun humanActionIsAppliedThroughEngine() = runTest {
        val game = simpleHumanTurn()
        val controller = controller(game)
        controller.startGame(UnoSinglePlayerConfig(2), Random(1))
        assertTrue(controller.submitHumanActionAndWait(UnoAction.PlayCard("play")))
        assertFalse(controller.uiState.value.humanHand.any { it.cardId == "play" })
    }

    @Test
    fun botAutomaticallyActsUntilHumanTurn() = runTest {
        val game = engine(
            hands = listOf(
                listOf(number("human", UnoColor.BLUE, 8)),
                listOf(number("bot-play", UnoColor.RED, 5), number("bot-left", UnoColor.GREEN, 7)),
            ),
            top = number("top", UnoColor.RED, 2),
            currentSeat = 1,
        )
        val controller = controller(game)
        controller.startGame(UnoSinglePlayerConfig(2), Random(2))
        controller.awaitIdle()
        assertEquals(1, controller.uiState.value.opponents.single().remainingCardCount)
        assertEquals(UNO_HUMAN_PLAYER_ID, controller.uiState.value.currentPlayerId)
    }

    @Test
    fun botDeclaresUnoThenContinuesToPlay() = runTest {
        val game = engine(
            hands = listOf(
                listOf(number("human", UnoColor.BLUE, 8)),
                listOf(number("bot-play", UnoColor.RED, 5), number("bot-left", UnoColor.GREEN, 7)),
            ),
            top = number("top", UnoColor.RED, 2),
            currentSeat = 1,
        )
        val controller = controller(game)
        controller.startGame(UnoSinglePlayerConfig(2), Random(3))
        controller.awaitIdle()
        assertEquals(1, controller.uiState.value.opponents.single().remainingCardCount)
        assertTrue(controller.uiState.value.eventMessage?.endsWith("：UNO！") == true)
    }

    @Test
    fun botWildAutomaticallyChoosesColor() = runTest {
        val game = engine(
            hands = listOf(
                listOf(number("human", UnoColor.BLUE, 8)),
                listOf(wild("bot-wild"), number("bot-blue", UnoColor.BLUE, 7), number("bot-blue-2", UnoColor.BLUE, 6)),
            ),
            top = number("top", UnoColor.RED, 2),
            currentSeat = 1,
        )
        val controller = controller(game)
        controller.startGame(UnoSinglePlayerConfig(2), Random(4))
        controller.awaitIdle()
        assertEquals(UnoColor.BLUE, controller.uiState.value.activeColor)
        assertNotEquals(UnoPhase.CHOOSE_COLOR, controller.uiState.value.phase)
    }

    @Test
    fun botsStopWhenItIsHumanTurn() = runTest {
        val game = simpleHumanTurn()
        val controller = controller(game)
        controller.startGame(UnoSinglePlayerConfig(2), Random(5))
        controller.awaitIdle()
        assertEquals(UNO_HUMAN_PLAYER_ID, controller.uiState.value.currentPlayerId)
        assertFalse(controller.uiState.value.isBotThinking)
    }

    @Test
    fun roundFinishedDoesNotAutoStartNextRound() = runTest {
        val game = finishedRound(matchFinished = false)
        val controller = controller(game)
        controller.startGame(UnoSinglePlayerConfig(2, UnoMatchMode.POINTS), Random(6))
        controller.awaitIdle()
        assertEquals(UnoPhase.ROUND_FINISHED, controller.uiState.value.phase)
        assertEquals(1, controller.uiState.value.roundNumber)
    }

    @Test
    fun nextRoundUsesEngineAndReschedulesBots() = runTest {
        val game = finishedRound(matchFinished = false)
        val controller = controller(game)
        controller.startGame(UnoSinglePlayerConfig(2, UnoMatchMode.POINTS), Random(7))
        assertTrue(controller.submitHumanActionAndWait(UnoAction.StartNextRound))
        assertEquals(2, controller.uiState.value.roundNumber)
        assertNotEquals(UnoPhase.ROUND_FINISHED, controller.uiState.value.phase)
        assertFalse(controller.uiState.value.isActionInProgress)
    }

    @Test
    fun matchFinishedStopsAllBotScheduling() = runTest {
        val game = finishedRound(matchFinished = true)
        val controller = controller(game)
        controller.startGame(UnoSinglePlayerConfig(2), Random(8))
        controller.awaitIdle()
        assertEquals(UnoPhase.MATCH_FINISHED, controller.uiState.value.phase)
        assertFalse(controller.uiState.value.isBotThinking)
    }

    @Test
    fun duplicateSubmissionIsRejectedBeforeFirstActionRuns() = runTest {
        val controller = controller(simpleHumanTurn())
        controller.startGame(UnoSinglePlayerConfig(2), Random(9))
        assertTrue(controller.submitHumanAction(UnoAction.DrawCard))
        assertFalse(controller.submitHumanAction(UnoAction.DrawCard))
        advanceUntilIdle()
    }

    @Test
    fun staleIllegalActionShowsErrorWithoutCrashing() = runTest {
        val controller = controller(simpleHumanTurn())
        controller.startGame(UnoSinglePlayerConfig(2), Random(10))
        assertFalse(controller.submitHumanAction(UnoAction.PlayCard("missing")))
        assertNotNull(controller.uiState.value.errorMessage)
    }

    @Test
    fun clearGameCancelsControllerAndRemovesUiState() = runTest {
        val controller = controller()
        controller.startGame(UnoSinglePlayerConfig(2), Random(11))
        controller.clearGame()
        assertFalse(controller.uiState.value.gameStarted)
        assertNull(controller.uiState.value.phase)
    }

    @Test
    fun reverseDirectionIsReadFromEngineNotUiRules() = runTest {
        val game = engine(
            hands = listOf(listOf(number("human", UnoColor.BLUE, 8)), listOf(number("bot", UnoColor.GREEN, 7))),
            top = action("reverse", UnoColor.RED, UnoCardType.REVERSE),
            direction = UnoDirection.COUNTER_CLOCKWISE,
            currentSeat = 0,
        )
        val controller = controller(game)
        controller.startGame(UnoSinglePlayerConfig(2), Random(12))
        assertEquals(UnoDirection.COUNTER_CLOCKWISE, controller.uiState.value.direction)
    }

    private fun TestScope.controller(fixedEngine: com.offlinelandlord.game.uno.core.UnoEngine? = null) =
        UnoSinglePlayerController(
            scope = this,
            botDelayProvider = UnoBotDelayProvider.Immediate,
            engineFactory = if (fixedEngine == null) {
                UnoSinglePlayerEngineFactory { config, random ->
                    val players = (0 until config.playerCount).map { seat ->
                        com.offlinelandlord.game.uno.core.UnoPlayer("p$seat", if (seat == 0) "你" else "机器人$seat")
                    }
                    requireNotNull(com.offlinelandlord.game.uno.core.UnoEngine.start(players, random, config.matchMode, 500).engine)
                }
            } else {
                UnoSinglePlayerEngineFactory { _, _ -> fixedEngine }
            },
        )

    private fun simpleHumanTurn() = engine(
        hands = listOf(
            listOf(number("play", UnoColor.RED, 5), number("keep", UnoColor.BLUE, 8), number("keep2", UnoColor.GREEN, 4)),
            listOf(number("bot", UnoColor.YELLOW, 7), number("bot2", UnoColor.GREEN, 6)),
        ),
        top = number("top", UnoColor.RED, 2),
        drawPile = listOf(number("draw", UnoColor.YELLOW, 1)),
        currentSeat = 0,
    )

    private fun finishedRound(matchFinished: Boolean) = engine(
        hands = listOf(
            emptyList(),
            listOf(number("bot", UnoColor.YELLOW, 7)),
        ),
        top = number("top", UnoColor.RED, 2),
        currentSeat = null,
        phase = if (matchFinished) UnoPhase.MATCH_FINISHED else UnoPhase.ROUND_FINISHED,
        matchMode = UnoMatchMode.POINTS,
        scores = mapOf("p0" to if (matchFinished) 500 else 20, "p1" to 0),
        roundWinnerId = "p0",
        matchWinnerId = "p0".takeIf { matchFinished },
        lastRoundScore = 20,
    )
}
