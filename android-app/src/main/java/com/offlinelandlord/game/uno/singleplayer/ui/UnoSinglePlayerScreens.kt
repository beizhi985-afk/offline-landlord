package com.offlinelandlord.game.uno.singleplayer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.offlinelandlord.game.ui.FreshButton
import com.offlinelandlord.game.ui.FreshOutlineButton
import com.offlinelandlord.game.ui.SoftPanel
import com.offlinelandlord.game.ui.theme.Ink
import com.offlinelandlord.game.ui.theme.Lavender
import com.offlinelandlord.game.ui.theme.LavenderDeep
import com.offlinelandlord.game.ui.theme.Mint
import com.offlinelandlord.game.ui.theme.MintDeep
import com.offlinelandlord.game.ui.theme.MutedInk
import com.offlinelandlord.game.ui.theme.Peach
import com.offlinelandlord.game.ui.theme.PeachDeep
import com.offlinelandlord.game.ui.theme.Sunny
import com.offlinelandlord.game.uno.core.UnoColor
import com.offlinelandlord.game.uno.core.UnoDirection
import com.offlinelandlord.game.uno.core.UnoMatchMode
import com.offlinelandlord.game.uno.core.UnoPhase
import com.offlinelandlord.game.uno.ui.UnoBackground
import com.offlinelandlord.game.uno.singleplayer.UnoGameViewModel
import com.offlinelandlord.game.uno.singleplayer.UnoSinglePlayerConfig
import com.offlinelandlord.game.uno.singleplayer.UnoUiPlayer
import com.offlinelandlord.game.uno.singleplayer.UnoUiState
import kotlinx.coroutines.delay

@Composable
fun UnoHomeScreen(
    onSinglePlayer: () -> Unit,
    onBackToGameSelection: () -> Unit,
) {
    UnoBackground(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        BoxWithConstraints(
            modifier = Modifier
                .align(Alignment.Center)
                .width(580.dp)
                .fillMaxHeight(0.94f)
                .heightIn(max = 350.dp),
        ) {
            val compact = maxHeight < 320.dp
            SoftPanel(Modifier.fillMaxSize(), tint = Color(0xF2FFFFFF)) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(start = 30.dp, top = if (compact) 10.dp else 20.dp, end = 30.dp, bottom = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("UNO", color = PeachDeep, fontSize = 42.sp, fontWeight = FontWeight.Black)
                    Text("轻松单机牌局", color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Black)
                    Text("1名真人 · 1～3名NORMAL机器人", color = MutedInk, fontSize = 13.sp)
                    Spacer(Modifier.height(if (compact) 8.dp else 10.dp))
                    FreshButton("单机游戏", onSinglePlayer, Modifier.width(210.dp).height(48.dp))
                    Spacer(Modifier.height(if (compact) 4.dp else 6.dp))
                    FreshOutlineButton(
                        text = "局域网游戏 · 敬请期待",
                        onClick = {},
                        enabled = false,
                        modifier = Modifier.width(210.dp).height(45.dp),
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onBackToGameSelection) { Text("返回游戏选择", color = LavenderDeep) }
                }
            }
        }
    }
}

@Composable
fun UnoSinglePlayerSetupScreen(
    initialPlayerCount: Int,
    initialMatchMode: UnoMatchMode,
    onStartGame: (UnoSinglePlayerConfig) -> Unit,
    onBack: () -> Unit,
) {
    var playerCount by rememberSaveable { mutableIntStateOf(initialPlayerCount) }
    var matchModeName by rememberSaveable { mutableStateOf(initialMatchMode.name) }
    val matchMode = UnoMatchMode.valueOf(matchModeName)
    BackHandler(onBack = onBack)
    UnoBackground(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        SoftPanel(
            Modifier.align(Alignment.Center).width(650.dp).height(360.dp),
            tint = Color(0xF3FFFFFF),
        ) {
            Column(Modifier.fillMaxSize().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("UNO 单机配置", color = Ink, fontSize = 28.sp, fontWeight = FontWeight.Black)
                Text("固定1名真人，其余座位由NORMAL机器人加入", color = MutedInk, fontSize = 12.sp)
                Spacer(Modifier.height(20.dp))
                Text("总玩家人数", color = LavenderDeep, fontWeight = FontWeight.Black)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    (2..4).forEach { count ->
                        UnoChoiceChip("${count}人", playerCount == count) { playerCount = count }
                    }
                }
                Spacer(Modifier.height(18.dp))
                Text("比赛模式", color = LavenderDeep, fontWeight = FontWeight.Black)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    UnoChoiceChip("快速游戏 · 一局决胜", matchMode == UnoMatchMode.QUICK) {
                        matchModeName = UnoMatchMode.QUICK.name
                    }
                    UnoChoiceChip("积分赛 · 500分", matchMode == UnoMatchMode.POINTS) {
                        matchModeName = UnoMatchMode.POINTS.name
                    }
                }
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    FreshOutlineButton("返回", onBack, Modifier.width(130.dp).height(45.dp))
                    FreshButton(
                        "开始游戏",
                        { onStartGame(UnoSinglePlayerConfig(playerCount, matchMode)) },
                        Modifier.width(170.dp).height(45.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun UnoChoiceChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(42.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) Lavender.copy(alpha = 0.52f) else Color.White.copy(alpha = 0.68f))
            .border(1.5.dp, if (selected) LavenderDeep else Lavender.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (selected) LavenderDeep else MutedInk, fontWeight = FontWeight.Black)
    }
}

@Composable
fun UnoGameScreen(
    viewModel: UnoGameViewModel,
    onReturnToUnoHome: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showExitConfirmation by rememberSaveable { mutableStateOf(false) }
    BackHandler { showExitConfirmation = true }

    LaunchedEffect(state.eventMessage) {
        if (state.eventMessage != null) {
            delay(1_600)
            viewModel.dismissEvent()
        }
    }

    UnoBackground(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        if (!state.gameStarted) {
            SoftPanel(Modifier.align(Alignment.Center).width(260.dp).height(100.dp)) {
                Text("正在准备UNO牌局…", Modifier.align(Alignment.Center), color = Ink, fontWeight = FontWeight.Black)
            }
        } else {
            UnoTable(
                state = state,
                viewModel = viewModel,
                onRequestExit = { showExitConfirmation = true },
                onReturnToUnoHome = onReturnToUnoHome,
            )
        }

        state.eventMessage?.let { message ->
            Box(
                Modifier.align(Alignment.TopCenter).padding(top = 54.dp).clip(RoundedCornerShape(18.dp))
                    .background(Color(0xEFFFFFFF)).border(1.5.dp, Peach, RoundedCornerShape(18.dp))
                    .padding(horizontal = 18.dp, vertical = 8.dp),
            ) { Text(message, color = PeachDeep, fontWeight = FontWeight.Black) }
        }

        if (state.mustChooseColor) {
            AlertDialog(
                onDismissRequest = {},
                title = { Text("请选择颜色", fontWeight = FontWeight.Black) },
                text = {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        UnoColor.entries.forEach { color ->
                            Box(
                                Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(color.toCardColor())
                                    .clickable(enabled = !state.isActionInProgress) { viewModel.chooseColor(color) },
                                contentAlignment = Alignment.Center,
                            ) { Text(color.displayName(), color = Color.White, fontWeight = FontWeight.Black) }
                        }
                    }
                },
                confirmButton = {},
                shape = RoundedCornerShape(26.dp),
                containerColor = Color(0xFFFFFBF8),
            )
        }

        state.errorMessage?.let { message ->
            AlertDialog(
                onDismissRequest = viewModel::dismissError,
                title = { Text("小提示") },
                text = { Text(message) },
                confirmButton = { TextButton(onClick = viewModel::dismissError) { Text("知道啦") } },
                shape = RoundedCornerShape(26.dp),
                containerColor = Color(0xFFFFFBF8),
            )
        }

        if (showExitConfirmation) {
            AlertDialog(
                onDismissRequest = { showExitConfirmation = false },
                title = { Text("退出当前UNO牌局？", fontWeight = FontWeight.Black) },
                text = { Text("本局单机进度不会保存。") },
                dismissButton = { TextButton(onClick = { showExitConfirmation = false }) { Text("继续游戏") } },
                confirmButton = { TextButton(onClick = onReturnToUnoHome) { Text("退出", color = PeachDeep) } },
                shape = RoundedCornerShape(26.dp),
                containerColor = Color(0xFFFFFBF8),
            )
        }
    }
}

@Composable
private fun UnoTable(
    state: UnoUiState,
    viewModel: UnoGameViewModel,
    onRequestExit: () -> Unit,
    onReturnToUnoHome: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 8.dp)) {
            UnoTopBar(state, onRequestExit)
            Row(
                Modifier.fillMaxWidth().height(82.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                state.opponents.forEach { UnoOpponentSeat(it) }
            }
            Row(
                Modifier.fillMaxWidth().height(105.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    UnoCardBack(
                        Modifier.width(58.dp).height(86.dp).clickable(enabled = state.canDraw) { viewModel.drawCard() },
                    )
                    Text("牌堆 ${state.drawPileCount}", color = Ink, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(22.dp))
                state.topDiscardCard?.let {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        UnoCardView(it, enabled = false, onClick = {}, modifier = Modifier.width(62.dp).height(92.dp))
                        Text("弃牌顶牌", color = Ink, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            UnoActionBar(state, viewModel)
            Row(
                Modifier.fillMaxWidth().height(28.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val human = state.players.firstOrNull { it.isHuman }
                Text("你${if (human?.isRoundWinner == true || human?.isMatchWinner == true) " 🏆" else ""} · ${state.humanHand.size}张", color = Ink, fontWeight = FontWeight.Black)
                Spacer(Modifier.weight(1f))
                Text("点击亮起的合法牌出牌", color = MutedInk, fontSize = 10.sp)
            }
            LazyRow(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.Bottom,
            ) {
                items(state.humanHand, key = { it.cardId }) { card ->
                    val enabled = card.cardId in state.legalCardIds && !state.isActionInProgress
                    UnoCardView(
                        card = card,
                        enabled = enabled,
                        onClick = { viewModel.playCard(card.cardId) },
                        modifier = Modifier.width(62.dp).height(96.dp),
                    )
                }
            }
        }

        if (state.phase == UnoPhase.ROUND_FINISHED || state.phase == UnoPhase.MATCH_FINISHED) {
            UnoResultPanel(state, viewModel, onReturnToUnoHome, Modifier.align(Alignment.Center))
        }
    }
}

@Composable
private fun UnoTopBar(state: UnoUiState, onRequestExit: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(43.dp), verticalAlignment = Alignment.CenterVertically) {
        FreshOutlineButton("退出", onRequestExit, Modifier.width(78.dp).height(38.dp), color = LavenderDeep)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                when {
                    state.phase == UnoPhase.MATCH_FINISHED -> "整场结束"
                    state.phase == UnoPhase.ROUND_FINISHED -> "本局结束"
                    state.isBotThinking -> "${state.currentPlayerName ?: "机器人"}思考中…"
                    state.isHumanTurn -> "轮到你"
                    else -> "${state.currentPlayerName ?: "对手"}行动"
                },
                color = Ink,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
            )
            Text(
                "第${state.roundNumber}局 · ${if (state.config.matchMode == UnoMatchMode.QUICK) "快速游戏" else "500分积分赛"}",
                color = MutedInk,
                fontSize = 10.sp,
            )
        }
        Spacer(Modifier.weight(1f))
        Box(
            Modifier.clip(RoundedCornerShape(16.dp)).background(state.activeColor.toCardColor().copy(alpha = 0.88f))
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) { Text("当前颜色：${state.activeColor.displayName()}", color = Color.White, fontWeight = FontWeight.Black) }
        Spacer(Modifier.width(8.dp))
        Text(
            if (state.direction == UnoDirection.CLOCKWISE) "↻ 顺时针" else "↺ 逆时针",
            Modifier.clip(RoundedCornerShape(16.dp)).background(Color(0xDFFFFFFF)).padding(horizontal = 12.dp, vertical = 6.dp),
            color = LavenderDeep,
            fontWeight = FontWeight.Black,
        )
    }
}

@Composable
private fun UnoOpponentSeat(player: UnoUiPlayer) {
    Row(
        Modifier
            .width(168.dp)
            .height(72.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (player.isCurrentPlayer) Sunny.copy(alpha = 0.48f) else Color(0xDFFFFFFF))
            .border(if (player.isCurrentPlayer) 2.dp else 1.dp, if (player.isCurrentPlayer) PeachDeep else Color.White, RoundedCornerShape(20.dp))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UnoCardBack(Modifier.width(38.dp).height(54.dp))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "${if (player.isRoundWinner || player.isMatchWinner) "🏆 " else ""}${player.name}",
                color = Ink,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text("背面牌 × ${player.remainingCardCount}", color = LavenderDeep, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text("累计 ${player.score}分", color = MutedInk, fontSize = 9.sp)
        }
    }
}

@Composable
private fun UnoActionBar(state: UnoUiState, viewModel: UnoGameViewModel) {
    Row(
        Modifier.fillMaxWidth().height(46.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.canCatchUno) FreshButton("抓 UNO！", viewModel::catchUno, Modifier.width(112.dp).fillMaxHeight(), color = LavenderDeep)
        if (state.canDeclareUno) FreshButton("UNO！", viewModel::declareUno, Modifier.width(96.dp).fillMaxHeight(), color = PeachDeep)
        if (state.canDraw) FreshButton("摸一张", viewModel::drawCard, Modifier.width(100.dp).fillMaxHeight(), color = MintDeep)
        if (state.phase == UnoPhase.AFTER_DRAW && state.legalCardIds.isNotEmpty() && !state.isActionInProgress) {
            FreshButton(
                "打出刚摸的牌",
                { viewModel.playCard(state.legalCardIds.single()) },
                Modifier.width(142.dp).fillMaxHeight(),
            )
        }
        if (state.canPassAfterDraw) FreshOutlineButton("不出", viewModel::passAfterDraw, Modifier.width(92.dp).fillMaxHeight())
        if (state.isBotThinking) Text("机器人正在思考…", color = LavenderDeep, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun UnoResultPanel(
    state: UnoUiState,
    viewModel: UnoGameViewModel,
    onReturnToUnoHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val matchFinished = state.phase == UnoPhase.MATCH_FINISHED
    val winnerId = if (matchFinished) state.matchWinnerId else state.roundWinnerId
    val winnerName = state.players.firstOrNull { it.playerId == winnerId }?.name ?: "未知玩家"
    SoftPanel(modifier.width(560.dp).height(330.dp), tint = Color(0xFAFFFFFF)) {
        Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (matchFinished && state.config.matchMode == UnoMatchMode.POINTS) "UNO 整场结束" else "UNO 本局结束",
                color = PeachDeep,
                fontSize = 25.sp,
                fontWeight = FontWeight.Black,
            )
            Text("$winnerName 获胜 · 本局 ${state.lastRoundScore}分", color = Ink, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Text("累计排行榜", color = LavenderDeep, fontWeight = FontWeight.Black)
            state.ranking.forEachIndexed { index, player ->
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
                FreshOutlineButton("返回UNO首页", onReturnToUnoHome, Modifier.width(150.dp).height(43.dp))
                if (matchFinished) {
                    FreshButton(
                        if (state.config.matchMode == UnoMatchMode.QUICK) "再来一局" else "再来一场",
                        viewModel::restartMatch,
                        Modifier.width(145.dp).height(43.dp),
                    )
                } else {
                    FreshButton("下一局", viewModel::startNextRound, Modifier.width(145.dp).height(43.dp))
                }
            }
        }
    }
}
