package com.offlinelandlord.game.uno.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
