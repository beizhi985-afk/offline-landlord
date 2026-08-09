package com.offlinelandlord.game.uno.bot

import com.offlinelandlord.game.uno.core.UnoPhase
import com.offlinelandlord.game.uno.core.UnoTestFixtures.engine
import com.offlinelandlord.game.uno.core.UnoTestFixtures.number
import com.offlinelandlord.game.uno.core.UnoColor
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnoBotObservationTest {
    private val top = number("top", UnoColor.RED, 2)

    @Test
    fun observationContainsBotsOwnCompleteHand() {
        val own = listOf(number("own-a", UnoColor.RED, 5), number("own-b", UnoColor.BLUE, 7))
        val game = engine(listOf(own, listOf(number("hidden", UnoColor.GREEN, 1))), top)
        assertEquals(own, requireNotNull(UnoBot("p0", Random(1)).observe(game)).ownHand)
    }

    @Test
    fun opponentObservationHasCountButNoHandField() {
        val game = engine(
            listOf(
                listOf(number("own", UnoColor.RED, 5)),
                listOf(number("hidden-a", UnoColor.GREEN, 1), number("hidden-b", UnoColor.BLUE, 3)),
            ),
            top,
        )
        val observation = requireNotNull(UnoBot("p0", Random(1)).observe(game))
        assertEquals(2, observation.players.single { it.playerId == "p1" }.remainingCardCount)
        val fieldNames = UnoBotPlayerObservation::class.java.declaredFields.map { it.name.lowercase() }
        assertFalse(fieldNames.any { it == "hand" || it == "cards" || it == "cardids" })
    }

    @Test
    fun changingHiddenCardsWithoutChangingCountProducesSameObservation() {
        val own = listOf(
            number("own-red", UnoColor.RED, 5),
            number("own-blue", UnoColor.BLUE, 5),
            number("own-extra", UnoColor.GREEN, 8),
        )
        val first = engine(
            listOf(own, listOf(number("hidden-a", UnoColor.GREEN, 1), number("hidden-b", UnoColor.BLUE, 3))),
            top,
        )
        val second = engine(
            listOf(own, listOf(number("other-a", UnoColor.YELLOW, 9), number("other-b", UnoColor.RED, 7))),
            top,
        )
        assertEquals(UnoBot("p0", Random(3)).observe(first), UnoBot("p0", Random(3)).observe(second))
    }

    @Test
    fun fixedSeedDecisionIgnoresChangedHiddenCardFaces() {
        val own = listOf(
            number("own-red", UnoColor.RED, 5),
            number("own-blue", UnoColor.BLUE, 5),
            number("own-extra", UnoColor.GREEN, 8),
        )
        val first = engine(
            listOf(own, listOf(number("hidden-a", UnoColor.GREEN, 1), number("hidden-b", UnoColor.BLUE, 3))),
            top,
        )
        val second = engine(
            listOf(own, listOf(number("other-a", UnoColor.YELLOW, 9), number("other-b", UnoColor.RED, 7))),
            top,
        )
        assertEquals(
            UnoBot("p0", Random(123)).chooseAction(first),
            UnoBot("p0", Random(123)).chooseAction(second),
        )
    }

    @Test
    fun drawnCardIdIsHiddenFromOtherPlayers() {
        val drawn = number("secret-drawn", UnoColor.RED, 5)
        val game = engine(
            hands = listOf(
                listOf(drawn, number("p0-old", UnoColor.BLUE, 8)),
                listOf(number("p1", UnoColor.GREEN, 1)),
            ),
            top = top,
            phase = UnoPhase.AFTER_DRAW,
            currentSeat = 0,
            drawnCardId = drawn.cardId,
        )
        assertEquals(drawn.cardId, UnoBot("p0").observe(game)?.drawnCardId)
        assertNull(UnoBot("p1").observe(game)?.drawnCardId)
    }

    @Test
    fun unknownPlayerCannotReceiveObservation() {
        val game = engine(
            listOf(listOf(number("p0", UnoColor.RED, 5)), listOf(number("p1", UnoColor.BLUE, 1))),
            top,
        )
        assertNull(UnoBot("missing").observe(game))
        assertTrue(game.viewFor("missing") == null)
    }
}
