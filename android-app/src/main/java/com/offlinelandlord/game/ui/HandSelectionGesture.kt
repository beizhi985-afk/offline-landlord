package com.offlinelandlord.game.ui

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

internal enum class HandGestureMode {
    SELECT,
    DESELECT,
}

/**
 * Pure state machine for the overlapping-hand pointer interaction.
 *
 * MOVE events only update [previewIds]. The permanent selection is returned
 * once from [finish], so reversing direction naturally shrinks the preview.
 */
internal class HandSelectionGesture(
    private val dragThresholdPx: Float,
) {
    private var cardIds: List<String> = emptyList()
    private var spacingPx: Float = 0f
    private var baselineSelectedIds: Set<String> = emptySet()
    private var startX: Float = 0f
    private var startIndex: Int = -1

    var mode: HandGestureMode? = null
        private set
    var currentIndex: Int = -1
        private set
    var previewIds: Set<String> = emptySet()
        private set
    var isDragging: Boolean = false
        private set
    var isActive: Boolean = false
        private set

    fun start(
        x: Float,
        cardIds: List<String>,
        spacingPx: Float,
        selectedIds: Set<String>,
    ): Boolean {
        reset()
        val index = handCardIndexAt(x, cardIds.size, spacingPx)
        if (index < 0) return false

        this.cardIds = cardIds.toList()
        this.spacingPx = spacingPx
        baselineSelectedIds = selectedIds.toSet()
        startX = x
        startIndex = index
        currentIndex = index
        mode = if (cardIds[index] in selectedIds) HandGestureMode.DESELECT else HandGestureMode.SELECT
        isActive = true
        return true
    }

    fun move(x: Float) {
        if (!isActive) return
        if (!isDragging && abs(x - startX) >= dragThresholdPx.coerceAtLeast(0f)) {
            isDragging = true
        }
        if (!isDragging) return

        currentIndex = handCardIndexAt(x, cardIds.size, spacingPx)
        previewIds = contiguousCardIds(cardIds, startIndex, currentIndex)
    }

    fun finish(): Set<String> {
        if (!isActive) return baselineSelectedIds
        val result = if (isDragging) {
            when (mode) {
                HandGestureMode.SELECT -> baselineSelectedIds + previewIds
                HandGestureMode.DESELECT -> baselineSelectedIds - previewIds
                null -> baselineSelectedIds
            }
        } else {
            val startId = cardIds[startIndex]
            if (startId in baselineSelectedIds) baselineSelectedIds - startId else baselineSelectedIds + startId
        }
        reset()
        return result
    }

    fun cancel(): Set<String> {
        val unchanged = baselineSelectedIds
        reset()
        return unchanged
    }

    private fun reset() {
        cardIds = emptyList()
        spacingPx = 0f
        baselineSelectedIds = emptySet()
        startX = 0f
        startIndex = -1
        currentIndex = -1
        mode = null
        previewIds = emptySet()
        isDragging = false
        isActive = false
    }
}

/** Maps an X coordinate to the topmost visible card in an overlapping hand. */
internal fun handCardIndexAt(x: Float, cardCount: Int, spacingPx: Float): Int {
    if (cardCount <= 0) return -1
    if (cardCount == 1 || spacingPx <= 0f) return 0
    return floor(x / spacingPx).toInt().coerceIn(0, cardCount - 1)
}

internal fun contiguousCardIds(cardIds: List<String>, startIndex: Int, currentIndex: Int): Set<String> {
    if (cardIds.isEmpty() || startIndex !in cardIds.indices || currentIndex !in cardIds.indices) return emptySet()
    val first = min(startIndex, currentIndex)
    val last = max(startIndex, currentIndex)
    return cardIds.subList(first, last + 1).toSet()
}
