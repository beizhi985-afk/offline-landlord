package com.offlinelandlord.game.app.gameselection

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.offlinelandlord.game.shared.GameType
import com.offlinelandlord.game.ui.FreshScenicBackground
import com.offlinelandlord.game.ui.SoftPanel
import com.offlinelandlord.game.ui.theme.Ink
import com.offlinelandlord.game.ui.theme.Lavender
import com.offlinelandlord.game.ui.theme.LavenderDeep
import com.offlinelandlord.game.ui.theme.Mint
import com.offlinelandlord.game.ui.theme.MintDeep
import com.offlinelandlord.game.ui.theme.MutedInk
import com.offlinelandlord.game.ui.theme.Peach
import com.offlinelandlord.game.ui.theme.PeachDeep

@Composable
fun GameSelectionScreen(onGameSelected: (GameType) -> Unit) {
    FreshScenicBackground(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 54.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("离线牌局", color = Ink, fontSize = 36.sp, fontWeight = FontWeight.Black)
            Text("无需互联网 · 同一热点即可联机", color = MutedInk, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(22.dp))
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
            ) {
                GameOptionCard(
                    title = "斗地主",
                    description = "经典三人斗地主",
                    status = "可游玩",
                    symbol = "♠",
                    accent = PeachDeep,
                    tint = Peach,
                    onClick = { onGameSelected(GameType.LANDLORD) },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                GameOptionCard(
                    title = "UNO",
                    description = "2～4人卡牌游戏",
                    status = "开发中",
                    symbol = "UNO",
                    accent = LavenderDeep,
                    tint = Lavender,
                    onClick = { onGameSelected(GameType.UNO) },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }
    }
}

@Composable
private fun GameOptionCard(
    title: String,
    description: String,
    status: String,
    symbol: String,
    accent: Color,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SoftPanel(modifier = modifier, tint = Color(0xEFFFFFFF)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clickable(onClick = onClick)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(82.dp)
                    .clip(RoundedCornerShape(25.dp))
                    .background(tint.copy(alpha = 0.65f))
                    .border(2.dp, accent.copy(alpha = 0.68f), RoundedCornerShape(25.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = symbol,
                    color = accent,
                    fontSize = if (symbol.length > 1) 21.sp else 40.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(title, color = Ink, fontSize = 26.sp, fontWeight = FontWeight.Black)
            Text(description, color = MutedInk, fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .width(112.dp)
                    .height(34.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(if (status == "可游玩") Mint.copy(alpha = 0.55f) else tint.copy(alpha = 0.42f))
                    .border(
                        1.4.dp,
                        if (status == "可游玩") MintDeep.copy(alpha = 0.72f) else accent.copy(alpha = 0.58f),
                        RoundedCornerShape(15.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    status,
                    color = if (status == "可游玩") MintDeep else accent,
                    fontWeight = FontWeight.Black,
                    fontSize = 12.sp,
                )
            }
        }
    }
}
