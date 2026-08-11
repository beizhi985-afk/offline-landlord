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

class UnoEngineActionTest {
    private val top = number("top", UnoColor.RED, 2)
    private val filler = number("filler", UnoColor.BLUE, 8)

    @Test
    fun validPlayRemovesCardAndAdvancesTurn() {
        val card = number("play", UnoColor.RED, 5)
        val engine = engine(listOf(listOf(card, filler), listOf(number("p1", UnoColor.YELLOW, 1))), top)
        assertTrue(engine.applyAction("p0", UnoAction.PlayCard(card.cardId)).success)
        assertEquals("p1", engine.state.currentPlayerId)
        assertEquals(listOf(filler), engine.state.players[0].hand)
        assertEquals(card, engine.state.discardPile.last())
        assertEquals(UnoColor.RED, engine.state.activeColor)
    }

    @Test
    fun nonCurrentPlayerCannotPlay() {
        val card = number("play", UnoColor.RED, 5)
        val engine = engine(listOf(listOf(filler), listOf(card, number("p1-extra", UnoColor.BLUE, 3))), top)
        val result = engine.applyAction("p1", UnoAction.PlayCard(card.cardId))
        assertFalse(result.success)
        assertEquals(UnoErrorCode.NOT_YOUR_TURN, result.error?.code)
    }

    @Test
    fun unknownCardIdIsRejected() {
        val engine = engine(listOf(listOf(filler), listOf(number("p1", UnoColor.YELLOW, 1))), top)
        val result = engine.applyAction("p0", UnoAction.PlayCard("missing"))
        assertEquals(UnoErrorCode.CARD_NOT_IN_HAND, result.error?.code)
    }

    @Test
    fun unplayableCardIsRejectedWithoutChangingTurn() {
        val engine = engine(listOf(listOf(filler), listOf(number("p1", UnoColor.YELLOW, 1))), top)
        val before = engine.state
        val result = engine.applyAction("p0", UnoAction.PlayCard(filler.cardId))
        assertEquals(UnoErrorCode.CARD_NOT_PLAYABLE, result.error?.code)
        assertEquals(before, engine.state)
    }

    @Test
    fun illegalWildDrawFourGetsSpecificError() {
        val four = wildDrawFour()
        val red = number("red", UnoColor.RED, 9)
        val engine = engine(listOf(listOf(red, four), listOf(number("p1", UnoColor.YELLOW, 1))), top)
        val result = engine.applyAction("p0", UnoAction.PlayCard(four.cardId))
        assertEquals(UnoErrorCode.ILLEGAL_WILD_DRAW_FOUR, result.error?.code)
    }

    @Test
    fun playerMayDrawEvenWhenAnotherCardIsPlayable() {
        val playable = number("red", UnoColor.RED, 8)
        val drawn = number("drawn", UnoColor.BLUE, 3)
        val engine = engine(
            hands = listOf(listOf(playable), listOf(number("p1", UnoColor.YELLOW, 1))),
            top = top,
            drawPile = listOf(drawn),
        )
        assertTrue(engine.applyAction("p0", UnoAction.DrawCard).success)
        assertEquals(2, engine.state.players[0].hand.size)
    }

    @Test
    fun unplayableDrawnCardAutomaticallyEndsTurn() {
        val drawn = number("drawn", UnoColor.BLUE, 3)
        val engine = engine(
            listOf(listOf(filler), listOf(number("p1", UnoColor.YELLOW, 1))),
            top,
            drawPile = listOf(drawn),
        )
        assertTrue(engine.applyAction("p0", UnoAction.DrawCard).success)
        assertEquals(UnoPhase.TURN, engine.state.phase)
        assertEquals("p1", engine.state.currentPlayerId)
        assertNull(engine.state.drawnCardId)
    }

    @Test
    fun playableDrawnCardEntersAfterDrawPhase() {
        val drawn = number("drawn", UnoColor.RED, 3)
        val engine = engine(
            listOf(listOf(filler), listOf(number("p1", UnoColor.YELLOW, 1))),
            top,
            drawPile = listOf(drawn),
        )
        assertTrue(engine.applyAction("p0", UnoAction.DrawCard).success)
        assertEquals(UnoPhase.AFTER_DRAW, engine.state.phase)
        assertEquals(drawn.cardId, engine.state.drawnCardId)
        assertEquals("p0", engine.state.currentPlayerId)
    }

    @Test
    fun afterDrawOnlyTheNewCardMayBePlayed() {
        val old = number("old", UnoColor.RED, 8)
        val drawn = number("drawn", UnoColor.RED, 3)
        val engine = engine(
            listOf(listOf(old), listOf(number("p1", UnoColor.YELLOW, 1))),
            top,
            drawPile = listOf(drawn),
        )
        engine.applyAction("p0", UnoAction.DrawCard)
        val result = engine.applyAction("p0", UnoAction.PlayDrawnCard(old.cardId))
        assertEquals(UnoErrorCode.ONLY_DRAWN_CARD_CAN_BE_PLAYED, result.error?.code)
        assertEquals(UnoPhase.AFTER_DRAW, engine.state.phase)
    }

    @Test
    fun afterDrawWildDrawFourIsBlockedWhenTheOriginalHandHasTheActiveColor() {
        val four = wildDrawFour("drawn-four")
        val oldRed = number("old-red", UnoColor.RED, 8)
        val engine = engine(
            hands = listOf(listOf(oldRed, four), listOf(number("p1", UnoColor.YELLOW, 1))),
            top = top,
            phase = UnoPhase.AFTER_DRAW,
            drawnCardId = four.cardId,
        )

        val result = engine.applyAction("p0", UnoAction.PlayDrawnCard(four.cardId))

        assertEquals(UnoErrorCode.ILLEGAL_WILD_DRAW_FOUR, result.error?.code)
    }

    @Test
    fun drawnPlayableCardCanBePlayedImmediately() {
        val drawn = number("drawn", UnoColor.RED, 3)
        val engine = engine(
            listOf(listOf(filler), listOf(number("p1", UnoColor.YELLOW, 1))),
            top,
            drawPile = listOf(drawn),
        )
        engine.applyAction("p0", UnoAction.DrawCard)
        assertTrue(engine.applyAction("p0", UnoAction.PlayDrawnCard(drawn.cardId)).success)
        assertEquals("p1", engine.state.currentPlayerId)
        assertEquals(drawn, engine.state.discardPile.last())
    }

    @Test
    fun passAfterDrawKeepsCardAndEndsTurn() {
        val drawn = number("drawn", UnoColor.RED, 3)
        val engine = engine(
            listOf(listOf(filler), listOf(number("p1", UnoColor.YELLOW, 1))),
            top,
            drawPile = listOf(drawn),
        )
        engine.applyAction("p0", UnoAction.DrawCard)
        assertTrue(engine.applyAction("p0", UnoAction.PassAfterDraw).success)
        assertTrue(engine.state.players[0].hand.contains(drawn))
        assertEquals("p1", engine.state.currentPlayerId)
    }

    @Test
    fun skipJumpsOnePlayerInThreePlayerGame() {
        val skip = action("skip", UnoColor.RED, UnoCardType.SKIP)
        val engine = threePlayerEngine(skip)
        engine.applyAction("p0", UnoAction.PlayCard(skip.cardId))
        assertEquals("p2", engine.state.currentPlayerId)
    }

    @Test
    fun skipJumpsOnePlayerInFourPlayerGame() {
        val skip = action("skip", UnoColor.RED, UnoCardType.SKIP)
        val engine = engine(
            listOf(
                listOf(skip, filler),
                listOf(number("p1", UnoColor.YELLOW, 1)),
                listOf(number("p2", UnoColor.YELLOW, 2)),
                listOf(number("p3", UnoColor.YELLOW, 3)),
            ),
            top,
        )
        engine.applyAction("p0", UnoAction.PlayCard(skip.cardId))
        assertEquals("p2", engine.state.currentPlayerId)
    }

    @Test
    fun reverseChangesDirectionAndMovesCounterClockwiseWithThreePlayers() {
        val reverse = action("reverse", UnoColor.RED, UnoCardType.REVERSE)
        val engine = threePlayerEngine(reverse)
        engine.applyAction("p0", UnoAction.PlayCard(reverse.cardId))
        assertEquals(UnoDirection.COUNTER_CLOCKWISE, engine.state.direction)
        assertEquals("p2", engine.state.currentPlayerId)
    }

    @Test
    fun reverseChangesDirectionAndMovesCounterClockwiseWithFourPlayers() {
        val reverse = action("reverse", UnoColor.RED, UnoCardType.REVERSE)
        val engine = engine(
            listOf(
                listOf(reverse, filler),
                listOf(number("p1", UnoColor.YELLOW, 1)),
                listOf(number("p2", UnoColor.YELLOW, 2)),
                listOf(number("p3", UnoColor.YELLOW, 3)),
            ),
            top,
        )
        engine.applyAction("p0", UnoAction.PlayCard(reverse.cardId))
        assertEquals("p3", engine.state.currentPlayerId)
    }

    @Test
    fun reverseActsLikeSkipWithTwoPlayers() {
        val reverse = action("reverse", UnoColor.RED, UnoCardType.REVERSE)
        val engine = engine(
            listOf(listOf(reverse, filler), listOf(number("p1", UnoColor.YELLOW, 1))),
            top,
        )
        engine.applyAction("p0", UnoAction.PlayCard(reverse.cardId))
        assertEquals("p0", engine.state.currentPlayerId)
        assertEquals(UnoDirection.CLOCKWISE, engine.state.direction)
    }

    @Test
    fun drawTwoPenalizesNextPlayerAndSkipsThem() {
        val drawTwo = action("draw-two", UnoColor.RED, UnoCardType.DRAW_TWO)
        val penalties = listOf(
            number("penalty-1", UnoColor.BLUE, 1),
            number("penalty-2", UnoColor.GREEN, 2),
        )
        val engine = threePlayerEngine(drawTwo, penalties)
        engine.applyAction("p0", UnoAction.PlayCard(drawTwo.cardId))
        assertEquals(3, engine.state.players[1].hand.size)
        assertEquals("p2", engine.state.currentPlayerId)
    }

    @Test
    fun wildRequiresThePlayingPlayerToChooseColor() {
        val wild = wild()
        val engine = threePlayerEngine(wild)
        engine.applyAction("p0", UnoAction.PlayCard(wild.cardId))
        assertEquals(UnoPhase.CHOOSE_COLOR, engine.state.phase)
        assertFalse(engine.applyAction("p1", UnoAction.ChooseColor(UnoColor.GREEN)).success)
        assertTrue(engine.applyAction("p0", UnoAction.ChooseColor(UnoColor.GREEN)).success)
        assertEquals(UnoColor.GREEN, engine.state.activeColor)
        assertEquals("p1", engine.state.currentPlayerId)
    }

    @Test
    fun chosenWildColorControlsTheNextPlayersLegalCards() {
        val wild = wild("chosen-wild")
        val blue = number("blue", UnoColor.BLUE, 7)
        val red = number("red", UnoColor.RED, 9)
        val engine = engine(
            hands = listOf(listOf(wild, filler), listOf(blue, red)),
            top = top,
        )

        assertTrue(engine.applyAction("p0", UnoAction.PlayCard(wild.cardId)).success)
        assertTrue(engine.applyAction("p0", UnoAction.ChooseColor(UnoColor.BLUE)).success)

        assertEquals("p1", engine.state.currentPlayerId)
        assertEquals(UnoColor.BLUE, engine.state.activeColor)
        assertEquals(listOf(blue), engine.legalPlayableCards("p1"))
    }

    @Test
    fun wildDrawFourChoosesColorThenPenalizesAndSkips() {
        val four = wildDrawFour()
        val penalties = (1..4).map { number("penalty-$it", UnoColor.BLUE, it) }
        val engine = threePlayerEngine(four, penalties)
        engine.applyAction("p0", UnoAction.PlayCard(four.cardId))
        assertEquals(1, engine.state.players[1].hand.size)
        engine.applyAction("p0", UnoAction.ChooseColor(UnoColor.YELLOW))
        assertEquals(5, engine.state.players[1].hand.size)
        assertEquals("p2", engine.state.currentPlayerId)
        assertEquals(UnoColor.YELLOW, engine.state.activeColor)
    }

    @Test
    fun drawTwoCannotBeStackedBecausePenalizedPlayerIsSkipped() {
        val first = action("first", UnoColor.RED, UnoCardType.DRAW_TWO)
        val response = action("response", UnoColor.BLUE, UnoCardType.DRAW_TWO)
        val engine = engine(
            listOf(
                listOf(first, filler),
                listOf(response),
                listOf(number("p2", UnoColor.YELLOW, 1)),
            ),
            top,
            drawPile = listOf(
                number("d1", UnoColor.GREEN, 1),
                number("d2", UnoColor.GREEN, 2),
            ),
        )
        engine.applyAction("p0", UnoAction.PlayCard(first.cardId))
        val result = engine.applyAction("p1", UnoAction.PlayCard(response.cardId))
        assertEquals(UnoErrorCode.NOT_YOUR_TURN, result.error?.code)
    }

    @Test
    fun drawTwoCannotBeAnsweredWithWildDrawFourBecauseThePenalizedPlayerIsSkipped() {
        val drawTwo = action("draw-two", UnoColor.RED, UnoCardType.DRAW_TWO)
        val response = wildDrawFour("response-four")
        val engine = engine(
            hands = listOf(listOf(drawTwo, filler), listOf(response), listOf(number("p2", UnoColor.YELLOW, 1))),
            top = top,
            drawPile = listOf(number("d1", UnoColor.GREEN, 1), number("d2", UnoColor.GREEN, 2)),
        )

        assertTrue(engine.applyAction("p0", UnoAction.PlayCard(drawTwo.cardId)).success)
        val result = engine.applyAction("p1", UnoAction.PlayCard(response.cardId))

        assertEquals(UnoErrorCode.NOT_YOUR_TURN, result.error?.code)
    }

    @Test
    fun wildDrawFourCannotBeAnsweredWithDrawTwoBecauseThePenalizedPlayerIsSkipped() {
        val four = wildDrawFour("first-four")
        val response = action("response-two", UnoColor.BLUE, UnoCardType.DRAW_TWO)
        val engine = engine(
            hands = listOf(listOf(four, filler), listOf(response), listOf(number("p2", UnoColor.YELLOW, 1))),
            top = top,
            drawPile = (1..4).map { number("d$it", UnoColor.GREEN, it) },
        )

        assertTrue(engine.applyAction("p0", UnoAction.PlayCard(four.cardId)).success)
        assertTrue(engine.applyAction("p0", UnoAction.ChooseColor(UnoColor.BLUE)).success)
        val result = engine.applyAction("p1", UnoAction.PlayCard(response.cardId))

        assertEquals(UnoErrorCode.NOT_YOUR_TURN, result.error?.code)
    }

    private fun threePlayerEngine(card: UnoCard, drawPile: List<UnoCard> = listOf(number("draw", UnoColor.YELLOW, 9))) =
        engine(
            listOf(
                listOf(card, filler),
                listOf(number("p1", UnoColor.YELLOW, 1)),
                listOf(number("p2", UnoColor.YELLOW, 2)),
            ),
            top,
            drawPile = drawPile,
        )
}
