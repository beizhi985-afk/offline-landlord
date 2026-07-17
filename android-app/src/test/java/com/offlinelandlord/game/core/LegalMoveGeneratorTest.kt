package com.offlinelandlord.game.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LegalMoveGeneratorTest {
    @Test
    fun everyGeneratedMoveIsLegalAndUniqueByRankShape() {
        val hand = cards(
            Rank.THREE, Rank.THREE, Rank.THREE,
            Rank.FOUR, Rank.FOUR, Rank.FOUR,
            Rank.FIVE, Rank.FIVE,
            Rank.SIX, Rank.SIX,
            Rank.SEVEN, Rank.EIGHT, Rank.NINE, Rank.TEN, Rank.JACK,
            Rank.SMALL_JOKER, Rank.BIG_JOKER,
        )

        val moves = LegalMoveGenerator.generate(hand)
        assertTrue(moves.isNotEmpty())
        assertTrue(moves.all { HandRules.analyze(it.cards) == it.pattern })
        val signatures = moves.map { move ->
            move.cards.groupingBy { it.rank }.eachCount().toSortedMap(compareBy { it.power }).toString()
        }
        assertEquals(signatures.size, signatures.distinct().size)
        assertTrue(moves.any { it.pattern.type == PatternType.ROCKET })
        assertTrue(moves.any { it.pattern.type == PatternType.STRAIGHT })
        assertTrue(moves.any { it.pattern.type == PatternType.CONSECUTIVE_PAIRS })
        assertTrue(moves.any { it.pattern.type == PatternType.AIRPLANE })
        assertTrue(moves.any { it.pattern.type == PatternType.AIRPLANE_WITH_SINGLES })
    }

    @Test
    fun responseMovesAllBeatPreviousPattern() {
        val hand = cards(
            Rank.FOUR, Rank.FIVE, Rank.SIX, Rank.SEVEN, Rank.EIGHT,
            Rank.NINE, Rank.NINE, Rank.NINE, Rank.NINE,
            Rank.SMALL_JOKER, Rank.BIG_JOKER,
        )
        val previous = CardPattern(PatternType.SINGLE, Rank.SEVEN, 1)
        val moves = LegalMoveGenerator.generate(hand, previous)

        assertFalse(moves.isEmpty())
        assertTrue(moves.all { HandRules.beats(it.pattern, previous) })
        assertTrue(moves.any { it.pattern.type == PatternType.BOMB })
        assertTrue(moves.any { it.pattern.type == PatternType.ROCKET })
    }

    private fun cards(vararg ranks: Rank): List<Card> {
        val available = DeckFactory.orderedDeck().groupBy { it.rank }
        val used = mutableMapOf<Rank, Int>()
        return ranks.map { rank ->
            val index = used.getOrDefault(rank, 0)
            used[rank] = index + 1
            requireNotNull(available[rank]?.getOrNull(index))
        }
    }
}
