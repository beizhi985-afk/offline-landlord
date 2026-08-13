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
import com.offlinelandlord.game.uno.core.UnoCard
import com.offlinelandlord.game.uno.core.UnoDirection
import com.offlinelandlord.game.uno.core.UnoMatchMode
import com.offlinelandlord.game.uno.core.UnoPhase
import com.offlinelandlord.game.uno.ui.*
import com.offlinelandlord.game.uno.singleplayer.UnoGameViewModel
import com.offlinelandlord.game.uno.singleplayer.UnoSinglePlayerConfig
import com.offlinelandlord.game.uno.singleplayer.UnoUiPlayer
import com.offlinelandlord.game.uno.singleplayer.UnoUiState
import kotlinx.coroutines.delay

@Composable
fun UnoHomeScreen(
    onSinglePlayer: () -> Unit,
    onLanPlayer: () -> Unit,
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
                    Text("1名真人 · 1～3名普通机器人", color = MutedInk, fontSize = 13.sp)
                    Spacer(Modifier.height(if (compact) 8.dp else 10.dp))
                    FreshButton("单机游戏", onSinglePlayer, Modifier.width(210.dp).height(48.dp))
                    Spacer(Modifier.height(if (compact) 4.dp else 6.dp))
                    FreshOutlineButton(
                        text = "局域网游戏",
                        onClick = onLanPlayer,
                        enabled = true,
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
                Text("固定1名真人，其余座位由普通机器人加入", color = MutedInk, fontSize = 12.sp)
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
            UnoGameTableContent(
                state = state.toTablePresentation(),
                callbacks = UnoTableCallbacks(
                    onExit = { showExitConfirmation = true },
                    onPlayCard = viewModel::playCard,
                    onDrawCard = viewModel::drawCard,
                    onPlayDrawnCard = viewModel::playCard,
                    onPassAfterDraw = viewModel::passAfterDraw,
                    onDeclareUno = viewModel::declareUno,
                    onCatchUno = viewModel::catchUno,
                    onChooseColor = { color -> viewModel.chooseColor(color.toCoreColor()) },
                    onNext = { if (state.phase == UnoPhase.MATCH_FINISHED) viewModel.restartMatch() else viewModel.startNextRound() },
                    onReturnHome = onReturnToUnoHome,
                ),
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

private fun UnoUiState.toTablePresentation(): UnoTablePresentationState {
    val human = players.firstOrNull(UnoUiPlayer::isHuman)
    val matchFinished = phase == UnoPhase.MATCH_FINISHED
    val roundFinished = phase == UnoPhase.ROUND_FINISHED
    val winnerId = if (matchFinished) matchWinnerId else roundWinnerId
    val winnerName = players.firstOrNull { it.playerId == winnerId }?.name ?: "未知玩家"
    return UnoTablePresentationState(
        phase = when (phase) {
            UnoPhase.AFTER_DRAW -> UnoTablePhase.AFTER_DRAW
            UnoPhase.CHOOSE_COLOR -> UnoTablePhase.CHOOSE_COLOR
            UnoPhase.ROUND_FINISHED -> UnoTablePhase.ROUND_FINISHED
            UnoPhase.MATCH_FINISHED -> UnoTablePhase.MATCH_FINISHED
            UnoPhase.TURN -> UnoTablePhase.TURN
            else -> UnoTablePhase.WAITING
        },
        turnText = when {
            matchFinished -> "整场结束"
            roundFinished -> "本局结束"
            isBotThinking -> "${currentPlayerName ?: "机器人"}思考中…"
            isHumanTurn -> "轮到你"
            else -> "${currentPlayerName ?: "对手"}行动"
        },
        roundAndModeText = "第${roundNumber}局 · ${if (config.matchMode == UnoMatchMode.QUICK) "快速游戏" else "500分积分赛"}",
        activeColor = activeColor.toTableColor(),
        activeColorName = activeColor.displayName(),
        clockwise = direction == UnoDirection.CLOCKWISE,
        opponents = opponents.map { player ->
            UnoTablePlayer(player.playerId, player.name, "机器人", player.remainingCardCount, player.score, player.isCurrentPlayer, player.isRoundWinner, player.isMatchWinner)
        },
        hand = humanHand.map(UnoCard::toTableCard),
        legalCardIds = legalCardIds,
        drawPileCount = drawPileCount,
        topDiscard = topDiscardCard?.toTableCard(),
        canDraw = canDraw,
        canPlayDrawnCard = phase == UnoPhase.AFTER_DRAW && legalCardIds.isNotEmpty(),
        drawnCardId = legalCardIds.singleOrNull(),
        canPassAfterDraw = canPassAfterDraw,
        canDeclareUno = canDeclareUno,
        canCatchUno = canCatchUno,
        isActionInProgress = isActionInProgress,
        isBotThinking = isBotThinking,
        mustChooseColor = mustChooseColor,
        localPlayerWon = human?.isRoundWinner == true || human?.isMatchWinner == true,
        eventMessage = eventMessage,
        result = if (roundFinished || matchFinished) UnoTableResult(
            matchFinished = matchFinished,
            title = if (matchFinished && config.matchMode == UnoMatchMode.POINTS) "UNO 整场结束" else "UNO 本局结束",
            winnerLine = "$winnerName 获胜 · 本局 ${lastRoundScore}分",
            ranking = ranking.map { UnoTableRanking(it.name, it.score) },
            nextLabel = if (matchFinished) if (config.matchMode == UnoMatchMode.QUICK) "再来一局" else "再来一场" else "下一局",
        ) else null,
    )
}

private fun UnoColor?.toTableColor(): UnoTableColor = when (this) {
    UnoColor.RED -> UnoTableColor.RED
    UnoColor.YELLOW -> UnoTableColor.YELLOW
    UnoColor.GREEN -> UnoTableColor.GREEN
    UnoColor.BLUE -> UnoTableColor.BLUE
    null -> UnoTableColor.WILD
}

private fun UnoTableColor.toCoreColor(): UnoColor = when (this) {
    UnoTableColor.RED -> UnoColor.RED
    UnoTableColor.YELLOW -> UnoColor.YELLOW
    UnoTableColor.GREEN -> UnoColor.GREEN
    UnoTableColor.BLUE -> UnoColor.BLUE
    UnoTableColor.WILD -> error("万能色不能作为选择结果")
}
