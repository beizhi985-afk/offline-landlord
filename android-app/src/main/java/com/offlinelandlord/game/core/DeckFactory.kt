package com.offlinelandlord.game.core

import kotlin.random.Random

object DeckFactory {
    private val normalRanks = Rank.entries.filter { it.power <= Rank.TWO.power }
    private val normalSuits = Suit.entries.filter { it != Suit.JOKER }

    fun orderedDeck(): List<Card> = buildList(54) {
        normalRanks.forEach { rank ->
            normalSuits.forEach { suit -> add(Card(suit, rank)) }
        }
        add(Card(Suit.JOKER, Rank.SMALL_JOKER))
        add(Card(Suit.JOKER, Rank.BIG_JOKER))
    }

    fun shuffledDeck(random: Random = Random.Default): List<Card> = orderedDeck().shuffled(random)
}

