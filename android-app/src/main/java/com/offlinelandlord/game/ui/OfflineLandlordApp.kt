package com.offlinelandlord.game.ui

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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.offlinelandlord.game.core.GamePhase
import com.offlinelandlord.game.core.PlayerGameView
import com.offlinelandlord.game.core.PlayerRole
import com.offlinelandlord.game.core.PlayerSummary
import com.offlinelandlord.game.network.ConnectionState
import com.offlinelandlord.game.network.DiscoveredRoom
import com.offlinelandlord.game.network.LanGameServer
import com.offlinelandlord.game.ui.theme.Cream
import com.offlinelandlord.game.ui.theme.Ink
import com.offlinelandlord.game.ui.theme.Lavender
import com.offlinelandlord.game.ui.theme.LavenderDeep
import com.offlinelandlord.game.ui.theme.Mint
import com.offlinelandlord.game.ui.theme.MintDeep
import com.offlinelandlord.game.ui.theme.MutedInk
import com.offlinelandlord.game.ui.theme.Peach
import com.offlinelandlord.game.ui.theme.PeachDeep
import com.offlinelandlord.game.ui.theme.RoseRed
import com.offlinelandlord.game.ui.theme.Sunny

@Composable
fun OfflineLandlordApp(viewModel: GameViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Surface(modifier = Modifier.fillMaxSize(), color = Cream) {
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
                    TextButton(onClick = viewModel::dismissError) { Text("知道啦") }
                },
                icon = { Text("♡", color = PeachDeep, fontSize = 26.sp) },
                title = { Text("小提示") },
                text = { Text(message) },
                shape = RoundedCornerShape(26.dp),
                containerColor = Color(0xFFFFFBF8),
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

    FreshScenicBackground(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(
                modifier = Modifier.weight(0.86f).fillMaxHeight(),
                verticalArrangement = Arrangement.Top,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(15.dp))
                            .background(PeachDeep),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("♠", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Black)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("离线斗地主", fontSize = 28.sp, fontWeight = FontWeight.Black, color = Ink, maxLines = 1)
                        Text("V3 · 清风牌局", color = PeachDeep, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(Modifier.height(16.dp))

                SoftPanel(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("开一桌小聚", style = MaterialTheme.typography.titleLarge)
                        Text("同一热点相遇，不需要互联网", color = MutedInk, fontSize = 13.sp)
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = playerName,
                                onValueChange = { playerName = it.take(12) },
                                label = { Text("你的昵称") },
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.weight(1f),
                            )
                            FreshButton(
                                text = "创建",
                                onClick = { viewModel.createRoom(playerName) },
                                enabled = !state.isBusy,
                                modifier = Modifier.width(82.dp).height(48.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("房主先开启手机热点；移动数据可以关闭。", color = MutedInk, fontSize = 12.sp)
            }

            SoftPanel(modifier = Modifier.weight(1.14f).fillMaxHeight()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("加入牌桌", style = MaterialTheme.typography.titleLarge)
                            Text("附近房间会出现在这里", color = MutedInk, fontSize = 12.sp)
                        }
                        FreshOutlineButton(
                            text = if (state.isDiscovering) "寻找中…" else "寻找附近房间",
                            onClick = viewModel::discoverRooms,
                            enabled = !state.isDiscovering,
                        )
                    }

                    state.discoveredRooms.take(2).forEach { room ->
                        DiscoveredRoomRow(room) { viewModel.joinDiscovered(room, playerName) }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = hostIp,
                            onValueChange = { hostIp = it.take(45) },
                            label = { Text("房主 IP") },
                            singleLine = true,
                            shape = RoundedCornerShape(15.dp),
                            modifier = Modifier.weight(1.35f),
                        )
                        OutlinedTextField(
                            value = portText,
                            onValueChange = { portText = it.filter(Char::isDigit).take(5) },
                            label = { Text("端口") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = RoundedCornerShape(15.dp),
                            modifier = Modifier.weight(0.75f),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = roomCode,
                            onValueChange = { roomCode = it.filter(Char::isDigit).take(6) },
                            label = { Text("六位房间码") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = RoundedCornerShape(15.dp),
                            modifier = Modifier.weight(1f),
                        )
                        FreshButton(
                            text = if (state.isBusy) "连接中…" else "进入房间",
                            onClick = {
                                viewModel.joinRoom(
                                    hostIp,
                                    portText.toIntOrNull() ?: LanGameServer.DEFAULT_TCP_PORT,
                                    roomCode,
                                    playerName,
                                )
                            },
                            enabled = !state.isBusy,
                            color = LavenderDeep,
                            modifier = Modifier.width(112.dp).height(48.dp),
                        )
                    }
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
            .clip(RoundedCornerShape(16.dp))
            .background(Mint.copy(alpha = 0.45f))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(MintDeep))
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(room.roomName, fontWeight = FontWeight.Bold, maxLines = 1)
            Text("${room.host}:${room.port} · 房间 ${room.roomCode}", fontSize = 11.sp, color = MutedInk)
        }
        FreshOutlineButton("加入", onJoin)
    }
}

@Composable
private fun LobbyScreen(state: AppUiState, viewModel: GameViewModel) {
    val view = state.gameView ?: return
    val self = view.players.first { it.id == view.selfPlayerId }
    val bots = view.players.filter { it.isBot }

    FreshScenicBackground(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 14.dp)) {
            Row(modifier = Modifier.height(54.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(
                    Modifier
                        .width(126.dp)
                        .clip(RoundedCornerShape(17.dp))
                        .background(Color(0xE8FFFFFF))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                ) {
                    Text(view.roomName, fontSize = 16.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("房间码 ${view.roomCode}", color = PeachDeep, fontSize = 12.sp, fontWeight = FontWeight.Black, maxLines = 1)
                }
                Spacer(Modifier.weight(1f))
                ConnectionBadge(state.connectionState)
                Spacer(Modifier.width(6.dp))
                FreshOutlineButton("离开", viewModel::leaveRoom)
            }

            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                (0..2).forEach { seat ->
                    val player = view.players.firstOrNull { it.seat == seat }
                    PlayerSeatCard(player, seat, Modifier.weight(1f).fillMaxHeight())
                }
            }

            Spacer(Modifier.height(12.dp))
            SoftPanel(Modifier.fillMaxWidth(), tint = Color(0xE8FFFFFF)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(view.statusMessage, fontWeight = FontWeight.Bold)
                        if (state.isHost) Text("热点地址 ${state.hostAddress}:${state.port}", color = MutedInk, fontSize = 11.sp)
                    }
                    if (state.isHost) {
                        FreshOutlineButton("＋ 机器人", viewModel::addBot, enabled = view.players.size < 3, color = MintDeep)
                        if (bots.isNotEmpty()) {
                            Spacer(Modifier.width(7.dp))
                            FreshOutlineButton("移除机器人", { viewModel.removeBot(bots.last().id) }, color = RoseRed)
                        }
                        Spacer(Modifier.width(10.dp))
                    }
                    FreshButton(
                        text = if (self.ready) "取消准备" else "准备好啦",
                        onClick = { viewModel.setReady(!self.ready) },
                        color = if (self.ready) MintDeep else PeachDeep,
                        modifier = Modifier.width(142.dp).height(46.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerSeatCard(player: PlayerSummary?, seat: Int, modifier: Modifier = Modifier) {
    SoftPanel(modifier, tint = Color(0xE8FFFFFF)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (player == null) {
                Box(
                    Modifier.size(82.dp).clip(CircleShape).background(Color(0xFFF1EDF5)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("＋", color = LavenderDeep.copy(alpha = 0.55f), fontSize = 34.sp)
                }
                Spacer(Modifier.height(9.dp))
                Text("等待朋友入座", color = MutedInk, fontWeight = FontWeight.Bold)
                Text("座位 ${seat + 1}", color = MutedInk, fontSize = 12.sp)
            } else {
                PastelAvatar(
                    name = player.name,
                    isBot = player.isBot,
                    modifier = Modifier.size(58.dp),
                    accent = when (seat) { 0 -> Peach; 1 -> Lavender; else -> Mint },
                )
                Spacer(Modifier.height(4.dp))
                Text(player.name, fontSize = 18.sp, fontWeight = FontWeight.Black, maxLines = 1)
                Text(
                    when {
                        player.isBot -> "离线机器人 · 已准备"
                        player.isAutoPlaying -> "机器人正在代打"
                        player.ready -> "准备好啦"
                        player.connected -> "已入座"
                        else -> "暂时断线"
                    },
                    color = when {
                        player.ready || player.isBot -> MintDeep
                        !player.connected -> RoseRed
                        else -> LavenderDeep
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                )
                Spacer(Modifier.height(3.dp))
                Text(if (player.seat == 0) "房主" else "玩家 ${player.seat + 1}", color = MutedInk, fontSize = 10.sp)
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

    FreshScenicBackground(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 8.dp)) {
            GameTopBar(view, self, state, viewModel)

            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OpponentPanel(opponents.getOrNull(0), view.currentTurnId, accent = Peach)

                    CenterPlayArea(
                        view = view,
                        viewModel = viewModel,
                        modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                    )

                    OpponentPanel(opponents.getOrNull(1), view.currentTurnId, accent = Lavender)
                }

                PositionedLastPlay(
                    view = view,
                    leftPlayerId = opponents.getOrNull(0)?.id,
                    rightPlayerId = opponents.getOrNull(1)?.id,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            Box(modifier = Modifier.fillMaxWidth().height(140.dp)) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    SelfPanel(self, Modifier.width(112.dp).padding(bottom = 8.dp))
                    CardHand(
                        cards = view.ownHand,
                        selectedIds = selectedIds,
                        enabled = view.phase == GamePhase.PLAYING && !self.isAutoPlaying,
                        onToggle = { card ->
                            selectedIds = if (card.id in selectedIds) selectedIds - card.id else selectedIds + card.id
                        },
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(12.dp))
                }
                if (view.phase == GamePhase.PLAYING) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .offset(y = (-11).dp)
                            .alpha(0.80f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FreshOutlineButton(
                            text = "不出",
                            onClick = viewModel::pass,
                            enabled = view.currentTurnId == view.selfPlayerId && view.lastPlay != null && !self.isAutoPlaying,
                            modifier = Modifier.width(84.dp).height(40.dp),
                            containerColor = Color.White.copy(alpha = 0.58f),
                        )
                        FreshButton(
                            text = "出牌",
                            onClick = {
                                viewModel.play(selectedIds.toList())
                                selectedIds = emptySet()
                            },
                            enabled = view.currentTurnId == view.selfPlayerId && selectedIds.isNotEmpty() && !self.isAutoPlaying,
                            modifier = Modifier.width(84.dp).height(40.dp),
                        )
                    }
                }
            }
        }

        if (view.bottomCards.isNotEmpty()) {
            Row(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                view.bottomCards.forEach { MiniPlayingCard(it) }
            }
        }

        if (view.phase == GamePhase.FINISHED) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                ResultPanel(
                    view = view,
                    self = self,
                    viewModel = viewModel,
                    modifier = Modifier.width(300.dp),
                )
            }
        }
    }
}

@Composable
private fun GameTopBar(
    view: PlayerGameView,
    self: PlayerSummary,
    state: AppUiState,
    viewModel: GameViewModel,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(43.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(Color(0xDFFFFFFF)).padding(horizontal = 11.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(if (state.connectionState == ConnectionState.CONNECTED) MintDeep else PeachDeep))
            Spacer(Modifier.width(6.dp))
            Text("V3 · ${view.roomCode}", color = Ink, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(7.dp))
            Box(Modifier.clip(RoundedCornerShape(10.dp)).background(Sunny.copy(alpha = 0.6f)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                Text("×${view.multiplier}", color = Ink, fontWeight = FontWeight.Black)
            }
        }
        Spacer(Modifier.weight(1f))
        if (view.phase == GamePhase.BIDDING || view.phase == GamePhase.PLAYING) {
            TopActionChip(
                text = if (self.isAutoPlaying) "取消托管" else "托管",
                onClick = { viewModel.setAutoPlay(!self.isAutoPlaying) },
                width = if (self.isAutoPlaying) 80.dp else 58.dp,
                color = if (self.isAutoPlaying) RoseRed else LavenderDeep,
            )
            Spacer(Modifier.width(5.dp))
        }
        TopActionChip("退出", viewModel::leaveRoom, width = 54.dp, color = MutedInk)
    }
}

@Composable
private fun CenterPlayArea(
    view: PlayerGameView,
    viewModel: GameViewModel,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val showStatus = view.phase != GamePhase.PLAYING ||
            view.lastPlay == null ||
            !view.statusMessage.contains("出了")
        if (showStatus) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xB8FFFFFF))
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(view.statusMessage, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, color = Ink)
            }
            Spacer(Modifier.height(7.dp))
        }
        if (view.phase == GamePhase.PLAYING && view.lastPlay == null) {
            Text("等待领出", color = Ink.copy(alpha = 0.72f), fontSize = 12.sp)
            Spacer(Modifier.height(5.dp))
        }
        when (view.phase) {
            GamePhase.BIDDING -> BiddingControls(view, viewModel)
            GamePhase.PLAYING -> Unit
            GamePhase.FINISHED -> Unit
            else -> Unit
        }
    }
}

@Composable
private fun PositionedLastPlay(
    view: PlayerGameView,
    leftPlayerId: String?,
    rightPlayerId: String?,
    modifier: Modifier = Modifier,
) {
    val play = view.lastPlay ?: return
    val playerName = view.players.firstOrNull { it.id == play.playerId }?.name.orEmpty()
    Box(modifier) {
        val position = when (play.playerId) {
            leftPlayerId -> Modifier.align(Alignment.CenterStart).offset(x = 98.dp)
            rightPlayerId -> Modifier.align(Alignment.CenterEnd).offset(x = (-98).dp)
            else -> Modifier.align(Alignment.BottomCenter).offset(y = 26.dp)
        }
        Column(
            modifier = position.width(150.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.60f))
                    .padding(horizontal = 7.dp, vertical = 2.dp),
            ) {
                Text(
                    "$playerName · ${play.pattern.type.displayName}",
                    color = Ink,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.height(2.dp))
            PlayedCardGroup(play.cards)
        }
    }
}

@Composable
private fun BiddingControls(view: PlayerGameView, viewModel: GameViewModel) {
    val self = view.players.firstOrNull { it.id == view.selfPlayerId }
    val isTurn = view.currentTurnId == view.selfPlayerId && self?.isAutoPlaying != true
    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        (0..3).forEach { value ->
            val enabled = isTurn && (value == 0 || value > view.highestBid)
            val color = if (value == 0) MutedInk else PeachDeep
            Box(
                modifier = Modifier
                    .width(58.dp)
                    .height(38.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(Color.White.copy(alpha = if (enabled) 0.72f else 0.28f))
                    .border(1.5.dp, color.copy(alpha = if (enabled) 0.72f else 0.28f), RoundedCornerShape(15.dp))
                    .clickable(enabled = enabled) { viewModel.bid(value) },
                contentAlignment = Alignment.Center,
            ) {
                Text(if (value == 0) "不叫" else "$value 分", color = color.copy(alpha = if (enabled) 1f else 0.42f), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ResultPanel(
    view: PlayerGameView,
    self: PlayerSummary,
    viewModel: GameViewModel,
    modifier: Modifier = Modifier,
) {
    val result = view.result ?: return
    val won = result.winnerRole == self.role
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFDFBFA)),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                if (won) "本局胜利" else "本局结束",
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                color = if (won) PeachDeep else LavenderDeep,
            )
            Text("${if (result.spring) "春天 · " else ""}最终倍数 ×${result.multiplier}", color = MutedInk)
            Spacer(Modifier.height(6.dp))
            FreshButton(
                text = if (self.ready) "取消下一局" else "再来一局",
                onClick = { viewModel.setReady(!self.ready) },
                modifier = Modifier.width(170.dp),
            )
        }
    }
}

@Composable
private fun OpponentPanel(player: PlayerSummary?, currentTurnId: String?, accent: Color) {
    if (player == null) {
        Spacer(Modifier.width(112.dp))
        return
    }
    val isTurn = player.id == currentTurnId
    Column(
        modifier = Modifier
            .width(112.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(if (isTurn) Sunny.copy(alpha = 0.20f) else Color.Transparent)
            .border(if (isTurn) 2.dp else 0.dp, if (isTurn) Sunny else Color.Transparent, RoundedCornerShape(22.dp))
            .padding(horizontal = 5.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PastelAvatar(player.name, player.isBot, Modifier.size(52.dp), accent)
        Spacer(Modifier.height(3.dp))
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(13.dp))
                .background(Color.White.copy(alpha = 0.80f))
                .padding(horizontal = 7.dp, vertical = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(player.name, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(roleText(player.role), color = PeachDeep, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        CardBackStack(player.remainingCards)
        Text("积分 ${player.score}", color = Ink, fontSize = 10.sp)
        when {
            player.isBot -> StatusLabel("机器人", MintDeep)
            player.isAutoPlaying -> StatusLabel("托管中", LavenderDeep)
            !player.connected -> StatusLabel("暂时断线", RoseRed)
            isTurn -> StatusLabel("轮到这里", PeachDeep)
        }
    }
}

@Composable
private fun SelfPanel(player: PlayerSummary, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        PastelAvatar(player.name, player.isBot, Modifier.size(50.dp), Peach)
        Spacer(Modifier.height(3.dp))
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.82f))
                .padding(horizontal = 8.dp, vertical = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(player.name, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${roleText(player.role)} · ${player.score}分", color = MutedInk, fontSize = 10.sp)
        }
        if (player.isAutoPlaying) StatusLabel("托管中", LavenderDeep)
    }
}

@Composable
private fun TopActionChip(
    text: String,
    onClick: () -> Unit,
    width: androidx.compose.ui.unit.Dp,
    color: Color,
) {
    Box(
        modifier = Modifier
            .width(width)
            .height(38.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.76f))
            .border(1.5.dp, color.copy(alpha = 0.58f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.Black, maxLines = 1)
    }
}

@Composable
private fun StatusLabel(text: String, color: Color) {
    Text(
        text,
        color = color,
        fontSize = 10.sp,
        fontWeight = FontWeight.Black,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}

@Composable
private fun ConnectionBadge(state: ConnectionState) {
    val (text, color) = when (state) {
        ConnectionState.CONNECTED -> "已连接" to MintDeep
        ConnectionState.CONNECTING -> "连接中" to PeachDeep
        ConnectionState.RECONNECTING -> "正在重连" to PeachDeep
        ConnectionState.FAILED -> "连接失败" to RoseRed
        ConnectionState.DISCONNECTED -> "未连接" to MutedInk
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xDFFFFFFF))
            .padding(horizontal = 9.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(5.dp))
        Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Black)
    }
}

private fun roleText(role: PlayerRole): String = when (role) {
    PlayerRole.LANDLORD -> "地主"
    PlayerRole.FARMER -> "农民"
    PlayerRole.UNKNOWN -> "等待身份"
}
