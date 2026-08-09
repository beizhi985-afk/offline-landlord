package com.offlinelandlord.game.uno.core

import com.offlinelandlord.game.uno.core.UnoTestFixtures.action
import com.offlinelandlord.game.uno.core.UnoTestFixtures.engine
import com.offlinelandlord.game.uno.core.UnoTestFixtures.number
import com.offlinelandlord.game.uno.core.UnoTestFixtures.wild
import com.offlinelandlord.game.uno.core.UnoTestFixtures.wildDrawFour
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnoDeclarationAndScoringTest {
    private val top = number("top", UnoColor.RED, 2)

    @Test
    fun playerWithTwoCardsMayDeclareUno() {
        val engine = twoCardTurn()
        assertTrue(engine.applyAction("p0", UnoAction.DeclareUno).success)
        assertEquals("p0", engine.state.unoDeclaredPlayerId)
    }

    @Test
    fun playerWithThreeCardsCannotDeclareUno() {
        val engine = engine(
            listOf(
                listOf(
                    number("a", UnoColor.RED, 1),
                    number("b", UnoColor.BLUE, 2),
                    number("c", UnoColor.GREEN, 3),
                ),
                listOf(number("p1", UnoColor.YELLOW, 1)),
            ),
            top,
        )
        assertEquals(
            UnoErrorCode.CANNOT_DECLARE_UNO,
            engine.applyAction("p0", UnoAction.DeclareUno).error?.code,
        )
    }

    @Test
    fun playerWithOneCardCannotDeclareUno() {
        val engine = engine(
            listOf(listOf(number("a", UnoColor.RED, 1)), listOf(number("p1", UnoColor.YELLOW, 1))),
            top,
        )
        assertEquals(
            UnoErrorCode.CANNOT_DECLARE_UNO,
            engine.applyAction("p0", UnoAction.DeclareUno).error?.code,
        )
    }

    @Test
    fun nonCurrentPlayerCannotDeclareUno() {
        val engine = engine(
            listOf(
                listOf(number("p0", UnoColor.BLUE, 1)),
                listOf(number("a", UnoColor.RED, 1), number("b", UnoColor.BLUE, 2)),
            ),
            top,
        )
        assertEquals(UnoErrorCode.NOT_YOUR_TURN, engine.applyAction("p1", UnoAction.DeclareUno).error?.code)
    }

    @Test
    fun repeatedUnoDeclarationIsRejected() {
        val engine = twoCardTurn()
        engine.applyAction("p0", UnoAction.DeclareUno)
        assertEquals(
            UnoErrorCode.ALREADY_DECLARED_UNO,
            engine.applyAction("p0", UnoAction.DeclareUno).error?.code,
        )
    }

    @Test
    fun correctDeclarationPreventsCatchWindowWhenOneCardRemains() {
        val engine = twoCardTurn()
        val playable = engine.state.players[0].hand.first { it.color == UnoColor.RED }
        engine.applyAction("p0", UnoAction.DeclareUno)
        engine.applyAction("p0", UnoAction.PlayCard(playable.cardId))
        assertEquals(1, engine.state.players[0].hand.size)
        assertNull(engine.state.catchWindow)
    }

    @Test
    fun missingDeclarationOpensCatchWindowWhenOneCardRemains() {
        val engine = twoCardTurn()
        val playable = engine.state.players[0].hand.first { it.color == UnoColor.RED }
        engine.applyAction("p0", UnoAction.PlayCard(playable.cardId))
        assertEquals("p0", engine.state.catchWindow?.targetPlayerId)
    }

    @Test
    fun anotherPlayerCanCatchUnoAndTargetDrawsTwo() {
        val engine = engineWithOpenCatch()
        assertTrue(engine.applyAction("p1", UnoAction.CatchUno("p0")).success)
        assertEquals(3, engine.state.players[0].hand.size)
        assertNull(engine.state.catchWindow)
    }

    @Test
    fun playerCannotCatchThemselves() {
        val engine = engineWithOpenCatch()
        val result = engine.applyAction("p0", UnoAction.CatchUno("p0"))
        assertEquals(UnoErrorCode.CANNOT_CATCH_SELF, result.error?.code)
        assertEquals("p0", engine.state.catchWindow?.targetPlayerId)
    }

    @Test
    fun wrongCatchTargetIsRejected() {
        val engine = engineWithOpenCatch()
        val result = engine.applyAction("p0", UnoAction.CatchUno("p1"))
        assertEquals(UnoErrorCode.INVALID_UNO_CATCH_TARGET, result.error?.code)
    }

    @Test
    fun nextPlayersValidPlayClosesCatchWindowBeforePlaying() {
        val play = number("play", UnoColor.RED, 7)
        val engine = engine(
            hands = listOf(
                listOf(number("caught", UnoColor.BLUE, 1)),
                listOf(
                    play,
                    number("p1-extra-a", UnoColor.GREEN, 3),
                    number("p1-extra-b", UnoColor.BLUE, 4),
                ),
            ),
            top = top,
            currentSeat = 1,
            catchTargetId = "p0",
        )
        assertTrue(engine.applyAction("p1", UnoAction.PlayCard(play.cardId)).success)
        assertNull(engine.state.catchWindow)
        assertEquals(
            UnoErrorCode.UNO_CATCH_WINDOW_CLOSED,
            engine.applyAction("p1", UnoAction.CatchUno("p0")).error?.code,
        )
    }

    @Test
    fun nextPlayersValidDrawClosesCatchWindow() {
        val engine = engine(
            hands = listOf(
                listOf(number("caught", UnoColor.BLUE, 1)),
                listOf(number("p1", UnoColor.BLUE, 4)),
            ),
            top = top,
            currentSeat = 1,
            catchTargetId = "p0",
            drawPile = listOf(number("draw", UnoColor.GREEN, 5)),
        )
        assertTrue(engine.applyAction("p1", UnoAction.DrawCard).success)
        assertNull(engine.state.catchWindow)
    }

    @Test
    fun nextPlayersValidDeclarationClosesOldWindow() {
        val engine = engine(
            hands = listOf(
                listOf(number("caught", UnoColor.BLUE, 1)),
                listOf(number("p1-a", UnoColor.RED, 4), number("p1-b", UnoColor.GREEN, 5)),
            ),
            top = top,
            currentSeat = 1,
            catchTargetId = "p0",
        )
        assertTrue(engine.applyAction("p1", UnoAction.DeclareUno).success)
        assertNull(engine.state.catchWindow)
        assertEquals("p1", engine.state.unoDeclaredPlayerId)
    }

    @Test
    fun invalidActionDoesNotCloseCatchWindow() {
        val blocked = number("blocked", UnoColor.BLUE, 8)
        val engine = engine(
            hands = listOf(
                listOf(number("caught", UnoColor.BLUE, 1)),
                listOf(blocked, number("p1-extra", UnoColor.GREEN, 5)),
            ),
            top = top,
            currentSeat = 1,
            catchTargetId = "p0",
        )
        assertFalse(engine.applyAction("p1", UnoAction.PlayCard(blocked.cardId)).success)
        assertEquals("p0", engine.state.catchWindow?.targetPlayerId)
    }

    @Test
    fun lastNumberCardFinishesQuickMatchAndScoresOpponentsHand() {
        val last = number("last", UnoColor.RED, 5)
        val opponentCards = listOf(number("nine", UnoColor.BLUE, 9), wild("opponent-wild"))
        val engine = engine(listOf(listOf(last), opponentCards), top)
        assertTrue(engine.applyAction("p0", UnoAction.PlayCard(last.cardId)).success)
        assertEquals(UnoPhase.MATCH_FINISHED, engine.state.phase)
        assertEquals("p0", engine.state.roundWinnerId)
        assertEquals("p0", engine.state.matchWinnerId)
        assertEquals(59, engine.state.scores.getValue("p0"))
    }

    @Test
    fun lastSkipFinishesWithoutWaitingForAnotherTurn() {
        val last = action("last-skip", UnoColor.RED, UnoCardType.SKIP)
        val engine = engine(listOf(listOf(last), listOf(number("p1", UnoColor.BLUE, 1))), top)
        engine.applyAction("p0", UnoAction.PlayCard(last.cardId))
        assertEquals(UnoPhase.MATCH_FINISHED, engine.state.phase)
    }

    @Test
    fun lastReverseFinishesWithoutWaitingForAnotherTurn() {
        val last = action("last-reverse", UnoColor.RED, UnoCardType.REVERSE)
        val engine = engine(listOf(listOf(last), listOf(number("p1", UnoColor.BLUE, 1))), top)
        engine.applyAction("p0", UnoAction.PlayCard(last.cardId))
        assertEquals(UnoPhase.MATCH_FINISHED, engine.state.phase)
    }

    @Test
    fun lastOrdinaryWildFinishesWithoutColorChoice() {
        val last = wild("last-wild")
        val engine = engine(listOf(listOf(last), listOf(number("p1", UnoColor.BLUE, 1))), top)
        engine.applyAction("p0", UnoAction.PlayCard(last.cardId))
        assertEquals(UnoPhase.MATCH_FINISHED, engine.state.phase)
        assertNull(engine.state.colorChooserPlayerId)
    }

    @Test
    fun lastDrawTwoAppliesPenaltyBeforeScoring() {
        val last = action("last-draw-two", UnoColor.RED, UnoCardType.DRAW_TWO)
        val penalties = listOf(wild("penalty-wild"), number("penalty-five", UnoColor.GREEN, 5))
        val engine = engine(
            listOf(listOf(last), listOf(number("opponent-one", UnoColor.BLUE, 1))),
            top,
            drawPile = penalties,
        )
        engine.applyAction("p0", UnoAction.PlayCard(last.cardId))
        assertEquals(3, engine.state.players[1].hand.size)
        assertEquals(56, engine.state.lastRoundScore)
        assertEquals(56, engine.state.scores.getValue("p0"))
    }

    @Test
    fun lastWildDrawFourAppliesPenaltyAfterColorChoiceBeforeScoring() {
        val last = wildDrawFour("last-four")
        val penalties = (1..4).map { number("penalty-$it", UnoColor.GREEN, it) }
        val engine = engine(
            listOf(listOf(last), listOf(number("opponent-one", UnoColor.BLUE, 1))),
            top,
            drawPile = penalties,
        )
        engine.applyAction("p0", UnoAction.PlayCard(last.cardId))
        assertEquals(UnoPhase.CHOOSE_COLOR, engine.state.phase)
        assertTrue(engine.applyAction("p0", UnoAction.ChooseColor(UnoColor.BLUE)).success)
        assertEquals(5, engine.state.players[1].hand.size)
        assertEquals(11, engine.state.lastRoundScore)
        assertEquals(UnoPhase.MATCH_FINISHED, engine.state.phase)
    }

    @Test
    fun numberCardScoresItsFaceValue() {
        assertEquals(9, UnoScoring.cardPoints(number("nine", UnoColor.RED, 9)))
    }

    @Test
    fun coloredActionCardsScoreTwentyEach() {
        assertEquals(20, UnoScoring.cardPoints(action("skip", UnoColor.RED, UnoCardType.SKIP)))
        assertEquals(20, UnoScoring.cardPoints(action("reverse", UnoColor.RED, UnoCardType.REVERSE)))
        assertEquals(20, UnoScoring.cardPoints(action("draw-two", UnoColor.RED, UnoCardType.DRAW_TWO)))
    }

    @Test
    fun wildCardsScoreFiftyEach() {
        assertEquals(50, UnoScoring.cardPoints(wild()))
        assertEquals(50, UnoScoring.cardPoints(wildDrawFour()))
    }

    @Test
    fun mixedHandScoreIsSummedExactly() {
        val hand = listOf(
            number("nine", UnoColor.RED, 9),
            action("skip", UnoColor.BLUE, UnoCardType.SKIP),
            wild(),
            wildDrawFour(),
        )
        assertEquals(129, UnoScoring.handPoints(hand))
    }

    private fun twoCardTurn(): UnoEngine = engine(
        listOf(
            listOf(number("playable", UnoColor.RED, 5), number("remaining", UnoColor.BLUE, 8)),
            listOf(number("p1", UnoColor.YELLOW, 1)),
        ),
        top,
    )

    private fun engineWithOpenCatch(): UnoEngine = engine(
        hands = listOf(
            listOf(number("remaining", UnoColor.BLUE, 8)),
            listOf(number("p1", UnoColor.YELLOW, 1)),
        ),
        top = top,
        currentSeat = 1,
        catchTargetId = "p0",
        drawPile = listOf(
            number("penalty-one", UnoColor.GREEN, 4),
            number("penalty-two", UnoColor.GREEN, 5),
        ),
    )
}
