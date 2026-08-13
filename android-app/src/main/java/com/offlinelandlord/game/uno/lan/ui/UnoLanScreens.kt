package com.offlinelandlord.game.uno.lan.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.offlinelandlord.game.network.ConnectionState
import com.offlinelandlord.game.network.uno.v5.*
import com.offlinelandlord.game.ui.FreshButton
import com.offlinelandlord.game.ui.FreshOutlineButton
import com.offlinelandlord.game.ui.SoftPanel
import com.offlinelandlord.game.ui.theme.*
import com.offlinelandlord.game.uno.lan.UnoLanViewModel
import com.offlinelandlord.game.uno.ui.*

@Composable
fun UnoLanHomeScreen(viewModel: UnoLanViewModel, onBack: () -> Unit, onLobby: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var name by rememberSaveable { mutableStateOf("玩家") }
    var players by rememberSaveable { mutableIntStateOf(2) }
    var mode by rememberSaveable { mutableStateOf(UnoV5GameMode.QUICK) }
    var host by rememberSaveable { mutableStateOf("") }
    var port by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    BackHandler(onBack = onBack)
    LaunchedEffect(state.room?.roomCode) { if (state.room != null) onLobby() }
    UnoLanBackground {
        BoxWithConstraints(Modifier.fillMaxSize().padding(18.dp)) {
            SoftPanel(Modifier.align(Alignment.Center).fillMaxWidth().heightIn(min = 390.dp, max = 680.dp), tint = Color(0xF3FFFFFF)) {
                LazyColumn(Modifier.fillMaxSize().padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item { Header("UNO LAN", state.connectionState) }
                    item { Label("创建房间") }
                    item { ChoiceRow((2..4).map { "${it}人" }, players - 2) { players = it + 2 } }
                    item { ChoiceRow(listOf("快速游戏", "积分赛 500 分"), if (mode == UnoV5GameMode.QUICK) 0 else 1) { mode = if (it == 0) UnoV5GameMode.QUICK else UnoV5GameMode.POINTS_500 } }
                    item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { OutlinedTextField(name, { name = it.take(16) }, label = { Text("你的昵称") }, singleLine = true, modifier = Modifier.weight(1f)); FreshButton("创建", { viewModel.createRoom(name, players, mode) }, Modifier.width(112.dp).height(48.dp), enabled = !state.isBusy) } }
                    item { Label("手动加入") }
                    item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(host, { host = it.take(45) }, label = { Text("房主地址") }, singleLine = true, modifier = Modifier.weight(1.2f)); OutlinedTextField(port, { port = it.filter(Char::isDigit).take(5) }, label = { Text("端口") }, singleLine = true, modifier = Modifier.weight(.7f)); OutlinedTextField(code, { code = it.filter(Char::isDigit).take(6) }, label = { Text("六位房间码") }, singleLine = true, modifier = Modifier.weight(1f)) } }
                    item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { FreshOutlineButton("寻找附近房间", viewModel::discoverRooms, Modifier.weight(1f), enabled = !state.isDiscovering); FreshButton("加入房间", { viewModel.joinRoom(host, port.toIntOrNull() ?: 0, code, name) }, Modifier.weight(1f), color = LavenderDeep, enabled = !state.isBusy) } }
                    if (state.isDiscovering) item { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) } }
                    if (state.rooms.isNotEmpty()) { item { Label("附近的 UNO 房间") }; items(state.rooms, key = { "${it.host}:${it.port}:${it.roomCode}" }) { room -> RoomRow(room) { viewModel.joinDiscovered(room, name) } } }
                    item { TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("返回 UNO 首页", color = LavenderDeep) } }
                }
            }
        }
    }
    state.errorMessage?.let { ErrorDialog(it, viewModel::dismissError) }
}

@Composable
private fun Header(title: String, connection: ConnectionState) { Row(verticalAlignment = Alignment.CenterVertically) { Text(title, color = PeachDeep, fontSize = 30.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f)); ConnectionPill(connection) } }

@Composable
fun UnoLanLobbyScreen(viewModel: UnoLanViewModel, onBack: () -> Unit, onGame: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmExit by rememberSaveable { mutableStateOf(false) }
    BackHandler { confirmExit = true }
    LaunchedEffect(state.room?.status) { if (state.room?.status == UnoV5RoomStatus.PLAYING) onGame() }
    UnoLanBackground { Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("房间 ${state.room?.roomCode.orEmpty()}", color = Ink, fontSize = 26.sp, fontWeight = FontWeight.Black); Text("${gameModeName(state.room?.gameMode)} · 房主地址 ${state.hostAddress}:${state.hostPort}", color = MutedInk, fontSize = 12.sp) }; ConnectionPill(state.connectionState); Spacer(Modifier.width(8.dp)); FreshOutlineButton("退出", { confirmExit = true }, Modifier.width(90.dp).height(40.dp)) }
        SoftPanel(Modifier.fillMaxWidth().weight(1f), tint = Color(0xEFFFFFFF)) { LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { item { Text("房间玩家", color = LavenderDeep, fontWeight = FontWeight.Black) }; items(state.room?.players.orEmpty(), key = { it.playerId }) { p -> LobbySeat(p, p.playerId == state.room?.hostPlayerId, p.playerId == state.selfId) { if (p.isBot) viewModel.removeBot(p.playerId) } } } }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { if (state.isHost) FreshOutlineButton("+ 添加机器人", viewModel::addBot, Modifier.width(130.dp).height(42.dp), enabled = state.room?.status == UnoV5RoomStatus.WAITING); val self = state.room?.players?.firstOrNull { it.playerId == state.selfId }; FreshOutlineButton(if (self?.ready == true) "取消准备" else "准备", { if (self?.ready == true) viewModel.unready() else viewModel.ready() }, Modifier.weight(1f).height(42.dp), enabled = self != null && !self.isBot); if (state.isHost) FreshButton("开始游戏", viewModel::startGame, Modifier.width(130.dp).height(42.dp), enabled = state.room?.players?.size == state.room?.maxPlayers && state.room?.players?.filterNot { it.isBot }?.all { it.ready } == true) }
    } }
    state.errorMessage?.let { ErrorDialog(it, viewModel::dismissError) }
    if (confirmExit) ConfirmDialog("退出房间？", "本机与该房间的连接将关闭。", "取消", "退出", { confirmExit = false }, { confirmExit = false; viewModel.leaveRoom(); onBack() })
}

@Composable
fun UnoLanGameScreen(viewModel: UnoLanViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmExit by rememberSaveable { mutableStateOf(false) }
    BackHandler { confirmExit = true }
    UnoLanBackground {
        UnoGameTableContent(
            state = state.toTablePresentation(),
            callbacks = UnoTableCallbacks(
                onExit = { confirmExit = true },
                onPlayCard = viewModel::playCard,
                onDrawCard = viewModel::drawCard,
                onPlayDrawnCard = viewModel::playDrawnCard,
                onPassAfterDraw = viewModel::passAfterDraw,
                onDeclareUno = viewModel::declareUno,
                onCatchUno = { viewModel.catchUno(state.game?.catchTargetPlayerId) },
                onChooseColor = { color -> viewModel.chooseColor(color.toProtocolColor()) },
                onNext = viewModel::startNextRound,
                onReturnHome = { viewModel.leaveRoom(); onBack() },
            ),
        )
    }
    state.errorMessage?.let { ErrorDialog(it, viewModel::dismissError) }
    if (confirmExit) ConfirmDialog("退出当前牌局？", "本机将退出房间，牌局仍由房主继续。", "继续游戏", "退出", { confirmExit = false }, { confirmExit = false; viewModel.leaveRoom(); onBack() })
}

@Composable private fun UnoLanBackground(content: @Composable () -> Unit) { UnoBackground(Modifier.fillMaxSize()) { content() } }
@Composable private fun Label(text: String) { Text(text, color = LavenderDeep, fontWeight = FontWeight.Black, fontSize = 14.sp) }
@Composable private fun ChoiceRow(labels: List<String>, selected: Int, onSelected: (Int) -> Unit) { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { labels.forEachIndexed { index, label -> Box(Modifier.clip(RoundedCornerShape(14.dp)).background(if (selected == index) Lavender.copy(.45f) else Color.White.copy(.65f)).border(1.dp, if (selected == index) LavenderDeep else Lavender, RoundedCornerShape(14.dp)).clickable { onSelected(index) }.padding(horizontal = 14.dp, vertical = 9.dp)) { Text(label, color = if (selected == index) LavenderDeep else MutedInk, fontWeight = FontWeight.Black, fontSize = 12.sp) } } } }
@Composable private fun ConnectionPill(connection: ConnectionState) { val pair = when (connection) { ConnectionState.CONNECTED -> "已连接" to MintDeep; ConnectionState.CONNECTING -> "连接中" to PeachDeep; ConnectionState.RECONNECTING -> "重连中" to PeachDeep; ConnectionState.FAILED -> "连接失败" to RoseRed; ConnectionState.DISCONNECTED -> "未连接" to MutedInk }; Text("● ${pair.first}", color = pair.second, fontWeight = FontWeight.Black, fontSize = 11.sp, modifier = Modifier.clip(RoundedCornerShape(18.dp)).background(Color.White.copy(.78f)).padding(horizontal = 10.dp, vertical = 7.dp)) }
@Composable private fun RoomRow(room: UnoLanRoom, onJoin: () -> Unit) { Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(Mint.copy(.38f)).padding(9.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(room.roomName, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis); Text("${room.host}:${room.port} · ${room.playerCount}/${room.maxPlayers}人 · ${gameModeName(room.gameMode)}", color = MutedInk, fontSize = 10.sp) }; FreshOutlineButton("加入", onJoin, Modifier.width(70.dp).height(34.dp)) } }
@Composable private fun LobbySeat(player: UnoV5PlayerView, isHost: Boolean, isSelf: Boolean, onRemove: () -> Unit) { Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(if (isSelf) Lavender.copy(.26f) else Color.White.copy(.68f)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(34.dp).clip(RoundedCornerShape(12.dp)).background(if (player.isBot) Mint.copy(.7f) else Peach.copy(.7f)), contentAlignment = Alignment.Center) { Text(if (player.isBot) "机" else "玩", fontSize = 10.sp, fontWeight = FontWeight.Black) }; Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text("${player.seatIndex + 1}号座位 · ${player.displayName}", fontWeight = FontWeight.Black); Text(listOfNotNull(if (isHost) "房主" else null, if (player.isBot) "机器人" else "玩家", if (player.ready) "已准备" else "等待准备").joinToString(" · "), color = if (player.ready) MintDeep else MutedInk, fontSize = 11.sp) }; if (isHost && player.isBot) TextButton(onClick = onRemove) { Text("移除", color = RoseRed) } } }
@Composable private fun ErrorDialog(message: String, onDismiss: () -> Unit) { AlertDialog(onDismissRequest = onDismiss, title = { Text("小提示", fontWeight = FontWeight.Black) }, text = { Text(message) }, confirmButton = { TextButton(onClick = onDismiss) { Text("知道啦") } }) }
@Composable private fun ConfirmDialog(title: String, text: String, dismissLabel: String, confirmLabel: String, onDismiss: () -> Unit, onConfirm: () -> Unit) { AlertDialog(onDismissRequest = onDismiss, title = { Text(title, fontWeight = FontWeight.Black) }, text = { Text(text) }, dismissButton = { TextButton(onClick = onDismiss) { Text(dismissLabel) } }, confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel, color = PeachDeep) } }) }
private fun gameModeName(mode: UnoV5GameMode?): String = if (mode == UnoV5GameMode.POINTS_500) "积分赛 500 分" else "快速游戏"

private fun UnoLanUiState.toTablePresentation(): UnoTablePresentationState {
    val game = game
    val self = game?.players?.firstOrNull { it.playerId == selfId }
    val matchFinished = room?.status == UnoV5RoomStatus.MATCH_FINISHED
    val roundFinished = room?.status == UnoV5RoomStatus.ROUND_FINISHED
    val winnerId = if (matchFinished) game?.matchWinnerId else game?.roundWinnerId
    val winnerName = game?.players?.firstOrNull { it.playerId == winnerId }?.displayName ?: "未知玩家"
    val current = game?.players?.firstOrNull { it.playerId == game.currentPlayerId }
    return UnoTablePresentationState(
        phase = when {
            matchFinished -> UnoTablePhase.MATCH_FINISHED
            roundFinished -> UnoTablePhase.ROUND_FINISHED
            game?.phase == "AFTER_DRAW" -> UnoTablePhase.AFTER_DRAW
            game?.phase == "CHOOSE_COLOR" -> UnoTablePhase.CHOOSE_COLOR
            game?.phase == "TURN" -> UnoTablePhase.TURN
            else -> UnoTablePhase.WAITING
        },
        turnText = when {
            matchFinished -> "整场结束"
            roundFinished -> "本局结束"
            game?.currentPlayerId == selfId && game?.phase == "AFTER_DRAW" -> "请手动选择：打出刚摸的牌或不出"
            game?.currentPlayerId == selfId -> "轮到你"
            current?.isBot == true -> "${current.displayName}思考中…"
            current != null -> "等待 ${current.displayName} 操作"
            else -> "等待状态同步"
        },
        roundAndModeText = "第${game?.roundNumber ?: 1}局 · ${gameModeName(room?.gameMode)}",
        activeColor = game?.activeColor.toTableColor(),
        activeColorName = game?.activeColor.toColorName(),
        clockwise = game?.direction != "COUNTERCLOCKWISE",
        opponents = opponentViews.map { player ->
            UnoTablePlayer(player.playerId, player.displayName, if (player.isBot) "机器人" else "玩家", player.handCount, player.score, player.playerId == game?.currentPlayerId, player.playerId == game?.roundWinnerId, player.playerId == game?.matchWinnerId)
        },
        hand = selfHand.map(UnoV5Card::toTableCard),
        legalCardIds = game?.legalPlayableCardIds.orEmpty().toSet(),
        drawPileCount = game?.drawPileCount ?: 0,
        topDiscard = game?.topDiscard?.toTableCard(),
        canDraw = UnoV5ActionType.DRAW_CARD in game?.legalActions.orEmpty(),
        canPlayDrawnCard = UnoV5ActionType.PLAY_DRAWN_CARD in game?.legalActions.orEmpty(),
        drawnCardId = game?.drawnCardId,
        canPassAfterDraw = UnoV5ActionType.PASS_AFTER_DRAW in game?.legalActions.orEmpty(),
        canDeclareUno = UnoV5ActionType.DECLARE_UNO in game?.legalActions.orEmpty(),
        canCatchUno = UnoV5ActionType.CATCH_UNO in game?.legalActions.orEmpty(),
        isActionInProgress = isBusy,
        isBotThinking = current?.isBot == true,
        mustChooseColor = game?.phase == "CHOOSE_COLOR" && game?.colorChooserPlayerId == selfId,
        localPlayerWon = self?.playerId == winnerId,
        connectionBadge = connectionState.toChineseLabel(),
        roomBadge = room?.roomCode?.let { "房间 $it" },
        eventMessage = eventMessage,
        result = if (roundFinished || matchFinished) UnoTableResult(
            matchFinished = matchFinished,
            title = if (matchFinished) "UNO 整场结束" else "UNO 本局结束",
            winnerLine = "$winnerName 获胜",
            ranking = room?.players.orEmpty().sortedByDescending { it.score }.map { UnoTableRanking(it.displayName, it.score) },
            nextLabel = if (roundFinished) "下一局" else null,
        ) else null,
    )
}

private fun UnoV5Card.toTableCard(): UnoTableCard = UnoTableCard(
    cardId = cardId,
    color = color.toTableColor(),
    label = if (type == "NUMBER") number?.toString().orEmpty() else when (type) {
        "DRAW_TWO" -> "+2"
        "WILD_DRAW_FOUR" -> "+4"
        "REVERSE" -> "↺"
        "SKIP" -> "⊘"
        "WILD" -> "WILD"
        else -> type
    },
    isWild = type == "WILD" || type == "WILD_DRAW_FOUR",
)

private fun String?.toTableColor(): UnoTableColor = when (this) {
    "RED" -> UnoTableColor.RED
    "YELLOW" -> UnoTableColor.YELLOW
    "GREEN" -> UnoTableColor.GREEN
    "BLUE" -> UnoTableColor.BLUE
    else -> UnoTableColor.WILD
}

private fun String?.toColorName(): String = when (this) { "RED" -> "红"; "YELLOW" -> "黄"; "GREEN" -> "绿"; "BLUE" -> "蓝"; else -> "待选择" }
private fun UnoTableColor.toProtocolColor(): String = name
private fun ConnectionState.toChineseLabel(): String = when (this) { ConnectionState.CONNECTED -> "已连接"; ConnectionState.CONNECTING -> "连接中"; ConnectionState.RECONNECTING -> "重连中"; ConnectionState.FAILED -> "连接失败"; ConnectionState.DISCONNECTED -> "未连接" }
