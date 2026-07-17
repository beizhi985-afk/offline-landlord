package com.offlinelandlord.game.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HandRulesTest {
    @Test
    fun recognizesBasicPatterns() {
        assertType(PatternType.SINGLE, cards(Rank.THREE, 1))
        assertType(PatternType.PAIR, cards(Rank.FIVE, 2))
        assertType(PatternType.TRIPLE, cards(Rank.SEVEN, 3))
        assertType(PatternType.TRIPLE_WITH_SINGLE, cards(Rank.NINE, 3) + cards(Rank.FOUR, 1))
        assertType(PatternType.TRIPLE_WITH_PAIR, cards(Rank.JACK, 3) + cards(Rank.SIX, 2))
    }

    @Test
    fun recognizesChainsAndRejectsTwoInStraight() {
        val straight = listOf(Rank.THREE, Rank.FOUR, Rank.FIVE, Rank.SIX, Rank.SEVEN).flatMap { cards(it, 1) }
        assertType(PatternType.STRAIGHT, straight)

        val pairs = listOf(Rank.EIGHT, Rank.NINE, Rank.TEN).flatMap { cards(it, 2) }
        assertType(PatternType.CONSECUTIVE_PAIRS, pairs)

        val invalid = listOf(Rank.TEN, Rank.JACK, Rank.QUEEN, Rank.KING, Rank.ACE, Rank.TWO).flatMap { cards(it, 1) }
        assertNull(HandRules.analyze(invalid))
    }

    @Test
    fun recognizesAirplanes() {
        val body = cards(Rank.THREE, 3) + cards(Rank.FOUR, 3)
        assertType(PatternType.AIRPLANE, body)
        assertType(PatternType.AIRPLANE_WITH_SINGLES, body + cards(Rank.SEVEN, 1) + cards(Rank.NINE, 1))
        assertType(PatternType.AIRPLANE_WITH_PAIRS, body + cards(Rank.SEVEN, 2) + cards(Rank.NINE, 2))
    }

    @Test
    fun recognizesFourWithWings() {
        assertType(PatternType.FOUR_WITH_TWO_SINGLES, cards(Rank.QUEEN, 4) + cards(Rank.THREE, 1) + cards(Rank.FIVE, 1))
        assertType(PatternType.FOUR_WITH_TWO_PAIRS, cards(Rank.QUEEN, 4) + cards(Rank.THREE, 2) + cards(Rank.FIVE, 2))
    }

    @Test
    fun bombAndRocketComparisonIsCorrect() {
        val pairAces = requireNotNull(HandRules.analyze(cards(Rank.ACE, 2)))
        val bombThrees = requireNotNull(HandRules.analyze(cards(Rank.THREE, 4)))
        val bombFours = requireNotNull(HandRules.analyze(cards(Rank.FOUR, 4)))
        val rocket = requireNotNull(
            HandRules.analyze(
                listOf(
                    Card(Suit.JOKER, Rank.SMALL_JOKER),
                    Card(Suit.JOKER, Rank.BIG_JOKER),
                ),
            ),
        )

        assertTrue(HandRules.beats(bombThrees, pairAces))
        assertTrue(HandRules.beats(bombFours, bombThrees))
        assertFalse(HandRules.beats(bombThrees, bombFours))
        assertTrue(HandRules.beats(rocket, bombFours))
        assertFalse(HandRules.beats(bombFours, rocket))
    }

    @Test
    fun samePatternRequiresSameLength() {
        val fiveCardStraight = requireNotNull(
            HandRules.analyze(listOf(Rank.THREE, Rank.FOUR, Rank.FIVE, Rank.SIX, Rank.SEVEN).flatMap { cards(it, 1) }),
        )
        val sixCardStraight = requireNotNull(
            HandRules.analyze(listOf(Rank.FOUR, Rank.FIVE, Rank.SIX, Rank.SEVEN, Rank.EIGHT, Rank.NINE).flatMap { cards(it, 1) }),
        )
        assertFalse(HandRules.beats(sixCardStraight, fiveCardStraight))
    }

    private fun assertType(type: PatternType, cards: List<Card>) {
        assertEquals(type, HandRules.analyze(cards)?.type)
    }

    private fun cards(rank: Rank, count: Int): List<Card> {
        val suits = listOf(Suit.CLUBS, Suit.DIAMONDS, Suit.HEARTS, Suit.SPADES)
        return suits.take(count).map { Card(it, rank) }
    }
}

