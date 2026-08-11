package com.offlinelandlord.game.uno.singleplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.offlinelandlord.game.uno.core.UnoCard
import com.offlinelandlord.game.uno.core.UnoCardType
import com.offlinelandlord.game.uno.core.UnoColor

@Composable
fun UnoCardView(
    card: UnoCard,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = card.color.toCardColor()
    val label = card.displayLabel()
    Box(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.48f)
            .clip(RoundedCornerShape(13.dp))
            .background(color)
            .border(3.dp, Color.White.copy(alpha = 0.94f), RoundedCornerShape(13.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(5.dp),
    ) {
        Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black)
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxSize(0.72f)
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.90f)),
            contentAlignment = Alignment.Center,
        ) {
            if (card.type == UnoCardType.WILD || card.type == UnoCardType.WILD_DRAW_FOUR) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        listOf(UnoColor.RED, UnoColor.YELLOW, UnoColor.GREEN, UnoColor.BLUE).forEach {
                            Box(Modifier.size(7.dp).clip(CircleShape).background(it.toCardColor()))
                        }
                    }
                    Text(label, color = Color(0xFF343746), fontSize = 14.sp, fontWeight = FontWeight.Black)
                }
            } else {
                Text(
                    label,
                    color = color,
                    fontSize = if (label.length > 2) 19.sp else 27.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
fun UnoCardBack(modifier: Modifier = Modifier) {
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

fun UnoCard.displayLabel(): String = when (type) {
    UnoCardType.NUMBER -> requireNotNull(number).toString()
    UnoCardType.SKIP -> "⊘"
    UnoCardType.REVERSE -> "↺"
    UnoCardType.DRAW_TWO -> "+2"
    UnoCardType.WILD -> "WILD"
    UnoCardType.WILD_DRAW_FOUR -> "+4"
}

fun UnoColor?.displayName(): String = when (this) {
    UnoColor.RED -> "红"
    UnoColor.YELLOW -> "黄"
    UnoColor.GREEN -> "绿"
    UnoColor.BLUE -> "蓝"
    null -> "待选择"
}

fun UnoColor?.toCardColor(): Color = when (this) {
    UnoColor.RED -> Color(0xFFE95D67)
    UnoColor.YELLOW -> Color(0xFFF2C94C)
    UnoColor.GREEN -> Color(0xFF45A66F)
    UnoColor.BLUE -> Color(0xFF4B86E8)
    null -> Color(0xFF3F4356)
}
