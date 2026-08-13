package com.offlinelandlord.game.uno.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnoTableLayoutMetricsTest {
    @Test fun huaweiLandscapeUsesVeryCompactBudgetWithoutCropping() {
        val metrics = metrics(width = 780f, height = 336f, players = 2, hand = 7)

        assertTrue(metrics.compact)
        assertTrue(metrics.veryCompact)
        assertTrue(metrics.requiredHeight <= 336f)
        assertEquals(334f, metrics.requiredHeight, 0.01f)
        assertEquals(80f, metrics.handCardHeight, 0.01f)
        assertTrue(metrics.handCardHeight in 76f..84f)
        assertTrue(metrics.handCardHeight < 96f)
        assertEquals(62f / 96f, metrics.handCardWidth / metrics.handCardHeight, 0.001f)
    }

    @Test fun normalLandscapePreservesExistingMeasurements() {
        val metrics = metrics(width = 854f, height = 480f, players = 2, hand = 7)

        assertFalse(metrics.compact)
        assertFalse(metrics.veryCompact)
        assertEquals(43f, metrics.headerHeight, 0.01f)
        assertEquals(82f, metrics.opponentAreaHeight, 0.01f)
        assertEquals(105f, metrics.centerAreaHeight, 0.01f)
        assertEquals(48f, metrics.actionAreaHeight, 0.01f)
        assertEquals(28f, metrics.handHeaderHeight, 0.01f)
        assertEquals(96f, metrics.handCardHeight, 0.01f)
        assertEquals(62f, metrics.handCardWidth, 0.01f)
        assertEquals(418f, metrics.requiredHeight, 0.01f)
    }

    @Test fun twentyByNineLandscapeUsesIntermediateCompactScale() {
        val metrics = metrics(width = 800f, height = 360f, players = 2, hand = 7)

        assertTrue(metrics.compact)
        assertFalse(metrics.veryCompact)
        assertTrue(metrics.requiredHeight <= 360f)
        assertTrue(metrics.handCardHeight >= 80f)
        assertTrue(metrics.handCardHeight < 96f)
    }

    @Test fun tallLandscapeDoesNotScalePastNormalMeasurements() {
        val normal = metrics(width = 854f, height = 480f, players = 2, hand = 7)
        val tall = metrics(width = 960f, height = 540f, players = 2, hand = 7)

        assertFalse(tall.compact)
        assertEquals(normal.headerHeight, tall.headerHeight, 0.01f)
        assertEquals(normal.handCardHeight, tall.handCardHeight, 0.01f)
        assertEquals(normal.handAreaHeight, tall.handAreaHeight, 0.01f)
    }

    @Test fun twoPlayerCompactLayoutKeepsOneOpponentInsideBudget() {
        val metrics = metrics(width = 780f, height = 336f, players = 2, hand = 7)

        assertEquals(1, metrics.opponentCount)
        assertTrue(metrics.opponentCardWidth <= 760f)
        assertTrue(metrics.requiredHeight <= metrics.availableHeight)
    }

    @Test fun threePlayerCompactLayoutKeepsOpponentRowInsideWidth() {
        val metrics = metrics(width = 780f, height = 336f, players = 3, hand = 7)

        assertEquals(2, metrics.opponentCount)
        assertTrue(metrics.opponentCardWidth * metrics.opponentCount <= 760f)
        assertTrue(metrics.requiredHeight <= metrics.availableHeight)
    }

    @Test fun fourPlayerCompactLayoutKeepsOpponentRowInsideWidth() {
        val metrics = metrics(width = 780f, height = 336f, players = 4, hand = 7)

        assertEquals(3, metrics.opponentCount)
        assertTrue(metrics.opponentCardWidth * metrics.opponentCount <= 760f)
        assertTrue(metrics.requiredHeight <= metrics.availableHeight)
    }

    @Test fun sevenCardsUseRelaxedSpacingWithoutChangingVerticalBudget() {
        val metrics = metrics(width = 780f, height = 336f, players = 2, hand = 7)

        assertEquals(5f, metrics.handSpacing, 0.01f)
        assertEquals(80f, metrics.handCardHeight, 0.01f)
    }

    @Test fun fifteenCardsTightenOnlyHorizontalSpacing() {
        val seven = metrics(width = 780f, height = 336f, players = 2, hand = 7)
        val fifteen = metrics(width = 780f, height = 336f, players = 2, hand = 15)

        assertEquals(3f, fifteen.handSpacing, 0.01f)
        assertEquals(seven.handCardHeight, fifteen.handCardHeight, 0.01f)
        assertEquals(seven.handAreaHeight, fifteen.handAreaHeight, 0.01f)
        assertEquals(seven.requiredHeight, fifteen.requiredHeight, 0.01f)
    }

    @Test fun twentyCardsUseMinimumSpacingWithoutChangingVerticalBudget() {
        val fifteen = metrics(width = 780f, height = 336f, players = 2, hand = 15)
        val twenty = metrics(width = 780f, height = 336f, players = 2, hand = 20)

        assertEquals(1f, twenty.handSpacing, 0.01f)
        assertEquals(fifteen.handCardHeight, twenty.handCardHeight, 0.01f)
        assertEquals(fifteen.handAreaHeight, twenty.handAreaHeight, 0.01f)
        assertEquals(fifteen.requiredHeight, twenty.requiredHeight, 0.01f)
    }

    private fun metrics(width: Float, height: Float, players: Int, hand: Int) =
        calculateUnoTableLayoutMetrics(width, height, players, hand)
}
