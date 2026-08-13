package com.offlinelandlord.game.uno.singleplayer.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.offlinelandlord.game.uno.core.UnoCard
import com.offlinelandlord.game.uno.core.UnoCardType
import com.offlinelandlord.game.uno.core.UnoColor
import com.offlinelandlord.game.uno.ui.UnoTableCard
import com.offlinelandlord.game.uno.ui.UnoTableCardView
import com.offlinelandlord.game.uno.ui.UnoTableColor

@Composable
fun UnoCardView(
    card: UnoCard,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    UnoTableCardView(card.toTableCard(), enabled, onClick, modifier)
}

fun UnoCard.toTableCard(): UnoTableCard = UnoTableCard(
    cardId = cardId,
    color = when (color) {
        UnoColor.RED -> UnoTableColor.RED
        UnoColor.YELLOW -> UnoTableColor.YELLOW
        UnoColor.GREEN -> UnoTableColor.GREEN
        UnoColor.BLUE -> UnoTableColor.BLUE
        null -> UnoTableColor.WILD
    },
    label = displayLabel(),
    isWild = type == UnoCardType.WILD || type == UnoCardType.WILD_DRAW_FOUR,
)

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
