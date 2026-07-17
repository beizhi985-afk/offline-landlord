package com.offlinelandlord.game.core

import org.junit.Assert.assertEquals
import org.junit.Test

class DeckFactoryTest {
    @Test
    fun deckContains54UniqueCards() {
        val deck = DeckFactory.orderedDeck()
        assertEquals(54, deck.size)
        assertEquals(54, deck.map { it.id }.toSet().size)
        assertEquals(2, deck.count { it.suit == Suit.JOKER })
    }
}

