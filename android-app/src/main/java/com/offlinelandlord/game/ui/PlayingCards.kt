package com.offlinelandlord.game.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.offlinelandlord.game.core.Card
import com.offlinelandlord.game.core.Rank
import com.offlinelandlord.game.core.Suit
import com.offlinelandlord.game.core.cardComparator
import com.offlinelandlord.game.ui.theme.Ink
import com.offlinelandlord.game.ui.theme.Lavender
import com.offlinelandlord.game.ui.theme.LavenderDeep
import com.offlinelandlord.game.ui.theme.MintDeep
import com.offlinelandlord.game.ui.theme.Peach
import com.offlinelandlord.game.ui.theme.RoseRed

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun CardHand(
    cards: List<Card>,
    selectedIds: Set<String>,
    enabled: Boolean,
    onSelectionChange: (Set<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val orderedCards = remember(cards) { cards.sortedWith(cardComparator.reversed()) }
    val latestSelectedIds by rememberUpdatedState(selectedIds)
    val latestOnSelectionChange by rememberUpdatedState(onSelectionChange)
    BoxWithConstraints(modifier = modifier.height(108.dp), contentAlignment = Alignment.BottomCenter) {
        val cardWidth = 62.dp
        val spacing = when {
            orderedCards.size <= 1 -> 0.dp
            else -> minOf(39.dp, (maxWidth - cardWidth) / (orderedCards.size - 1)).coerceAtLeast(14.dp)
        }
        val handWidth = if (orderedCards.isEmpty()) 0.dp else cardWidth + spacing * (orderedCards.size - 1)
        val density = LocalDensity.current
        val spacingPx = with(density) { spacing.toPx() }
        val cardIds = remember(orderedCards) { orderedCards.map(Card::id) }
        var previewIds by remember(orderedCards, enabled) { mutableStateOf(emptySet<String>()) }
        var previewMode by remember(orderedCards, enabled) { mutableStateOf<HandGestureMode?>(null) }

        Box(
            Modifier
                .width(handWidth)
                .height(108.dp)
                .pointerInput(enabled, cardIds, spacingPx) {
                    if (!enabled || cardIds.isEmpty()) return@pointerInput
                    detectTapGestures(
                        onTap = { position ->
                            val gesture = HandSelectionGesture(dragThresholdPx = Float.MAX_VALUE)
                            if (gesture.start(position.x, cardIds, spacingPx, latestSelectedIds)) {
                                latestOnSelectionChange(gesture.finish())
                            }
                        }
                    )
                }
                .pointerInput(enabled, cardIds, spacingPx) {
                    if (!enabled || cardIds.isEmpty()) return@pointerInput
                    var activeGesture: HandSelectionGesture? = null
                    fun clearPreview() {
                        previewIds = emptySet()
                        previewMode = null
                    }
                    fun syncPreview(gesture: HandSelectionGesture) {
                        previewIds = gesture.previewIds
                        previewMode = gesture.mode
                    }
                    detectDragGestures(
                        orientationLock = Orientation.Horizontal,
                        onDragStart = { down, slopTriggerChange, _ ->
                            // Compose applies Android's density-aware touch slop
                            // before this callback, so small movement remains a tap.
                            val gesture = HandSelectionGesture(dragThresholdPx = 0f)
                            activeGesture = if (gesture.start(down.position.x, cardIds, spacingPx, latestSelectedIds)) {
                                gesture.move(slopTriggerChange.position.x)
                                syncPreview(gesture)
                                gesture
                            } else {
                                null
                            }
                        },
                        onDrag = { change, _ ->
                            activeGesture?.let { gesture ->
                                gesture.move(change.position.x)
                                syncPreview(gesture)
                            }
                            // Consuming the recognized drag prevents the parallel
                            // tap detector (and parent UI) from handling pointer-up.
                            change.consume()
                        },
                        onDragEnd = { _ ->
                            activeGesture?.let { latestOnSelectionChange(it.finish()) }
                            activeGesture = null
                            clearPreview()
                        },
                        onDragCancel = {
                            activeGesture?.cancel()
                            activeGesture = null
                            clearPreview()
                        },
                    )
                },
        ) {
            orderedCards.forEachIndexed { index, card ->
                val previewed = card.id in previewIds
                val visuallySelected = when {
                    !previewed -> card.id in selectedIds
                    previewMode == HandGestureMode.SELECT -> true
                    else -> false
                }
                PlayingCardFace(
                    card = card,
                    selected = visuallySelected,
                    previewed = previewed,
                    modifier = Modifier
                        .offset(x = spacing * index)
                        .zIndex(index.toFloat()),
                )
            }
        }
    }
}

@Composable
fun PlayingCardFace(
    card: Card,
    selected: Boolean,
    previewed: Boolean,
    modifier: Modifier = Modifier,
) {
    val isRed = card.suit == Suit.HEARTS || card.suit == Suit.DIAMONDS || card.rank == Rank.BIG_JOKER
    val ink = if (isRed) RoseRed else Color(0xFF2A2B35)
    val shape = RoundedCornerShape(10.dp)

    Box(
        modifier = modifier
            .offset(y = if (selected) (-11).dp else 0.dp)
            .size(width = 62.dp, height = 92.dp)
            .shadow(if (selected || previewed) 9.dp else 5.dp, shape, ambientColor = LavenderDeep.copy(alpha = 0.28f))
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFFFFFFFF), Color(0xFFFFFCF7)),
                ),
            )
            .border(
                width = if (selected || previewed) 2.5.dp else 1.dp,
                color = when {
                    previewed -> MintDeep
                    selected -> Peach
                    else -> Color(0xFFE7E1E8)
                },
                shape = shape,
            )
            .padding(5.dp),
    ) {
        if (card.suit == Suit.JOKER) {
            Text(
                text = "J\nO\nK\nE\nR",
                color = ink,
                fontSize = 9.sp,
                lineHeight = 9.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Serif,
                modifier = Modifier.align(Alignment.TopStart),
            )
            Text(
                text = if (card.rank == Rank.BIG_JOKER) "★" else "☆",
                color = ink,
                fontSize = 18.sp,
                modifier = Modifier.align(Alignment.BottomEnd),
            )
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = card.rank.label,
                    color = ink,
                    fontSize = if (card.rank == Rank.TEN) 17.sp else 20.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Serif,
                )
                Text(
                    text = card.suit.symbol,
                    color = ink,
                    fontSize = 15.sp,
                    lineHeight = 14.sp,
                )
            }
            Text(
                text = card.suit.symbol,
                color = ink.copy(alpha = 0.94f),
                fontSize = 31.sp,
                modifier = Modifier.align(Alignment.BottomEnd),
            )
        }
    }
}

@Composable
fun MiniPlayingCard(card: Card, modifier: Modifier = Modifier) {
    val isRed = card.suit == Suit.HEARTS || card.suit == Suit.DIAMONDS || card.rank == Rank.BIG_JOKER
    val ink = if (isRed) RoseRed else Ink
    Box(
        modifier = modifier
            .size(width = 34.dp, height = 48.dp)
            .shadow(3.dp, RoundedCornerShape(6.dp))
            .clip(RoundedCornerShape(6.dp))
            .background(Color.White)
            .border(1.dp, Color(0xFFE8E3EA), RoundedCornerShape(6.dp))
            .padding(3.dp),
    ) {
        if (card.suit == Suit.JOKER) {
            Text(
                text = "J\nO\nK\nE\nR",
                color = ink,
                fontSize = 6.5.sp,
                lineHeight = 6.7.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Serif,
                modifier = Modifier.align(Alignment.TopStart),
            )
        } else {
            Text(
                text = card.rank.label,
                color = ink,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Serif,
            )
            Text(card.suit.symbol, color = ink, fontSize = 17.sp, modifier = Modifier.align(Alignment.BottomEnd))
        }
    }
}

@Composable
fun PlayedCardGroup(
    cards: List<Card>,
    modifier: Modifier = Modifier,
    maxWidth: Dp = 142.dp,
) {
    BoxWithConstraints(
        modifier = modifier.width(maxWidth).height(48.dp),
        contentAlignment = Alignment.Center,
    ) {
        val cardWidth = 34.dp
        val spacing = when {
            cards.size <= 1 -> 0.dp
            else -> minOf(27.dp, (this.maxWidth - cardWidth) / (cards.size - 1)).coerceAtLeast(5.dp)
        }
        val groupWidth = if (cards.isEmpty()) 0.dp else cardWidth + spacing * (cards.size - 1)
        Box(Modifier.width(groupWidth).height(48.dp)) {
            cards.forEachIndexed { index, card ->
                MiniPlayingCard(
                    card = card,
                    modifier = Modifier
                        .offset(x = spacing * index)
                        .zIndex(index.toFloat()),
                )
            }
        }
    }
}

@Composable
fun CardBackStack(count: Int, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(34.dp).height(46.dp)) {
            repeat(3) { index ->
                Box(
                    Modifier
                        .offset(x = (index * 3).dp, y = (-index * 2).dp)
                        .size(width = 27.dp, height = 40.dp)
                        .shadow(2.dp, RoundedCornerShape(6.dp))
                        .clip(RoundedCornerShape(6.dp))
                        .background(Brush.linearGradient(listOf(LavenderDeep, Lavender)))
                        .border(1.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(6.dp))
                        .padding(4.dp),
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .border(1.dp, Color.White.copy(alpha = 0.65f), RoundedCornerShape(3.dp)),
                    )
                }
            }
        }
        Spacer(Modifier.width(5.dp))
        Text("$count", color = Ink, fontWeight = FontWeight.Black, fontSize = 22.sp)
    }
}

private operator fun Dp.times(value: Int): Dp = (this.value * value).dp
