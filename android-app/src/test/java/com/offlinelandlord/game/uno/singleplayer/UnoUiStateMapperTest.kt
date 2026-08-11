package com.offlinelandlord.game.uno.singleplayer

import com.offlinelandlord.game.uno.core.UnoActionType
import com.offlinelandlord.game.uno.core.UnoColor
import com.offlinelandlord.game.uno.core.UnoDirection
import com.offlinelandlord.game.uno.core.UnoMatchMode
import com.offlinelandlord.game.uno.core.UnoPhase
import com.offlinelandlord.game.uno.core.UnoTestFixtures.engine
import com.offlinelandlord.game.uno.core.UnoTestFixtures.number
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnoUiStateMapperTest {
    @Test
    fun humanSeesCompleteOwnHand() {
        val game = game()
        val state = map(game)
        assertEquals(game.viewFor("p0")!!.ownHand, state.humanHand)
    }

    @Test
    fun opponentsExposeOnlyPublicCardCounts() {
        val state = map(game())
        assertEquals(listOf(2), state.opponents.map { it.remainingCardCount })
        val names = UnoUiPlayer::class.java.declaredFields.map { it.name.lowercase() }
        assertFalse(names.any { it == "hand" || it == "cards" || it == "cardids" })
    }

    @Test
    fun currentPlayerMapsCorrectly() {
        val state = map(game(currentSeat = 1))
        assertEquals("p1", state.currentPlayerId)
        assertEquals("Player 1", state.currentPlayerName)
        assertTrue(state.players.single { it.playerId == "p1" }.isCurrentPlayer)
    }

    @Test
    fun activeColorMapsFromEngine() {
        assertEquals(UnoColor.BLUE, map(game(activeColor = UnoColor.BLUE)).activeColor)
    }

    @Test
    fun topDiscardMapsFromEngine() {
        val top = number("top-green", UnoColor.GREEN, 7)
        assertEquals(top, map(game(top = top)).topDiscardCard)
    }

    @Test
    fun directionMapsFromEngine() {
        assertEquals(UnoDirection.COUNTER_CLOCKWISE, map(game(direction = UnoDirection.COUNTER_CLOCKWISE)).direction)
    }

    @Test
    fun scoresMapWithoutHiddenHands() {
        val scores = mapOf("p0" to 120, "p1" to 235)
        val state = map(game(scores = scores))
        assertEquals(listOf(235, 120), state.ranking.map { it.score })
    }

    @Test
    fun phaseMapsFromEngine() {
        assertEquals(UnoPhase.AFTER_DRAW, map(game(phase = UnoPhase.AFTER_DRAW, drawnCardId = "human-red")).phase)
    }

    @Test
    fun legalCardIdsComeFromEngineQuery() {
        val state = map(game())
        assertEquals(setOf("human-red"), state.legalCardIds)
        assertTrue(UnoActionType.PLAY_CARD in state.availableActions)
    }

    @Test
    fun declareUnoButtonUsesAvailableActions() {
        val state = map(game(humanHand = listOf(number("human-red", UnoColor.RED, 5), number("other", UnoColor.BLUE, 8))))
        assertTrue(state.canDeclareUno)
    }

    @Test
    fun catchUnoButtonUsesEngineWindow() {
        val state = map(game(catchTarget = "p1"))
        assertTrue(state.canCatchUno)
        assertEquals("p1", state.catchTargetPlayerId)
    }

    @Test
    fun colorChooserIsVisibleOnlyForHuman() {
        val human = map(game(phase = UnoPhase.CHOOSE_COLOR, currentSeat = 0, colorChooser = "p0"))
        val bot = map(game(phase = UnoPhase.CHOOSE_COLOR, currentSeat = 1, colorChooser = "p1"))
        assertTrue(human.mustChooseColor)
        assertFalse(bot.mustChooseColor)
    }

    @Test
    fun drawPileAndLastRoundScoreArePublicUiFields() {
        val state = map(game(lastRoundScore = 77))
        assertEquals(2, state.drawPileCount)
        assertEquals(77, state.lastRoundScore)
        assertNull(state.matchWinnerId)
    }

    private fun game(
        humanHand: List<com.offlinelandlord.game.uno.core.UnoCard> = listOf(
            number("human-red", UnoColor.RED, 5),
            number("human-blue", UnoColor.BLUE, 8),
            number("human-green", UnoColor.GREEN, 4),
        ),
        top: com.offlinelandlord.game.uno.core.UnoCard = number("top", UnoColor.RED, 2),
        currentSeat: Int? = 0,
        activeColor: UnoColor? = top.color,
        direction: UnoDirection = UnoDirection.CLOCKWISE,
        phase: UnoPhase = UnoPhase.TURN,
        drawnCardId: String? = null,
        scores: Map<String, Int> = mapOf("p0" to 0, "p1" to 0),
        catchTarget: String? = null,
        colorChooser: String? = null,
        lastRoundScore: Int = 0,
    ) = engine(
        hands = listOf(
            humanHand,
            listOf(number("bot-a", UnoColor.YELLOW, 1), number("bot-b", UnoColor.GREEN, 6)),
        ),
        top = top,
        drawPile = listOf(number("draw-a", UnoColor.YELLOW, 3), number("draw-b", UnoColor.BLUE, 9)),
        currentSeat = currentSeat,
        activeColor = activeColor,
        direction = direction,
        phase = phase,
        drawnCardId = drawnCardId,
        scores = scores,
        catchTargetId = catchTarget,
        colorChooserId = colorChooser,
        lastRoundScore = lastRoundScore,
    )

    private fun map(game: com.offlinelandlord.game.uno.core.UnoEngine) =
        UnoUiStateMapper.from(game, UnoSinglePlayerConfig())
}
