package com.offlinelandlord.game.uno.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UnoDeckTest {
    private val deck = UnoDeckFactory.createClassicDeck()

    @Test
    fun classicDeckHas108Cards() = assertEquals(108, deck.size)

    @Test
    fun classicDeckHasAllFourColors() {
        assertEquals(UnoColor.entries.toSet(), deck.mapNotNull { it.color }.toSet())
    }

    @Test
    fun eachColorHasOneZero() {
        UnoColor.entries.forEach { color ->
            assertEquals(1, deck.count { it.color == color && it.type == UnoCardType.NUMBER && it.number == 0 })
        }
    }

    @Test
    fun eachColorHasTwoOfNumbersOneThroughNine() {
        UnoColor.entries.forEach { color ->
            (1..9).forEach { value ->
                assertEquals(2, deck.count { it.color == color && it.type == UnoCardType.NUMBER && it.number == value })
            }
        }
    }

    @Test
    fun eachColorHasTwoSkips() {
        UnoColor.entries.forEach { assertEquals(2, deck.count { card -> card.color == it && card.type == UnoCardType.SKIP }) }
    }

    @Test
    fun eachColorHasTwoReverses() {
        UnoColor.entries.forEach { assertEquals(2, deck.count { card -> card.color == it && card.type == UnoCardType.REVERSE }) }
    }

    @Test
    fun eachColorHasTwoDrawTwos() {
        UnoColor.entries.forEach { assertEquals(2, deck.count { card -> card.color == it && card.type == UnoCardType.DRAW_TWO }) }
    }

    @Test
    fun deckHasFourWilds() =
        assertEquals(4, deck.count { it.type == UnoCardType.WILD })

    @Test
    fun deckHasFourWildDrawFours() =
        assertEquals(4, deck.count { it.type == UnoCardType.WILD_DRAW_FOUR })

    @Test
    fun everyCardIdIsUnique() {
        assertEquals(108, deck.map { it.cardId }.distinct().size)
        assertTrue(deck.all { it.cardId.isNotBlank() })
    }
}
