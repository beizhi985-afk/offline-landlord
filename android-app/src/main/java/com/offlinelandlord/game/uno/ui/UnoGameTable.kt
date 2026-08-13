package com.offlinelandlord.game.uno.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.offlinelandlord.game.ui.FreshButton
import com.offlinelandlord.game.ui.FreshOutlineButton
import com.offlinelandlord.game.ui.SoftPanel
import com.offlinelandlord.game.ui.theme.Ink
import com.offlinelandlord.game.ui.theme.Lavender
import com.offlinelandlord.game.ui.theme.LavenderDeep
import com.offlinelandlord.game.ui.theme.MintDeep
import com.offlinelandlord.game.ui.theme.MutedInk
import com.offlinelandlord.game.ui.theme.Peach
import com.offlinelandlord.game.ui.theme.PeachDeep
import com.offlinelandlord.game.ui.theme.Sunny

enum class UnoTablePhase { WAITING, TURN, AFTER_DRAW, CHOOSE_COLOR, ROUND_FINISHED, MATCH_FINISHED }

data class UnoTablePlayer(
    val playerId: String,
    val name: String,
    val role: String,
    val remainingCardCount: Int,
    val score: Int,
    val isCurrentPlayer: Boolean,
    val isRoundWinner: Boolean = false,
    val isMatchWinner: Boolean = false,
)

data class UnoTableRanking(val name: String, val score: Int)

data class UnoTableResult(
    val matchFinished: Boolean,
    val title: String,
    val winnerLine: String,
    val ranking: List<UnoTableRanking>,
    val nextLabel: String?,
)

data class UnoTablePresentationState(
    val phase: UnoTablePhase,
    val turnText: String,
    val roundAndModeText: String,
    val activeColor: UnoTableColor,
    val activeColorName: String,
    val clockwise: Boolean,
    val opponents: List<UnoTablePlayer>,
    val hand: List<UnoTableCard>,
    val legalCardIds: Set<String>,
    val drawPileCount: Int,
    val topDiscard: UnoTableCard?,
    val canDraw: Boolean,
    val canPlayDrawnCard: Boolean,
    val drawnCardId: String?,
    val canPassAfterDraw: Boolean,
    val canDeclareUno: Boolean,
    val canCatchUno: Boolean,
    val isActionInProgress: Boolean,
    val isBotThinking: Boolean,
    val mustChooseColor: Boolean,
    val localPlayerLabel: String = "你",
    val localPlayerWon: Boolean = false,
    val connectionBadge: String? = null,
    val roomBadge: String? = null,
    val eventMessage: String? = null,
    val result: UnoTableResult? = null,
)

data class UnoTableCallbacks(
    val onExit: () -> Unit,
    val onPlayCard: (String) -> Unit,
    val onDrawCard: () -> Unit,
    val onPlayDrawnCard: (String) -> Unit,
    val onPassAfterDraw: () -> Unit,
    val onDeclareUno: () -> Unit,
    val onCatchUno: () -> Unit,
    val onChooseColor: (UnoTableColor) -> Unit,
    val onNext: () -> Unit,
    val onReturnHome: () -> Unit,
)

/** The single visual implementation used by both single-player and LAN UNO. */
@Composable
fun UnoGameTableContent(
    state: UnoTablePresentationState,
    callbacks: UnoTableCallbacks,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 8.dp)) {
            UnoSharedTopBar(state, callbacks.onExit)
            Row(
                Modifier.fillMaxWidth().height(82.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                state.opponents.forEach { player ->
                    UnoTableOpponentCard(
                        name = "${if (player.isRoundWinner || player.isMatchWinner) "🏆 " else ""}${player.name}",
                        role = player.role,
                        remainingCardCount = player.remainingCardCount,
                        isCurrentPlayer = player.isCurrentPlayer,
                        score = player.score,
                        modifier = Modifier.width(168.dp),
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth().height(105.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    UnoTableCardBack(
                        Modifier.width(58.dp).height(86.dp).clickable(enabled = state.canDraw) { callbacks.onDrawCard() },
                    )
                    Text("牌堆 ${state.drawPileCount}", color = Ink, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(22.dp))
                state.topDiscard?.let { card ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        UnoTableCardView(card, enabled = false, onClick = {}, modifier = Modifier.width(62.dp).height(92.dp))
                        Text("弃牌顶牌", color = Ink, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            UnoSharedActionBar(state, callbacks)
            Row(Modifier.fillMaxWidth().height(28.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${state.localPlayerLabel}${if (state.localPlayerWon) " 🏆" else ""} · ${state.hand.size}张",
                    color = Ink,
                    fontWeight = FontWeight.Black,
                )
                Spacer(Modifier.weight(1f))
                Text("点击亮起的合法牌出牌", color = MutedInk, fontSize = 10.sp)
            }
            LazyRow(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.Bottom,
            ) {
                items(state.hand, key = UnoTableCard::cardId) { card ->
                    UnoTableCardView(
                        card = card,
                        enabled = card.cardId in state.legalCardIds && !state.isActionInProgress,
                        onClick = { callbacks.onPlayCard(card.cardId) },
                        modifier = Modifier.width(62.dp).height(96.dp),
                    )
                }
            }
        }

        state.eventMessage?.let { message ->
            Box(
                Modifier.align(Alignment.TopCenter).padding(top = 54.dp).clip(RoundedCornerShape(18.dp))
                    .background(Color(0xEFFFFFFF)).border(1.5.dp, Peach, RoundedCornerShape(18.dp))
                    .padding(horizontal = 18.dp, vertical = 8.dp),
            ) { Text(message, color = PeachDeep, fontWeight = FontWeight.Black) }
        }

        state.result?.let { result -> UnoSharedResultPanel(result, callbacks, Modifier.align(Alignment.Center)) }
    }

    if (state.mustChooseColor) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("请选择颜色", fontWeight = FontWeight.Black) },
            text = {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    listOf(
                        UnoTableColor.RED to "红",
                        UnoTableColor.YELLOW to "黄",
                        UnoTableColor.GREEN to "绿",
                        UnoTableColor.BLUE to "蓝",
                    ).forEach { (color, name) ->
                        Box(
                            Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(color.toDisplayColor())
                                .clickable(enabled = !state.isActionInProgress) { callbacks.onChooseColor(color) },
                            contentAlignment = Alignment.Center,
                        ) { Text(name, color = Color.White, fontWeight = FontWeight.Black) }
                    }
                }
            },
            confirmButton = {},
            shape = RoundedCornerShape(26.dp),
            containerColor = Color(0xFFFFFBF8),
        )
    }
}

@Composable
private fun UnoSharedTopBar(state: UnoTablePresentationState, onExit: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(43.dp), verticalAlignment = Alignment.CenterVertically) {
        FreshOutlineButton("退出", onExit, Modifier.width(78.dp).height(38.dp), color = LavenderDeep)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(state.turnText, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Black)
            Text(state.roundAndModeText, color = MutedInk, fontSize = 10.sp)
        }
        Spacer(Modifier.weight(1f))
        state.roomBadge?.let { badge ->
            Text(badge, color = LavenderDeep, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
        }
        state.connectionBadge?.let { badge ->
            Text(badge, color = MintDeep, fontSize = 10.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.width(8.dp))
        }
        Box(
            Modifier.clip(RoundedCornerShape(16.dp)).background(state.activeColor.toDisplayColor().copy(alpha = 0.88f))
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) { Text("当前颜色：${state.activeColorName}", color = Color.White, fontWeight = FontWeight.Black) }
        Spacer(Modifier.width(8.dp))
        Text(
            if (state.clockwise) "↻ 顺时针" else "↺ 逆时针",
            Modifier.clip(RoundedCornerShape(16.dp)).background(Color(0xDFFFFFFF)).padding(horizontal = 12.dp, vertical = 6.dp),
            color = LavenderDeep,
            fontWeight = FontWeight.Black,
        )
    }
}

@Composable
private fun UnoSharedActionBar(state: UnoTablePresentationState, callbacks: UnoTableCallbacks) {
    Row(
        Modifier.fillMaxWidth().height(46.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.canCatchUno) FreshButton("抓 UNO！", callbacks.onCatchUno, Modifier.width(112.dp).fillMaxHeight(), color = LavenderDeep)
        if (state.canDeclareUno) FreshButton("UNO！", callbacks.onDeclareUno, Modifier.width(96.dp).fillMaxHeight(), color = PeachDeep)
        if (state.canDraw) FreshButton("摸一张", callbacks.onDrawCard, Modifier.width(100.dp).fillMaxHeight(), color = MintDeep)
        if (state.canPlayDrawnCard && state.drawnCardId != null && !state.isActionInProgress) {
            FreshButton("打出刚摸的牌", { callbacks.onPlayDrawnCard(state.drawnCardId) }, Modifier.width(142.dp).fillMaxHeight())
        }
        if (state.canPassAfterDraw) FreshOutlineButton("不出", callbacks.onPassAfterDraw, Modifier.width(92.dp).fillMaxHeight())
        if (state.isBotThinking) Text("机器人正在思考…", color = LavenderDeep, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun UnoSharedResultPanel(result: UnoTableResult, callbacks: UnoTableCallbacks, modifier: Modifier) {
    SoftPanel(modifier.width(560.dp).height(330.dp), tint = Color(0xFAFFFFFF)) {
        Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(result.title, color = PeachDeep, fontSize = 25.sp, fontWeight = FontWeight.Black)
            Text(result.winnerLine, color = Ink, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Text("累计排行榜", color = LavenderDeep, fontWeight = FontWeight.Black)
            result.ranking.forEachIndexed { index, player ->
                Row(
                    Modifier.fillMaxWidth().height(34.dp).padding(vertical = 2.dp).clip(RoundedCornerShape(12.dp))
                        .background(if (index == 0) Sunny.copy(alpha = 0.4f) else Lavender.copy(alpha = 0.18f))
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${index + 1}", color = if (index == 0) PeachDeep else LavenderDeep, fontWeight = FontWeight.Black)
                    Spacer(Modifier.width(12.dp))
                    Text(player.name, Modifier.weight(1f), color = Ink, fontWeight = FontWeight.Bold)
                    Text("${player.score}分", color = MintDeep, fontWeight = FontWeight.Black)
                }
            }
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FreshOutlineButton("返回UNO首页", callbacks.onReturnHome, Modifier.width(150.dp).height(43.dp))
                result.nextLabel?.let { label -> FreshButton(label, callbacks.onNext, Modifier.width(145.dp).height(43.dp)) }
            }
        }
    }
}
