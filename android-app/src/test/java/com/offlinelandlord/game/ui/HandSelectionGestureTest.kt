package com.offlinelandlord.game.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HandSelectionGestureTest {
    private val cards = listOf("3", "4", "5", "6", "7", "8", "9", "10", "J")

    @Test
    fun tapSelectsOneCard() {
        val gesture = gesture(startX = 10f)
        assertEquals(setOf("4"), gesture.finish())
    }

    @Test
    fun tappingSelectedCardDeselectsIt() {
        val gesture = gesture(startX = 10f, selected = setOf("4", "J"))
        assertEquals(setOf("J"), gesture.finish())
    }

    @Test
    fun dragLeftToRightSelectsContinuousRange() {
        val gesture = gesture(startX = 10f)
        gesture.move(50f)
        assertEquals(setOf("4", "5", "6", "7", "8"), gesture.finish())
    }

    @Test
    fun dragRightToLeftSelectsContinuousRange() {
        val gesture = gesture(startX = 50f)
        gesture.move(10f)
        assertEquals(setOf("4", "5", "6", "7", "8"), gesture.finish())
    }

    @Test
    fun dragFromUnselectedCardLocksSelectMode() {
        val gesture = gesture(startX = 10f, selected = setOf("6"))
        gesture.move(40f)
        assertEquals(HandGestureMode.SELECT, gesture.mode)
        assertEquals(setOf("4", "5", "6", "7"), gesture.finish())
    }

    @Test
    fun dragFromSelectedCardLocksDeselectMode() {
        val gesture = gesture(startX = 10f, selected = setOf("4", "5", "6", "7", "J"))
        gesture.move(40f)
        assertEquals(HandGestureMode.DESELECT, gesture.mode)
        assertEquals(setOf("J"), gesture.finish())
    }

    @Test
    fun reversingDragShrinksPreviewToCurrentRange() {
        val gesture = gesture(startX = 10f)
        gesture.move(70f)
        assertEquals(setOf("4", "5", "6", "7", "8", "9", "10"), gesture.previewIds)
        gesture.move(40f)
        assertEquals(setOf("4", "5", "6", "7"), gesture.previewIds)
    }

    @Test
    fun draggingPastLeftEdgeClampsToFirstCard() {
        val gesture = gesture(startX = 40f)
        gesture.move(-1_000f)
        assertEquals(setOf("3", "4", "5", "6", "7"), gesture.finish())
    }

    @Test
    fun draggingPastRightEdgeClampsToLastCard() {
        val gesture = gesture(startX = 40f)
        gesture.move(1_000f)
        assertEquals(setOf("7", "8", "9", "10", "J"), gesture.finish())
    }

    @Test
    fun duplicateRanksWithDifferentIdsRemainIndependent() {
        val duplicateRanks = listOf("seven-hearts", "seven-spades")
        val gesture = HandSelectionGesture(dragThresholdPx = 8f)
        assertTrue(gesture.start(0f, duplicateRanks, spacingPx = 10f, selectedIds = emptySet()))
        assertEquals(setOf("seven-hearts"), gesture.finish())
    }

    @Test
    fun movementBelowThresholdRemainsTap() {
        val gesture = gesture(startX = 10f)
        gesture.move(17.9f)
        assertFalse(gesture.isDragging)
        assertEquals(setOf("4"), gesture.finish())
    }

    @Test
    fun reachingThresholdCommitsDragWithoutExtraTap() {
        val gesture = gesture(startX = 0f)
        gesture.move(25f)
        assertTrue(gesture.isDragging)
        assertEquals(setOf("3", "4", "5"), gesture.finish())
    }

    @Test
    fun dragPreservesPreviouslySelectedCardsOutsideRange() {
        val gesture = gesture(startX = 20f, selected = setOf("3", "J"))
        gesture.move(50f)
        assertEquals(setOf("3", "5", "6", "7", "8", "J"), gesture.finish())
    }

    @Test
    fun finishReturnsEveryDraggedUniqueCardIdForExistingPlayFlow() {
        val gesture = gesture(startX = 20f)
        gesture.move(50f)
        val idsPassedToPlay = gesture.finish().toList()
        assertEquals(setOf("5", "6", "7", "8"), idsPassedToPlay.toSet())
    }

    @Test
    fun cancelClearsPreviewAndKeepsPermanentSelection() {
        val gesture = gesture(startX = 20f, selected = setOf("3", "J"))
        gesture.move(50f)
        assertTrue(gesture.previewIds.isNotEmpty())
        assertEquals(setOf("3", "J"), gesture.cancel())
        assertTrue(gesture.previewIds.isEmpty())
        assertFalse(gesture.isActive)
    }

    @Test
    fun overlappingCardsUseSpacingRatherThanCardWidthForIndex() {
        assertEquals(0, handCardIndexAt(x = 9.9f, cardCount = 9, spacingPx = 10f))
        assertEquals(1, handCardIndexAt(x = 10f, cardCount = 9, spacingPx = 10f))
        assertEquals(5, handCardIndexAt(x = 59f, cardCount = 9, spacingPx = 10f))
    }

    @Test
    fun emptyHandCannotStartGesture() {
        val gesture = HandSelectionGesture(dragThresholdPx = 8f)
        assertFalse(gesture.start(0f, emptyList(), spacingPx = 10f, selectedIds = emptySet()))
        assertTrue(gesture.previewIds.isEmpty())
    }

    private fun gesture(startX: Float, selected: Set<String> = emptySet()): HandSelectionGesture {
        return HandSelectionGesture(dragThresholdPx = 8f).also {
            assertTrue(it.start(startX, cards, spacingPx = 10f, selectedIds = selected))
        }
    }
}
