package com.offlinelandlord.game.uno.core

import com.offlinelandlord.game.uno.core.UnoTestFixtures.action
import com.offlinelandlord.game.uno.core.UnoTestFixtures.number
import com.offlinelandlord.game.uno.core.UnoTestFixtures.wild
import com.offlinelandlord.game.uno.core.UnoTestFixtures.wildDrawFour
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnoRulesTest {
    private val topSeven = number("top", UnoColor.RED, 7)

    @Test
    fun sameColorIsPlayable() {
        val card = number("card", UnoColor.RED, 2)
        assertTrue(UnoRules.canPlayCard(card, listOf(card), UnoColor.RED, topSeven))
    }

    @Test
    fun sameNumberIsPlayable() {
        val card = number("card", UnoColor.BLUE, 7)
        assertTrue(UnoRules.canPlayCard(card, listOf(card), UnoColor.RED, topSeven))
    }

    @Test
    fun sameSkipSymbolIsPlayable() {
        val top = action("top", UnoColor.RED, UnoCardType.SKIP)
        val card = action("card", UnoColor.BLUE, UnoCardType.SKIP)
        assertTrue(UnoRules.canPlayCard(card, listOf(card), UnoColor.RED, top))
    }

    @Test
    fun sameReverseSymbolIsPlayable() {
        val top = action("top", UnoColor.RED, UnoCardType.REVERSE)
        val card = action("card", UnoColor.GREEN, UnoCardType.REVERSE)
        assertTrue(UnoRules.canPlayCard(card, listOf(card), UnoColor.RED, top))
    }

    @Test
    fun sameDrawTwoSymbolIsPlayable() {
        val top = action("top", UnoColor.RED, UnoCardType.DRAW_TWO)
        val card = action("card", UnoColor.YELLOW, UnoCardType.DRAW_TWO)
        assertTrue(UnoRules.canPlayCard(card, listOf(card), UnoColor.RED, top))
    }

    @Test
    fun completelyDifferentCardIsNotPlayable() {
        val card = number("card", UnoColor.BLUE, 2)
        assertFalse(UnoRules.canPlayCard(card, listOf(card), UnoColor.RED, topSeven))
    }

    @Test
    fun ordinaryWildIsAlwaysPlayable() {
        val card = wild()
        assertTrue(UnoRules.canPlayCard(card, listOf(card), UnoColor.RED, topSeven))
    }

    @Test
    fun wildDrawFourIsLegalWithoutActiveColorInHand() {
        val card = wildDrawFour()
        val hand = listOf(number("blue", UnoColor.BLUE, 5), card)
        assertTrue(UnoRules.canPlayCard(card, hand, UnoColor.RED, topSeven))
    }

    @Test
    fun wildDrawFourIsIllegalWithActiveColorInHand() {
        val card = wildDrawFour()
        val hand = listOf(number("red", UnoColor.RED, 9), card)
        assertFalse(UnoRules.canPlayCard(card, hand, UnoColor.RED, topSeven))
    }

    @Test
    fun wildDrawFourChecksColorRatherThanMatchingNumber() {
        val card = wildDrawFour()
        val hand = listOf(number("blue-seven", UnoColor.BLUE, 7), card)
        assertTrue(UnoRules.canPlayCard(card, hand, UnoColor.RED, topSeven))
    }

    @Test
    fun legalPlayableCardsUsesTheSameAuthoritativeRule() {
        val playable = number("red", UnoColor.RED, 1)
        val blocked = number("blue", UnoColor.BLUE, 2)
        val four = wildDrawFour()
        val legal = UnoRules.legalPlayableCards(listOf(playable, blocked, four), UnoColor.RED, topSeven)
        assertEquals(setOf(playable), legal.toSet())
    }
}
