package com.offlinelandlord.game.uno.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.offlinelandlord.game.ui.theme.Ink
import com.offlinelandlord.game.ui.theme.LavenderDeep
import com.offlinelandlord.game.ui.theme.MutedInk
import com.offlinelandlord.game.ui.theme.PeachDeep
import com.offlinelandlord.game.ui.theme.Sunny

/** Shared visual shell for every UNO table. It deliberately contains no game control logic. */
@Composable
fun UnoTableOpponentCard(
    name: String,
    role: String,
    remainingCardCount: Int,
    isCurrentPlayer: Boolean,
    score: Int? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .height(72.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (isCurrentPlayer) Sunny.copy(alpha = 0.48f) else Color(0xDFFFFFFF))
            .border(if (isCurrentPlayer) 2.dp else 1.dp, if (isCurrentPlayer) PeachDeep else Color.White, RoundedCornerShape(20.dp))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UnoTableCardBack(Modifier.width(38.dp).fillMaxHeight())
        Spacer(Modifier.width(8.dp))
        Column(Modifier.width(68.dp)) {
            Text(name, color = Ink, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(role, color = LavenderDeep, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text("背面牌 × $remainingCardCount", color = MutedInk, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            score?.let { Text("累计 $it 分", color = MutedInk, fontSize = 9.sp) }
        }
    }
}

@Composable
fun UnoTableCardBack(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(11.dp))
            .background(Color(0xFF4A426F))
            .border(3.dp, Color.White.copy(alpha = 0.92f), RoundedCornerShape(11.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text("UNO", color = Color(0xFFFFD36D), fontSize = 13.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
fun UnoTablePileLabel(label: String, count: Int? = null, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Ink, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        count?.let { Text("$it 张", color = MutedInk, fontSize = 9.sp) }
    }
}

enum class UnoTableColor { RED, YELLOW, GREEN, BLUE, WILD }

data class UnoTableCard(
    val cardId: String,
    val color: UnoTableColor,
    val label: String,
    val isWild: Boolean = false,
)

fun UnoTableColor.toDisplayColor(): Color = when (this) {
    UnoTableColor.RED -> Color(0xFFE95D67)
    UnoTableColor.YELLOW -> Color(0xFFF2C94C)
    UnoTableColor.GREEN -> Color(0xFF45A66F)
    UnoTableColor.BLUE -> Color(0xFF4B86E8)
    UnoTableColor.WILD -> Color(0xFF3F4356)
}

@Composable
fun UnoTableCardView(
    card: UnoTableCard,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = card.color.toDisplayColor()
    Box(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.48f)
            .clip(RoundedCornerShape(13.dp))
            .background(color)
            .border(3.dp, Color.White.copy(alpha = 0.94f), RoundedCornerShape(13.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(5.dp),
    ) {
        Text(card.label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black)
        Box(
            modifier = Modifier.align(Alignment.Center).fillMaxWidth(0.72f).fillMaxHeight(0.72f)
                .clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.90f)),
            contentAlignment = Alignment.Center,
        ) {
            if (card.isWild) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row {
                        listOf(UnoTableColor.RED, UnoTableColor.YELLOW, UnoTableColor.GREEN, UnoTableColor.BLUE).forEach {
                            Box(Modifier.padding(1.dp).width(7.dp).height(7.dp).clip(RoundedCornerShape(50)).background(it.toDisplayColor()))
                        }
                    }
                    Text(card.label, color = Color(0xFF343746), fontSize = 14.sp, fontWeight = FontWeight.Black)
                }
            } else {
                Text(card.label, color = color, fontSize = if (card.label.length > 2) 19.sp else 27.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}
