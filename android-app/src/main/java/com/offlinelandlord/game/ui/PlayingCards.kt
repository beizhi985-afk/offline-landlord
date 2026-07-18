package com.offlinelandlord.game.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import com.offlinelandlord.game.ui.theme.Peach
import com.offlinelandlord.game.ui.theme.RoseRed

@Composable
fun CardHand(
    cards: List<Card>,
    selectedIds: Set<String>,
    enabled: Boolean,
    onToggle: (Card) -> Unit,
    modifier: Modifier = Modifier,
) {
    val orderedCards = remember(cards) { cards.sortedWith(cardComparator.reversed()) }
    BoxWithConstraints(modifier = modifier.height(108.dp), contentAlignment = Alignment.BottomCenter) {
        val cardWidth = 62.dp
        val spacing = when {
            orderedCards.size <= 1 -> 0.dp
            else -> minOf(39.dp, (maxWidth - cardWidth) / (orderedCards.size - 1)).coerceAtLeast(14.dp)
        }
        val handWidth = if (orderedCards.isEmpty()) 0.dp else cardWidth + spacing * (orderedCards.size - 1)
        Box(Modifier.width(handWidth).height(108.dp)) {
            orderedCards.forEachIndexed { index, card ->
                PlayingCardFace(
                    card = card,
                    selected = card.id in selectedIds,
                    enabled = enabled,
                    onClick = { onToggle(card) },
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
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isRed = card.suit == Suit.HEARTS || card.suit == Suit.DIAMONDS || card.rank == Rank.BIG_JOKER
    val ink = if (isRed) RoseRed else Color(0xFF2A2B35)
    val shape = RoundedCornerShape(10.dp)

    Box(
        modifier = modifier
            .offset(y = if (selected) (-11).dp else 0.dp)
            .size(width = 62.dp, height = 92.dp)
            .shadow(if (selected) 9.dp else 5.dp, shape, ambientColor = LavenderDeep.copy(alpha = 0.28f))
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFFFFFFFF), Color(0xFFFFFCF7)),
                ),
            )
            .border(
                width = if (selected) 2.5.dp else 1.dp,
                color = if (selected) Peach else Color(0xFFE7E1E8),
                shape = shape,
            )
            .clickable(enabled = enabled, onClick = onClick)
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
        Text(
            text = if (card.suit == Suit.JOKER) if (card.rank == Rank.BIG_JOKER) "大" else "小" else card.rank.label,
            color = ink,
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Serif,
        )
        if (card.suit != Suit.JOKER) {
            Text(card.suit.symbol, color = ink, fontSize = 17.sp, modifier = Modifier.align(Alignment.BottomEnd))
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
