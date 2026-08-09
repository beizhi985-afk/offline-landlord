package com.offlinelandlord.game.app.gameselection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.offlinelandlord.game.ui.FreshOutlineButton
import com.offlinelandlord.game.ui.FreshScenicBackground
import com.offlinelandlord.game.ui.SoftPanel
import com.offlinelandlord.game.ui.theme.Ink
import com.offlinelandlord.game.ui.theme.LavenderDeep
import com.offlinelandlord.game.ui.theme.MutedInk
import com.offlinelandlord.game.ui.theme.PeachDeep

@Composable
fun UnoComingSoonScreen(onBackToGameSelection: () -> Unit) {
    FreshScenicBackground(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        SoftPanel(
            modifier = Modifier
                .align(Alignment.Center)
                .width(520.dp)
                .height(320.dp),
            tint = Color(0xF2FFFFFF),
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(30.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("UNO", color = PeachDeep, fontSize = 42.sp, fontWeight = FontWeight.Black)
                Text("开发中", color = Ink, fontSize = 24.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(16.dp))
                Text("后续将支持", color = MutedInk, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(
                    "• 单机机器人\n• 2～4人局域网联机",
                    color = LavenderDeep,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    lineHeight = 25.sp,
                )
                Spacer(Modifier.height(22.dp))
                FreshOutlineButton(
                    text = "返回游戏选择",
                    onClick = onBackToGameSelection,
                    modifier = Modifier.width(180.dp).height(44.dp),
                )
            }
        }
    }
}
