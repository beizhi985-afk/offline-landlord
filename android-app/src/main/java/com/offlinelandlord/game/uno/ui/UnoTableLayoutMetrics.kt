package com.offlinelandlord.game.uno.ui

import kotlin.math.min

/**
 * Pure presentation measurements for the one shared UNO table.
 *
 * The table can be rendered inside a short landscape window (for example 780 x 336 dp)
 * without creating a device-specific or LAN-specific layout.
 */
internal data class UnoTableLayoutMetrics(
    val availableWidth: Float,
    val availableHeight: Float,
    val playerCount: Int,
    val localHandCount: Int,
    val compact: Boolean,
    val veryCompact: Boolean,
    val contentPaddingHorizontal: Float,
    val contentPaddingVertical: Float,
    val headerHeight: Float,
    val exitButtonWidth: Float,
    val opponentAreaHeight: Float,
    val opponentCardHeight: Float,
    val opponentCardWidth: Float,
    val centerAreaHeight: Float,
    val pileCardHeight: Float,
    val pileCardWidth: Float,
    val actionAreaHeight: Float,
    val handHeaderHeight: Float,
    val handAreaHeight: Float,
    val handCardHeight: Float,
    val handCardWidth: Float,
    val verticalGap: Float,
    val handSpacing: Float,
) {
    val opponentCount: Int get() = (playerCount - 1).coerceIn(1, 3)

    /** Every vertical region that must remain visible without scrolling the game table. */
    val requiredHeight: Float
        get() = contentPaddingVertical * 2 +
            headerHeight + opponentAreaHeight + centerAreaHeight + actionAreaHeight + handHeaderHeight + handAreaHeight +
            verticalGap * 5
}

/**
 * Calculates a single, continuously scaled layout. 418dp is the original table budget;
 * 336dp is the smallest supported landscape content height from the Huawei field sample.
 */
internal fun calculateUnoTableLayoutMetrics(
    availableWidth: Float,
    availableHeight: Float,
    playerCount: Int,
    localHandCount: Int,
): UnoTableLayoutMetrics {
    val safeWidth = availableWidth.coerceAtLeast(1f)
    val safeHeight = availableHeight.coerceAtLeast(MIN_SUPPORTED_HEIGHT)
    val progress = ((safeHeight - MIN_SUPPORTED_HEIGHT) / (NORMAL_REQUIRED_HEIGHT - MIN_SUPPORTED_HEIGHT)).coerceIn(0f, 1f)
    fun scaled(compactValue: Float, normalValue: Float): Float = compactValue + (normalValue - compactValue) * progress

    val verticalPadding = scaled(4f, 8f)
    val handCardHeightByHeight = scaled(80f, 96f)
    val contentWidth = (safeWidth - scaled(20f, 28f)).coerceAtLeast(1f)
    // At least seven cards receive their natural width. More cards retain readable height and scroll horizontally.
    val widthConstrainedCardHeight = contentWidth / min(localHandCount.coerceAtLeast(1), 7) / CARD_WIDTH_TO_HEIGHT
    val handCardHeight = min(handCardHeightByHeight, widthConstrainedCardHeight).coerceAtLeast(MIN_HAND_CARD_HEIGHT)
    val handCardWidth = handCardHeight * CARD_WIDTH_TO_HEIGHT
    val opponentCount = (playerCount - 1).coerceIn(1, 3)
    val desiredOpponentWidth = scaled(150f, 168f)
    val opponentCardWidth = min(desiredOpponentWidth, contentWidth / opponentCount)

    return UnoTableLayoutMetrics(
        availableWidth = availableWidth,
        availableHeight = availableHeight,
        playerCount = playerCount.coerceIn(2, 4),
        localHandCount = localHandCount.coerceAtLeast(0),
        compact = availableHeight < NORMAL_REQUIRED_HEIGHT,
        veryCompact = availableHeight <= VERY_COMPACT_MAX_HEIGHT,
        contentPaddingHorizontal = scaled(10f, 14f),
        contentPaddingVertical = verticalPadding,
        headerHeight = scaled(38f, 43f),
        exitButtonWidth = scaled(68f, 78f),
        opponentAreaHeight = scaled(52f, 82f),
        opponentCardHeight = scaled(48f, 72f),
        opponentCardWidth = opponentCardWidth,
        centerAreaHeight = scaled(70f, 105f),
        pileCardHeight = scaled(58f, 92f),
        pileCardWidth = scaled(58f, 62f),
        actionAreaHeight = 48f,
        handHeaderHeight = scaled(20f, 28f),
        handAreaHeight = scaled(88f, 96f),
        handCardHeight = handCardHeight,
        handCardWidth = handCardWidth,
        verticalGap = scaled(2f, 0f),
        handSpacing = when {
            localHandCount <= 7 -> 5f
            localHandCount <= 15 -> 3f
            else -> 1f
        },
    )
}

private const val MIN_SUPPORTED_HEIGHT = 336f
private const val VERY_COMPACT_MAX_HEIGHT = 344f
private const val NORMAL_REQUIRED_HEIGHT = 418f
private const val MIN_HAND_CARD_HEIGHT = 76f
private const val CARD_WIDTH_TO_HEIGHT = 62f / 96f
