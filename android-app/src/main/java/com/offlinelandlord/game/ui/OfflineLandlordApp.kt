package com.offlinelandlord.game.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.offlinelandlord.game.core.Card as GameCard
import com.offlinelandlord.game.core.GamePhase
import com.offlinelandlord.game.core.PlayerGameView
import com.offlinelandlord.game.core.PlayerRole
import com.offlinelandlord.game.core.PlayerSummary
import com.offlinelandlord.game.core.Rank
import com.offlinelandlord.game.core.Suit
import com.offlinelandlord.game.network.ConnectionState
import com.offlinelandlord.game.network.DiscoveredRoom
import com.offlinelandlord.game.network.LanGameServer

@Composable
fun OfflineLandlordApp(viewModel: GameViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Surface(modifier = Modifier.fillMaxSize()) {
        when (state.gameView?.phase) {
            null -> HomeScreen(state, viewModel)
            GamePhase.WAITING -> LobbyScreen(state, viewModel)
            GamePhase.BIDDING,
            GamePhase.PLAYING,
            GamePhase.FINISHED,
            -> GameTableScreen(state, viewModel)
        }

        state.errorMessage?.let { message ->
            AlertDialog(
                onDismissRequest = viewModel::dismissError,
                confirmButton = {
                    TextButton(onClick = viewModel::dismissError) { Text("知道了") }
                },
                title = { Text("提示") },
                text = { Text(message) },
            )
        }
    }
}

@Composable
private fun HomeScreen(state: AppUiState, viewModel: GameViewModel) {
    var playerName by rememberSaveable { mutableStateOf("玩家${(10..99).random()}") }
    var hostIp by rememberSaveable { mutableStateOf("192.168.43.1") }
    var roomCode by rememberSaveable { mutableStateOf("") }
    var portText by rememberSaveable { mutableStateOf("${state.port}") }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B5133))
            .padding(horizontal = 28.dp, vertical = 22.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column(
            modifier = Modifier.weight(0.9f).fillMaxHeight(),
            verticalArrangement = Arrangement.Center,
        ) {
            Text("离线斗地主", fontSize = 34.sp, fontWeight = FontWeight.Black, color = Color(0xFFFFD166))
            Spacer(Modifier.height(8.dp))
            Text("三台安卓手机 · 同一热点 · 不需要互联网", fontSize = 16.sp)
            Spacer(Modifier.height(24.dp))
            OutlinedTextField(
                value = playerName,
                onValueChange = { playerName = it.take(12) },
                label = { Text("你的昵称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { viewModel.createRoom(playerName) },
                enabled = !state.isBusy,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text("创建离线房间", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "房主请先在系统设置中开启个人热点；移动数据可以关闭。",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFC9DDCF),
            )
        }

        Card(
            modifier = Modifier.weight(1.25f).fillMaxHeight(),
            colors = CardDefaults.cardColors(containerColor = Color(0xCC123B2A)),
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("加入房间", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = viewModel::discoverRooms, enabled = !state.isDiscovering) {
                        if (state.isDiscovering) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(if (state.isDiscovering) "正在搜索" else "搜索附近房间")
                    }
                }

                if (state.discoveredRooms.isNotEmpty()) {
                    state.discoveredRooms.take(3).forEach { room ->
                        DiscoveredRoomRow(room) { viewModel.joinDiscovered(room, playerName) }
                    }
                    HorizontalDivider(color = Color(0x447FC69B))
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = hostIp,
                        onValueChange = { hostIp = it.take(45) },
                        label = { Text("房主 IP") },
                        singleLine = true,
                        modifier = Modifier.weight(1.4f),
                    )
                    OutlinedTextField(
                        value = portText,
                        onValueChange = { portText = it.filter(Char::isDigit).take(5) },
                        label = { Text("端口") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(0.75f),
                    )
                }
                OutlinedTextField(
                    value = roomCode,
                    onValueChange = { roomCode = it.filter(Char::isDigit).take(6) },
                    label = { Text("六位房间码") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = {
                        viewModel.joinRoom(
                            hostIp,
                            portText.toIntOrNull() ?: LanGameServer.DEFAULT_TCP_PORT,
                            roomCode,
                            playerName,
                        )
                    },
                    enabled = !state.isBusy,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) {
                    if (state.isBusy) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("手动加入")
                }
            }
        }
    }
}

@Composable
private fun DiscoveredRoomRow(room: DiscoveredRoom, onJoin: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0x55307448))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(room.roomName, fontWeight = FontWeight.Bold)
            Text("${room.host}:${room.port} · 房间 ${room.roomCode}", fontSize = 12.sp, color = Color(0xFFC9DDCF))
        }
        Button(onClick = onJoin) { Text("加入") }
    }
}

@Composable
private fun LobbyScreen(state: AppUiState, viewModel: GameViewModel) {
    val view = state.gameView ?: return
    val self = view.players.first { it.id == view.selfPlayerId }
    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFF0B5133)).padding(22.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(view.roomName, fontSize = 28.sp, fontWeight = FontWeight.Black, color = Color(0xFFFFD166))
                Text("房间码 ${view.roomCode}", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                if (state.isHost) Text("房主地址 ${state.hostAddress}:${state.port}", color = Color(0xFFC9DDCF))
            }
            ConnectionBadge(state.connectionState)
            Spacer(Modifier.width(12.dp))
            OutlinedButton(onClick = viewModel::leaveRoom) { Text("离开房间") }
        }
        Spacer(Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            (0..2).forEach { seat ->
                val player = view.players.firstOrNull { it.seat == seat }
                PlayerSeatCard(player, modifier = Modifier.weight(1f).fillMaxHeight())
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(view.statusMessage, modifier = Modifier.weight(1f), color = Color(0xFFC9DDCF))
            Button(
                onClick = { viewModel.setReady(!self.ready) },
                colors = if (self.ready) ButtonDefaults.buttonColors(containerColor = Color(0xFF8A5A00)) else ButtonDefaults.buttonColors(),
                modifier = Modifier.width(180.dp).height(50.dp),
            ) {
                Text(if (self.ready) "取消准备" else "准备", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun PlayerSeatCard(player: PlayerSummary?, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color(0xAA123B2A)),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (player == null) {
                Text("等待玩家", fontSize = 18.sp, color = Color(0xFF9EBBAB))
            } else {
                Text(if (player.seat == 0) "房主" else "玩家 ${player.seat + 1}", color = Color(0xFFFFD166))
                Spacer(Modifier.height(10.dp))
                Text(player.name, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Text(if (player.connected) "已连接" else "已断线", color = if (player.connected) Color(0xFF8BD3A9) else Color(0xFFFF8A80))
                Text(if (player.ready) "已准备" else "未准备", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun GameTableScreen(state: AppUiState, viewModel: GameViewModel) {
    val view = state.gameView ?: return
    val self = view.players.first { it.id == view.selfPlayerId }
    val opponents = view.players.filter { it.id != view.selfPlayerId }.sortedBy { it.seat }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    LaunchedEffect(view.revision) {
        val available = view.ownHand.mapTo(mutableSetOf()) { it.id }
        selectedIds = selectedIds.intersect(available)
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFF0B5133)).padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("房间 ${view.roomCode}", fontWeight = FontWeight.Bold, color = Color(0xFFFFD166))
            Spacer(Modifier.width(12.dp))
            Text("倍数 ×${view.multiplier}")
            Spacer(Modifier.weight(1f))
            ConnectionBadge(state.connectionState)
            Spacer(Modifier.width(10.dp))
            OutlinedButton(onClick = viewModel::leaveRoom) { Text("退出") }
        }

        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OpponentPanel(opponents.getOrNull(0), view.currentTurnId)

            Column(
                modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                if (view.bottomCards.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        view.bottomCards.forEach { MiniCard(it) }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Text(view.statusMessage, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                view.lastPlay?.let { play ->
                    val playerName = view.players.firstOrNull { it.id == play.playerId }?.name.orEmpty()
                    Text("$playerName · ${play.pattern.type.displayName}", color = Color(0xFFFFD166))
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        play.cards.forEach { MiniCard(it) }
                    }
                } ?: Text("等待领出", color = Color(0xFF9EBBAB))

                Spacer(Modifier.height(12.dp))
                when (view.phase) {
                    GamePhase.BIDDING -> BiddingControls(view, viewModel)
                    GamePhase.FINISHED -> ResultPanel(view, self, viewModel)
                    else -> Unit
                }
            }

            OpponentPanel(opponents.getOrNull(1), view.currentTurnId)
        }

        HorizontalDivider(color = Color(0x447FC69B))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(120.dp)) {
                Text(self.name, fontWeight = FontWeight.Bold)
                Text(roleText(self.role), color = Color(0xFFFFD166))
                Text("积分 ${self.score}", fontSize = 12.sp)
            }
            Row(
                modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()).padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                view.ownHand.forEach { card ->
                    CardFace(
                        card = card,
                        selected = card.id in selectedIds,
                        enabled = view.phase == GamePhase.PLAYING,
                        onClick = {
                            selectedIds = if (card.id in selectedIds) selectedIds - card.id else selectedIds + card.id
                        },
                    )
                }
            }
            if (view.phase == GamePhase.PLAYING) {
                Column(
                    modifier = Modifier.width(118.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Button(
                        onClick = {
                            viewModel.play(selectedIds.toList())
                            selectedIds = emptySet()
                        },
                        enabled = view.currentTurnId == view.selfPlayerId && selectedIds.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("出牌") }
                    OutlinedButton(
                        onClick = viewModel::pass,
                        enabled = view.currentTurnId == view.selfPlayerId && view.lastPlay != null,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("不出") }
                }
            }
        }
    }
}

@Composable
private fun BiddingControls(view: PlayerGameView, viewModel: GameViewModel) {
    val isTurn = view.currentTurnId == view.selfPlayerId
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        (0..3).forEach { value ->
            OutlinedButton(
                onClick = { viewModel.bid(value) },
                enabled = isTurn && (value == 0 || value > view.highestBid),
            ) {
                Text(if (value == 0) "不叫" else "$value 分")
            }
        }
    }
}

@Composable
private fun ResultPanel(view: PlayerGameView, self: PlayerSummary, viewModel: GameViewModel) {
    val result = view.result ?: return
    val won = result.winnerRole == self.role
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xCC1B5C3C))) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (won) "本局胜利" else "本局失利", fontSize = 24.sp, fontWeight = FontWeight.Black, color = if (won) Color(0xFFFFD166) else Color(0xFFFFB4AB))
            Text("${if (result.spring) "春天 · " else ""}最终倍数 ×${result.multiplier}")
            Spacer(Modifier.height(8.dp))
            Button(onClick = { viewModel.setReady(!self.ready) }) {
                Text(if (self.ready) "取消下一局" else "准备下一局")
            }
        }
    }
}

@Composable
private fun OpponentPanel(player: PlayerSummary?, currentTurnId: String?) {
    if (player == null) {
        Spacer(Modifier.width(130.dp))
        return
    }
    Card(
        modifier = Modifier.width(138.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (player.id == currentTurnId) Color(0xFF7A5A00) else Color(0xAA123B2A),
        ),
    ) {
        Column(Modifier.fillMaxWidth().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(player.name, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(roleText(player.role), color = Color(0xFFFFD166), fontSize = 12.sp)
            Text("${player.remainingCards} 张", fontSize = 28.sp, fontWeight = FontWeight.Black)
            Text("积分 ${player.score}", fontSize = 12.sp)
            if (!player.connected) Text("断线", color = Color(0xFFFF8A80), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CardFace(card: GameCard, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val red = card.suit == Suit.HEARTS || card.suit == Suit.DIAMONDS || card.rank == Rank.BIG_JOKER
    Card(
        modifier = Modifier
            .offset(y = if (selected) (-9).dp else 0.dp)
            .size(width = 48.dp, height = 70.dp)
            .border(if (selected) 2.dp else 0.dp, if (selected) Color(0xFFFFD166) else Color.Transparent, RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F5E9)),
    ) {
        Box(Modifier.fillMaxSize().padding(4.dp)) {
            Text(
                card.displayText,
                color = if (red) Color(0xFFC62828) else Color(0xFF151515),
                fontWeight = FontWeight.Black,
                fontSize = if (card.suit == Suit.JOKER) 13.sp else 16.sp,
            )
        }
    }
}

@Composable
private fun MiniCard(card: GameCard) {
    val red = card.suit == Suit.HEARTS || card.suit == Suit.DIAMONDS || card.rank == Rank.BIG_JOKER
    Box(
        modifier = Modifier
            .size(width = 34.dp, height = 46.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(Color(0xFFF8F5E9))
            .padding(3.dp),
    ) {
        Text(card.displayText, color = if (red) Color(0xFFC62828) else Color(0xFF151515), fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ConnectionBadge(state: ConnectionState) {
    val (text, color) = when (state) {
        ConnectionState.CONNECTED -> "已连接" to Color(0xFF8BD3A9)
        ConnectionState.CONNECTING -> "连接中" to Color(0xFFFFD166)
        ConnectionState.RECONNECTING -> "正在重连" to Color(0xFFFFD166)
        ConnectionState.FAILED -> "连接失败" to Color(0xFFFF8A80)
        ConnectionState.DISCONNECTED -> "未连接" to Color(0xFFB0B8B2)
    }
    Text(
        text,
        color = color,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0x44111111))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

private fun roleText(role: PlayerRole): String = when (role) {
    PlayerRole.LANDLORD -> "地主"
    PlayerRole.FARMER -> "农民"
    PlayerRole.UNKNOWN -> "等待身份"
}
