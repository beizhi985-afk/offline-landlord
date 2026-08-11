package com.offlinelandlord.game.uno.core

import com.offlinelandlord.game.uno.core.UnoTestFixtures.allCards
import com.offlinelandlord.game.uno.core.UnoTestFixtures.engine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnoPenaltyDrawBoundaryTest {
    @Test
    fun drawTwoRecyclesEnoughCardsAndAppliesTheFullPenalty() {
        val scenario = drawTwoScenario(recyclableCount = 1)

        assertTrue(scenario.engine.applyAction("p0", UnoAction.PlayCard(scenario.penaltyCardId)).success)

        assertEquals(scenario.targetCardsBefore + 2, handSize(scenario.engine, scenario.targetPlayerId))
        assertContinuesWithAllClassicCards(scenario.engine)
    }

    @Test
    fun drawTwoStopsAfterEveryActuallyAvailableCardIsDrawn() {
        val scenario = drawTwoScenario(recyclableCount = 0)

        assertTrue(scenario.engine.applyAction("p0", UnoAction.PlayCard(scenario.penaltyCardId)).success)

        // The previous top discard is recyclable after the Draw Two becomes the new top.
        assertEquals(scenario.targetCardsBefore + 1, handSize(scenario.engine, scenario.targetPlayerId))
        assertContinuesWithAllClassicCards(scenario.engine)
    }

    @Test
    fun wildDrawFourRecyclesEnoughCardsAndAppliesTheFullPenalty() {
        val scenario = wildDrawFourScenario(recyclableCount = 3)

        assertTrue(scenario.engine.applyAction("p0", UnoAction.PlayCard(scenario.penaltyCardId)).success)
        assertTrue(scenario.engine.applyAction("p0", UnoAction.ChooseColor(UnoColor.BLUE)).success)

        assertEquals(scenario.targetCardsBefore + 4, handSize(scenario.engine, scenario.targetPlayerId))
        assertContinuesWithAllClassicCards(scenario.engine)
    }

    @Test
    fun wildDrawFourStopsAfterEveryActuallyAvailableCardIsDrawn() {
        val scenario = wildDrawFourScenario(recyclableCount = 1)

        assertTrue(scenario.engine.applyAction("p0", UnoAction.PlayCard(scenario.penaltyCardId)).success)
        assertTrue(scenario.engine.applyAction("p0", UnoAction.ChooseColor(UnoColor.BLUE)).success)

        // One discarded card plus the former top are available; the new +4 stays on top.
        assertEquals(scenario.targetCardsBefore + 2, handSize(scenario.engine, scenario.targetPlayerId))
        assertContinuesWithAllClassicCards(scenario.engine)
    }

    @Test
    fun catchUnoRecyclesEnoughCardsAndAppliesTheFullPenalty() {
        val scenario = catchScenario(recyclableCount = 2)

        assertTrue(scenario.engine.applyAction("p0", UnoAction.CatchUno(scenario.targetPlayerId)).success)

        assertEquals(scenario.targetCardsBefore + 2, handSize(scenario.engine, scenario.targetPlayerId))
        assertNull(scenario.engine.state.catchWindow)
        assertContinuesWithAllClassicCards(scenario.engine)
    }

    @Test
    fun catchUnoStopsAfterEveryActuallyAvailableCardIsDrawn() {
        val scenario = catchScenario(recyclableCount = 1)

        assertTrue(scenario.engine.applyAction("p0", UnoAction.CatchUno(scenario.targetPlayerId)).success)

        assertEquals(scenario.targetCardsBefore + 1, handSize(scenario.engine, scenario.targetPlayerId))
        assertNull(scenario.engine.state.catchWindow)
        assertContinuesWithAllClassicCards(scenario.engine)
    }

    private fun drawTwoScenario(recyclableCount: Int): PenaltyScenario {
        val deck = UnoDeckFactory.createClassicDeck().toMutableList()
        val top = deck.take { it.color == UnoColor.RED && it.type == UnoCardType.NUMBER && it.number == 0 }
        val drawTwo = deck.take { it.color == UnoColor.RED && it.type == UnoCardType.DRAW_TWO }
        val keep = deck.take { it.color == UnoColor.BLUE && it.type == UnoCardType.NUMBER }
        val recyclable = deck.takeMany(recyclableCount)
        val targetHand = deck.toList()
        return PenaltyScenario(
            engine = engine(
                hands = listOf(listOf(drawTwo, keep), targetHand),
                top = top,
                drawPile = emptyList(),
                discardPile = recyclable + top,
                activeColor = UnoColor.RED,
            ),
            penaltyCardId = drawTwo.cardId,
            targetPlayerId = "p1",
            targetCardsBefore = targetHand.size,
        )
    }

    private fun wildDrawFourScenario(recyclableCount: Int): PenaltyScenario {
        val deck = UnoDeckFactory.createClassicDeck().toMutableList()
        val top = deck.take { it.color == UnoColor.RED && it.type == UnoCardType.NUMBER && it.number == 0 }
        val wildDrawFour = deck.take { it.type == UnoCardType.WILD_DRAW_FOUR }
        val keep = deck.take { it.color == UnoColor.BLUE && it.type == UnoCardType.NUMBER }
        val recyclable = deck.takeMany(recyclableCount)
        val targetHand = deck.toList()
        return PenaltyScenario(
            engine = engine(
                hands = listOf(listOf(wildDrawFour, keep), targetHand),
                top = top,
                drawPile = emptyList(),
                discardPile = recyclable + top,
                activeColor = UnoColor.RED,
            ),
            penaltyCardId = wildDrawFour.cardId,
            targetPlayerId = "p1",
            targetCardsBefore = targetHand.size,
        )
    }

    private fun catchScenario(recyclableCount: Int): PenaltyScenario {
        val deck = UnoDeckFactory.createClassicDeck().toMutableList()
        val top = deck.take { it.color == UnoColor.RED && it.type == UnoCardType.NUMBER && it.number == 0 }
        val targetCard = deck.take { it.color == UnoColor.YELLOW && it.type == UnoCardType.NUMBER }
        val recyclable = deck.takeMany(recyclableCount)
        val catcherHand = deck.toList()
        return PenaltyScenario(
            engine = engine(
                hands = listOf(catcherHand, listOf(targetCard)),
                top = top,
                drawPile = emptyList(),
                discardPile = recyclable + top,
                activeColor = UnoColor.RED,
                catchTargetId = "p1",
            ),
            penaltyCardId = "not-used-for-catch",
            targetPlayerId = "p1",
            targetCardsBefore = 1,
        )
    }

    private fun assertContinuesWithAllClassicCards(engine: UnoEngine) {
        assertEquals(UnoPhase.TURN, engine.state.phase)
        assertEquals("p0", engine.state.currentPlayerId)
        val cards = allCards(engine.state)
        assertEquals(108, cards.size)
        assertEquals(108, cards.map(UnoCard::cardId).distinct().size)
    }

    private fun handSize(engine: UnoEngine, playerId: String): Int =
        requireNotNull(engine.state.players.firstOrNull { it.playerId == playerId }).hand.size

    private fun MutableList<UnoCard>.take(predicate: (UnoCard) -> Boolean): UnoCard {
        val index = indexOfFirst(predicate)
        check(index >= 0) { "Required classic UNO card was not found" }
        return removeAt(index)
    }

    private fun MutableList<UnoCard>.takeMany(count: Int): List<UnoCard> =
        List(count) { removeAt(lastIndex) }

    private data class PenaltyScenario(
        val engine: UnoEngine,
        val penaltyCardId: String,
        val targetPlayerId: String,
        val targetCardsBefore: Int,
    )
}
