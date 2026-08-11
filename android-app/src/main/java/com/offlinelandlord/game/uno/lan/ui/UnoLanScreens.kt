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
import com.offlinelandlord.game.uno.ui.UnoBackground

@Composable
fun UnoLanHomeScreen(viewModel: UnoLanViewModel, onBack: () -> Unit, onLobby: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var name by rememberSaveable { mutableStateOf("Player") }
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
                    item { Label("Create room") }
                    item { ChoiceRow((2..4).map { "${it} players" }, players - 2) { players = it + 2 } }
                    item { ChoiceRow(listOf("Quick", "Points 500"), if (mode == UnoV5GameMode.QUICK) 0 else 1) { mode = if (it == 0) UnoV5GameMode.QUICK else UnoV5GameMode.POINTS_500 } }
                    item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { OutlinedTextField(name, { name = it.take(16) }, label = { Text("Name") }, singleLine = true, modifier = Modifier.weight(1f)); FreshButton("Create", { viewModel.createRoom(name, players, mode) }, Modifier.width(112.dp).height(48.dp), enabled = !state.isBusy) } }
                    item { Label("Join room") }
                    item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(host, { host = it.take(45) }, label = { Text("Host IP") }, singleLine = true, modifier = Modifier.weight(1.2f)); OutlinedTextField(port, { port = it.filter(Char::isDigit).take(5) }, label = { Text("Port") }, singleLine = true, modifier = Modifier.weight(.7f)); OutlinedTextField(code, { code = it.filter(Char::isDigit).take(6) }, label = { Text("Room code") }, singleLine = true, modifier = Modifier.weight(1f)) } }
                    item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { FreshOutlineButton("Find nearby", viewModel::discoverRooms, Modifier.weight(1f), enabled = !state.isDiscovering); FreshButton("Join", { viewModel.joinRoom(host, port.toIntOrNull() ?: 0, code, name) }, Modifier.weight(1f), color = LavenderDeep, enabled = !state.isBusy) } }
                    if (state.isDiscovering) item { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) } }
                    if (state.rooms.isNotEmpty()) { item { Label("Nearby UNO rooms") }; items(state.rooms, key = { "${it.host}:${it.port}:${it.roomCode}" }) { room -> RoomRow(room) { viewModel.joinDiscovered(room, name) } } }
                    item { TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back to UNO home", color = LavenderDeep) } }
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
        Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Room ${state.room?.roomCode.orEmpty()}", color = Ink, fontSize = 26.sp, fontWeight = FontWeight.Black); Text("${state.room?.gameMode ?: UnoV5GameMode.QUICK} · ${state.hostAddress}:${state.hostPort}", color = MutedInk, fontSize = 12.sp) }; ConnectionPill(state.connectionState); Spacer(Modifier.width(8.dp)); FreshOutlineButton("Leave", { confirmExit = true }, Modifier.width(90.dp).height(40.dp)) }
        SoftPanel(Modifier.fillMaxWidth().weight(1f), tint = Color(0xEFFFFFFF)) { LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { item { Text("Players", color = LavenderDeep, fontWeight = FontWeight.Black) }; items(state.room?.players.orEmpty(), key = { it.playerId }) { p -> LobbySeat(p, p.playerId == state.room?.hostPlayerId, p.playerId == state.selfId) { if (p.isBot) viewModel.removeBot(p.playerId) } } } }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { if (state.isHost) FreshOutlineButton("+ Bot", viewModel::addBot, Modifier.width(100.dp).height(42.dp), enabled = state.room?.status == UnoV5RoomStatus.WAITING); val self = state.room?.players?.firstOrNull { it.playerId == state.selfId }; FreshOutlineButton(if (self?.ready == true) "Unready" else "Ready", { if (self?.ready == true) viewModel.unready() else viewModel.ready() }, Modifier.weight(1f).height(42.dp), enabled = self != null && !self.isBot); if (state.isHost) FreshButton("Start", viewModel::startGame, Modifier.width(120.dp).height(42.dp), enabled = state.room?.players?.size == state.room?.maxPlayers && state.room?.players?.filterNot { it.isBot }?.all { it.ready } == true) }
    } }
    state.errorMessage?.let { ErrorDialog(it, viewModel::dismissError) }
    if (confirmExit) ConfirmDialog("Leave room?", "The local connection will close.", { confirmExit = false }, { confirmExit = false; viewModel.leaveRoom(); onBack() })
}

@Composable
fun UnoLanGameScreen(viewModel: UnoLanViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmExit by rememberSaveable { mutableStateOf(false) }
    var showColors by rememberSaveable { mutableStateOf(false) }
    BackHandler { confirmExit = true }
    LaunchedEffect(state.game?.phase) { if (state.game?.phase == "CHOOSE_COLOR") showColors = true }
    UnoLanBackground { BoxWithConstraints(Modifier.fillMaxSize().padding(12.dp)) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("UNO · ${state.room?.roomCode.orEmpty()}", color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Black); Text(if (state.currentPlayerName == null) "Waiting" else "Turn: ${state.currentPlayerName}", color = if (state.game?.currentPlayerId == state.selfId) PeachDeep else MutedInk, fontWeight = FontWeight.Bold, fontSize = 12.sp) }; ConnectionPill(state.connectionState); FreshOutlineButton("Exit", { confirmExit = true }, Modifier.width(76.dp).height(34.dp)) }
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) { val opponents = state.opponentViews; OpponentCard(opponents.getOrNull(0), Modifier.width(104.dp).fillMaxHeight(.45f).align(Alignment.CenterVertically)); Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { if (opponents.size > 2) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { opponents.drop(2).forEach { OpponentMini(it) } }; Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) { PileCard("Draw\n${state.game?.drawPileCount ?: 0}", LavenderDeep); PileCard(cardLabel(state.game?.topDiscard), cardColor(state.game?.topDiscard?.color), true) }; Text("Color ${state.game?.activeColor ?: "-"} · ${state.game?.direction ?: "-"}", color = MutedInk, fontSize = 11.sp); Text(if (state.game?.currentPlayerId == state.selfId) "Your turn" else "Waiting for opponent", color = if (state.game?.currentPlayerId == state.selfId) PeachDeep else MintDeep, fontWeight = FontWeight.Black) }; OpponentCard(opponents.getOrNull(1), Modifier.width(104.dp).fillMaxHeight(.45f).align(Alignment.CenterVertically)) }
            SoftPanel(Modifier.fillMaxWidth().heightIn(min = 112.dp, max = 180.dp), tint = Color(0xEFFFFFFF)) { Column(Modifier.fillMaxSize().padding(8.dp)) { Text("Your hand · ${state.selfHand.size}", color = Ink, fontWeight = FontWeight.Black, fontSize = 11.sp); LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) { items(state.selfHand, key = { it.cardId }) { card -> LanCard(card, card.cardId in state.game?.legalPlayableCardIds.orEmpty()) { viewModel.playCard(card.cardId) } } }; Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) { if (UnoV5ActionType.DRAW_CARD in state.game?.legalActions.orEmpty()) FreshOutlineButton("Draw", viewModel::drawCard, Modifier.weight(1f).height(34.dp)); if (UnoV5ActionType.PASS_AFTER_DRAW in state.game?.legalActions.orEmpty()) FreshOutlineButton("Pass", viewModel::passAfterDraw, Modifier.weight(1f).height(34.dp)); if (UnoV5ActionType.DECLARE_UNO in state.game?.legalActions.orEmpty()) FreshButton("UNO", viewModel::declareUno, Modifier.weight(1f).height(34.dp)); if (UnoV5ActionType.CATCH_UNO in state.game?.legalActions.orEmpty()) FreshButton("Catch", { viewModel.catchUno(state.game?.catchTargetPlayerId) }, Modifier.weight(1f).height(34.dp), color = RoseRed) } } }
        }
        if (state.isFinished) MatchResultCard(state, viewModel, { viewModel.leaveRoom(); onBack() }, Modifier.align(Alignment.Center))
    } }
    if (showColors) ColorDialog({ showColors = false }, viewModel::chooseColor)
    state.errorMessage?.let { ErrorDialog(it, viewModel::dismissError) }
    if (confirmExit) ConfirmDialog("Exit game?", "The current match will close on this device.", { confirmExit = false }, { confirmExit = false; viewModel.leaveRoom(); onBack() })
}

@Composable private fun UnoLanBackground(content: @Composable () -> Unit) { UnoBackground(Modifier.fillMaxSize()) { content() } }
@Composable private fun Label(text: String) { Text(text, color = LavenderDeep, fontWeight = FontWeight.Black, fontSize = 14.sp) }
@Composable private fun ChoiceRow(labels: List<String>, selected: Int, onSelected: (Int) -> Unit) { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { labels.forEachIndexed { index, label -> Box(Modifier.clip(RoundedCornerShape(14.dp)).background(if (selected == index) Lavender.copy(.45f) else Color.White.copy(.65f)).border(1.dp, if (selected == index) LavenderDeep else Lavender, RoundedCornerShape(14.dp)).clickable { onSelected(index) }.padding(horizontal = 14.dp, vertical = 9.dp)) { Text(label, color = if (selected == index) LavenderDeep else MutedInk, fontWeight = FontWeight.Black, fontSize = 12.sp) } } } }
@Composable private fun ConnectionPill(connection: ConnectionState) { val pair = when (connection) { ConnectionState.CONNECTED -> "Connected" to MintDeep; ConnectionState.CONNECTING -> "Connecting" to PeachDeep; ConnectionState.RECONNECTING -> "Reconnecting" to PeachDeep; ConnectionState.FAILED -> "Failed" to RoseRed; ConnectionState.DISCONNECTED -> "Offline" to MutedInk }; Text("● ${pair.first}", color = pair.second, fontWeight = FontWeight.Black, fontSize = 11.sp, modifier = Modifier.clip(RoundedCornerShape(18.dp)).background(Color.White.copy(.78f)).padding(horizontal = 10.dp, vertical = 7.dp)) }
@Composable private fun RoomRow(room: UnoLanRoom, onJoin: () -> Unit) { Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(Mint.copy(.38f)).padding(9.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(room.roomName, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis); Text("${room.host}:${room.port} · ${room.playerCount}/${room.maxPlayers} · ${room.gameMode}", color = MutedInk, fontSize = 10.sp) }; FreshOutlineButton("Join", onJoin, Modifier.width(70.dp).height(34.dp)) } }
@Composable private fun LobbySeat(player: UnoV5PlayerView, isHost: Boolean, isSelf: Boolean, onRemove: () -> Unit) { Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(if (isSelf) Lavender.copy(.26f) else Color.White.copy(.68f)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(34.dp).clip(RoundedCornerShape(12.dp)).background(if (player.isBot) Mint.copy(.7f) else Peach.copy(.7f)), contentAlignment = Alignment.Center) { Text(if (player.isBot) "BOT" else "P", fontSize = 10.sp, fontWeight = FontWeight.Black) }; Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text("Seat ${player.seatIndex + 1} · ${player.displayName}", fontWeight = FontWeight.Black); Text(listOfNotNull(if (isHost) "Host" else null, if (player.isBot) "Bot" else "Player", if (player.ready) "Ready" else "Waiting").joinToString(" · "), color = if (player.ready) MintDeep else MutedInk, fontSize = 11.sp) }; if (isHost && player.isBot) TextButton(onClick = onRemove) { Text("Remove", color = RoseRed) } } }
@Composable private fun OpponentCard(player: UnoV5PlayerView?, modifier: Modifier) { if (player == null) Spacer(modifier) else SoftPanel(modifier, tint = Color(0xEFFFFFFF)) { Column(Modifier.fillMaxSize().padding(7.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Text(player.displayName, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(if (player.isBot) "BOT" else "Player", color = if (player.isBot) MintDeep else LavenderDeep, fontSize = 10.sp); Text("${player.handCount} cards", color = Ink, fontWeight = FontWeight.Bold, fontSize = 12.sp) } } }
@Composable private fun OpponentMini(player: UnoV5PlayerView) { Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(72.dp)) { Text(player.displayName, fontWeight = FontWeight.Black, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis); Text("${player.handCount} cards", color = MutedInk, fontSize = 9.sp) } }
@Composable private fun PileCard(text: String, color: Color, highlighted: Boolean = false) { Box(Modifier.width(72.dp).height(88.dp).clip(RoundedCornerShape(13.dp)).background(Color.White.copy(.94f)).border(if (highlighted) 2.dp else 1.dp, color.copy(.7f), RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) { Text(text, textAlign = TextAlign.Center, color = color, fontWeight = FontWeight.Black, fontSize = 13.sp) } }
@Composable private fun LanCard(card: UnoV5Card, enabled: Boolean, onClick: () -> Unit) { Box(Modifier.width(58.dp).height(84.dp).clip(RoundedCornerShape(11.dp)).background(Color.White.copy(if (enabled) .98f else .68f)).border(if (enabled) 2.dp else 1.dp, cardColor(card.color).copy(alpha = if (enabled) .85f else .25f), RoundedCornerShape(11.dp)).clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(cardLabel(card), color = cardColor(card.color), fontWeight = FontWeight.Black, fontSize = 17.sp); if (card.type != "NUMBER") Text(card.type.replace('_', ' '), color = cardColor(card.color), fontSize = 7.sp, textAlign = TextAlign.Center) } } }
@Composable private fun MatchResultCard(state: UnoLanUiState, viewModel: UnoLanViewModel, onLeave: () -> Unit, modifier: Modifier) { SoftPanel(modifier.width(410.dp).height(270.dp), tint = Color(0xF8FFFFFF)) { Column(Modifier.fillMaxSize().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text("Match result", color = PeachDeep, fontSize = 24.sp, fontWeight = FontWeight.Black); Text("Winner: ${state.game?.roundWinnerId.orEmpty()}", color = Ink, fontWeight = FontWeight.Bold); Spacer(Modifier.height(10.dp)); state.room?.players?.sortedByDescending { it.score }?.forEach { Text("${it.displayName} · ${it.score}", color = if (it.playerId == state.game?.matchWinnerId) PeachDeep else Ink, fontWeight = FontWeight.Bold) }; Spacer(Modifier.weight(1f)); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { if (state.game?.roundWinnerId != null && state.game?.matchWinnerId == null) FreshButton("Next round", viewModel::startNextRound, Modifier.width(130.dp).height(38.dp)); FreshOutlineButton("Leave", onLeave, Modifier.width(110.dp).height(38.dp)) } } } }
@Composable private fun ColorDialog(onDismiss: () -> Unit, onColor: (String) -> Unit) { AlertDialog(onDismissRequest = onDismiss, title = { Text("Choose color", fontWeight = FontWeight.Black) }, text = { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) { listOf("RED" to Color(0xFFE66D72), "YELLOW" to Color(0xFFF3C64E), "GREEN" to Color(0xFF5BAF86), "BLUE" to Color(0xFF5F8DD3)).forEach { (name, color) -> Box(Modifier.size(54.dp).clip(RoundedCornerShape(16.dp)).background(color).clickable { onDismiss(); onColor(name) }, contentAlignment = Alignment.Center) { Text(name.take(1), color = Color.White, fontWeight = FontWeight.Black) } } } }, confirmButton = {}) }
@Composable private fun ErrorDialog(message: String, onDismiss: () -> Unit) { AlertDialog(onDismissRequest = onDismiss, title = { Text("Notice", fontWeight = FontWeight.Black) }, text = { Text(message) }, confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } }) }
@Composable private fun ConfirmDialog(title: String, text: String, onDismiss: () -> Unit, onConfirm: () -> Unit) { AlertDialog(onDismissRequest = onDismiss, title = { Text(title, fontWeight = FontWeight.Black) }, text = { Text(text) }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }, confirmButton = { TextButton(onClick = onConfirm) { Text("Leave", color = PeachDeep) } }) }
private fun cardLabel(card: UnoV5Card?): String = card?.let { if (it.type == "NUMBER") it.number?.toString().orEmpty() else when (it.type) { "DRAW_TWO" -> "+2"; "WILD_DRAW_FOUR" -> "+4"; "REVERSE" -> "↔"; "SKIP" -> "⏭"; "WILD" -> "WILD"; else -> it.type } } ?: "-"
private fun cardColor(color: String?): Color = when (color) { "RED" -> Color(0xFFD65B64); "YELLOW" -> Color(0xFFCC9A20); "GREEN" -> Color(0xFF3E9568); "BLUE" -> Color(0xFF4F74B6); else -> Ink }
