package com.offlinelandlord.game.core

import java.util.UUID
import kotlin.math.max
import kotlin.random.Random

class GameEngine(
    val roomCode: String,
    val roomName: String,
    hostName: String,
    private val random: Random = Random.Default,
    private val totalRounds: Int = 12,
    private val doublingEnabled: Boolean = true,
) {
    private data class PlayerState(
        val id: String,
        val resumeToken: String,
        var name: String,
        val seat: Int,
        val isBot: Boolean = false,
        var ready: Boolean = false,
        var connected: Boolean = true,
        var autoPlaying: Boolean = false,
        var role: PlayerRole = PlayerRole.UNKNOWN,
        val hand: MutableList<Card> = mutableListOf(),
        var score: Int = 0,
        var bid: Int? = null,
        var doubleChoice: Boolean? = null,
        var successfulPlayCount: Int = 0,
    )

    private val players = mutableListOf<PlayerState>()
    private var phase = GamePhase.WAITING
    private var bottomCards = emptyList<Card>()
    private var landlordId: String? = null
    private var currentTurnId: String? = null
    private var lastPlay: PublicPlay? = null
    private var passCount = 0
    private var bidCount = 0
    private var highestBid = 0
    private var highestBidderId: String? = null
    private var startingBidderSeat = 0
    private var firstBidderId = ""
    private var multiplier = 1
    private var completedRounds = 0
    private var result: RoundResult? = null
    private val roundHistory = mutableListOf<RoundRecord>()
    private var revision = 0L
    private var statusMessage = "等待三名玩家加入"

    val hostPlayerId: String

    init {
        require(totalRounds == 12 || totalRounds == 24) { "局数只能是 12 或 24" }
        val host = createPlayer(hostName.ifBlank { "房主" }, seat = 0)
        players += host
        hostPlayerId = host.id
        revision++
    }

    @Synchronized
    fun join(name: String, resumeToken: String? = null): JoinOutcome {
        val normalizedName = name.trim().take(12).ifBlank { "玩家" }
        if (resumeToken != null) {
            val existing = players.firstOrNull { it.resumeToken == resumeToken }
            if (existing != null) {
                existing.connected = true
                existing.autoPlaying = false
                existing.name = normalizedName
                statusMessage = "${existing.name} 已重新连接"
                revision++
                return JoinOutcome(true, existing.id, existing.resumeToken, statusMessage)
            }
        }

        if (phase != GamePhase.WAITING || players.size >= 3) {
            return JoinOutcome(false, message = "房间已满或牌局已经开始")
        }

        val freeSeat = (0..2).firstOrNull { seat -> players.none { it.seat == seat } }
            ?: return JoinOutcome(false, message = "房间已满")
        val player = createPlayer(normalizedName, freeSeat)
        players += player
        statusMessage = "${player.name} 加入房间"
        revision++
        return JoinOutcome(true, player.id, player.resumeToken, statusMessage)
    }

    @Synchronized
    fun disconnect(playerId: String) {
        players.firstOrNull { it.id == playerId }?.let {
            it.connected = false
            statusMessage = "${it.name} 连接中断，正在保留座位"
            revision++
        }
    }

    @Synchronized
    fun applyAction(playerId: String, action: PlayerAction, expectedRevision: Long? = null): ActionResult {
        if (expectedRevision != null && expectedRevision != revision) {
            return ActionResult.error("牌局状态已更新，请重试")
        }
        val player = players.firstOrNull { it.id == playerId }
            ?: return ActionResult.error("玩家不存在")

        return when (action.type) {
            ActionType.SET_READY -> setReady(player, action.ready ?: true)
            ActionType.BID -> bid(player, action.bid ?: -1)
            ActionType.DOUBLE -> chooseDouble(player, action.doubleChoice ?: false)
            ActionType.PLAY -> play(player, action.cardIds)
            ActionType.PASS -> pass(player)
            ActionType.ADD_BOT -> addBot(player)
            ActionType.REMOVE_BOT -> removeBot(player, action.targetPlayerId)
            ActionType.SET_AUTOPLAY -> setAutoPlay(player, action.autoPlay ?: true)
        }
    }

    @Synchronized
    fun viewFor(playerId: String): PlayerGameView? {
        val self = players.firstOrNull { it.id == playerId } ?: return null
        val revealRoles = phase == GamePhase.DOUBLING || phase == GamePhase.PLAYING || phase == GamePhase.FINISHED
        val currentRound = when {
            completedRounds >= totalRounds -> totalRounds
            phase == GamePhase.FINISHED -> completedRounds.coerceAtLeast(1)
            else -> (completedRounds + 1).coerceAtMost(totalRounds)
        }
        return PlayerGameView(
            roomCode = roomCode,
            roomName = roomName,
            selfPlayerId = self.id,
            hostPlayerId = hostPlayerId,
            phase = phase,
            players = players.sortedBy { it.seat }.map { player ->
                PlayerSummary(
                    id = player.id,
                    name = player.name,
                    seat = player.seat,
                    ready = player.ready,
                    connected = player.connected,
                    role = if (revealRoles) player.role else PlayerRole.UNKNOWN,
                    remainingCards = player.hand.size,
                    score = player.score,
                    bid = player.bid,
                    doubleChoice = player.doubleChoice,
                    isBot = player.isBot,
                    isAutoPlaying = player.autoPlaying,
                )
            },
            ownHand = self.hand.sortedWith(cardComparator),
            bottomCards = if (revealRoles) bottomCards else emptyList(),
            landlordId = if (revealRoles) landlordId else null,
            currentTurnId = currentTurnId,
            lastPlay = lastPlay,
            highestBid = highestBid,
            multiplier = multiplier,
            totalRounds = totalRounds,
            currentRound = currentRound,
            completedRounds = completedRounds,
            doublingEnabled = doublingEnabled,
            matchComplete = completedRounds >= totalRounds,
            result = result,
            roundHistory = roundHistory.toList(),
            revision = revision,
            statusMessage = statusMessage,
        )
    }

    @Synchronized
    fun playerIds(): List<String> = players.map { it.id }

    @Synchronized
    fun automatedPlayerId(): String? {
        if (phase != GamePhase.BIDDING && phase != GamePhase.DOUBLING && phase != GamePhase.PLAYING) return null
        val current = players.firstOrNull { it.id == currentTurnId } ?: return null
        return current.id.takeIf { current.isBot || current.autoPlaying }
    }

    @Synchronized
    fun enableAutoPlay(playerId: String): ActionResult {
        val player = players.firstOrNull { it.id == playerId }
            ?: return ActionResult.error("玩家不存在")
        if (player.isBot) return ActionResult.ok()
        if (player.connected) return ActionResult.error("玩家已经重新连接")
        player.autoPlaying = true
        if (phase == GamePhase.WAITING || (phase == GamePhase.FINISHED && completedRounds < totalRounds)) {
            player.ready = true
        }
        statusMessage = "${player.name} 已断线，机器人开始代打"
        revision++
        return ActionResult.ok(statusMessage)
    }

    private fun setReady(player: PlayerState, ready: Boolean): ActionResult {
        if (phase != GamePhase.WAITING && phase != GamePhase.FINISHED) {
            return ActionResult.error("牌局进行中，不能修改准备状态")
        }
        if (completedRounds >= totalRounds) return ActionResult.error("本场 $totalRounds 局已经完成")
        player.ready = ready
        statusMessage = if (ready) "${player.name} 已准备" else "${player.name} 取消准备"
        revision++

        startRoundIfReady()
        return ActionResult.ok(statusMessage)
    }

    private fun addBot(requester: PlayerState): ActionResult {
        if (requester.id != hostPlayerId) return ActionResult.error("只有房主可以添加机器人")
        if (phase != GamePhase.WAITING) return ActionResult.error("只能在等待房间中添加机器人")
        if (players.size >= 3) return ActionResult.error("房间已经满了")
        val freeSeat = (0..2).first { seat -> players.none { it.seat == seat } }
        val botNumber = players.count { it.isBot } + 1
        val bot = createPlayer("机器人$botNumber", freeSeat, isBot = true).apply {
            ready = true
            autoPlaying = true
        }
        players += bot
        statusMessage = "${bot.name} 已加入并准备"
        revision++
        startRoundIfReady()
        return ActionResult.ok(statusMessage)
    }

    private fun removeBot(requester: PlayerState, targetPlayerId: String?): ActionResult {
        if (requester.id != hostPlayerId) return ActionResult.error("只有房主可以移除机器人")
        if (phase != GamePhase.WAITING) return ActionResult.error("只能在等待房间中移除机器人")
        val bot = if (targetPlayerId == null) {
            players.filter { it.isBot }.maxByOrNull { it.seat }
        } else {
            players.firstOrNull { it.id == targetPlayerId && it.isBot }
        } ?: return ActionResult.error("房间中没有可移除的机器人")
        players.remove(bot)
        statusMessage = "${bot.name} 已离开房间"
        revision++
        return ActionResult.ok(statusMessage)
    }

    private fun setAutoPlay(player: PlayerState, enabled: Boolean): ActionResult {
        if (player.isBot) return ActionResult.error("机器人座位始终由机器人控制")
        player.autoPlaying = enabled
        statusMessage = if (enabled) {
            "${player.name} 开启托管"
        } else {
            "${player.name} 取消托管"
        }
        revision++
        return ActionResult.ok(statusMessage)
    }

    private fun startRoundIfReady() {
        if (completedRounds >= totalRounds) return
        if (
            players.size == 3 &&
            players.all { it.ready && (it.connected || it.isBot || it.autoPlaying) }
        ) {
            startRound()
        }
    }

    private fun startRound() {
        players.forEach {
            it.ready = false
            it.role = PlayerRole.UNKNOWN
            it.hand.clear()
            it.bid = null
            it.doubleChoice = null
            it.successfulPlayCount = 0
        }

        startingBidderSeat = random.nextInt(3)
        firstBidderId = playerAtSeat(startingBidderSeat).id
        val deck = DeckFactory.shuffledDeck(random)
        players.sortedBy { it.seat }.forEachIndexed { index, player ->
            player.hand += deck.subList(index * 17, (index + 1) * 17)
            player.hand.sortWith(cardComparator)
        }
        bottomCards = deck.takeLast(3)
        landlordId = null
        lastPlay = null
        passCount = 0
        bidCount = 0
        highestBid = 0
        highestBidderId = null
        multiplier = 1
        result = null
        phase = GamePhase.BIDDING
        currentTurnId = firstBidderId
        statusMessage = "${playerAtSeat(startingBidderSeat).name} 随机先叫"
        revision++
    }

    private fun bid(player: PlayerState, value: Int): ActionResult {
        if (phase != GamePhase.BIDDING) return ActionResult.error("当前不是叫分阶段")
        if (currentTurnId != player.id) return ActionResult.error("还没有轮到你叫分")
        if (value !in 0..3) return ActionResult.error("叫分只能是 0、1、2 或 3")
        if (value != 0 && value <= highestBid) return ActionResult.error("叫分必须高于当前最高分")

        player.bid = value
        bidCount++
        if (value > highestBid) {
            highestBid = value
            highestBidderId = player.id
        }
        statusMessage = if (value == 0) "${player.name} 不叫" else "${player.name} 叫 $value 分"
        revision++

        if (value == 3 || bidCount == 3) {
            if (highestBidderId == null) {
                statusMessage = "三人都不叫，重新发牌"
                startRound()
            } else {
                settleLandlord()
            }
        } else {
            currentTurnId = nextPlayer(player.id).id
        }
        return ActionResult.ok(statusMessage)
    }

    private fun settleLandlord() {
        val landlord = players.single { it.id == highestBidderId }
        landlordId = landlord.id
        players.forEach { it.role = if (it.id == landlord.id) PlayerRole.LANDLORD else PlayerRole.FARMER }
        landlord.hand += bottomCards
        landlord.hand.sortWith(cardComparator)
        multiplier = max(1, highestBid)
        currentTurnId = landlord.id
        phase = if (doublingEnabled) GamePhase.DOUBLING else GamePhase.PLAYING
        statusMessage = if (doublingEnabled) {
            "${landlord.name} 成为地主，请选择是否加倍"
        } else {
            "${landlord.name} 成为地主"
        }
        revision++
    }

    private fun chooseDouble(player: PlayerState, value: Boolean): ActionResult {
        if (phase != GamePhase.DOUBLING) return ActionResult.error("当前不是加倍阶段")
        if (currentTurnId != player.id) return ActionResult.error("还没有轮到你选择加倍")
        if (player.doubleChoice != null) return ActionResult.error("你已经选择过是否加倍")

        player.doubleChoice = value
        if (value) multiplier *= 2
        statusMessage = if (value) "${player.name} 选择加倍" else "${player.name} 选择不加倍"
        revision++

        if (players.all { it.doubleChoice != null }) {
            phase = GamePhase.PLAYING
            currentTurnId = landlordId
            statusMessage = "加倍完成，地主领出"
        } else {
            currentTurnId = nextPlayer(player.id).id
        }
        return ActionResult.ok(statusMessage)
    }

    private fun play(player: PlayerState, cardIds: List<String>): ActionResult {
        if (phase != GamePhase.PLAYING) return ActionResult.error("当前不能出牌")
        if (currentTurnId != player.id) return ActionResult.error("还没有轮到你出牌")
        if (cardIds.isEmpty() || cardIds.size != cardIds.distinct().size) {
            return ActionResult.error("请选择要出的牌")
        }

        val selected = cardIds.map { id -> player.hand.firstOrNull { it.id == id } }
        if (selected.any { it == null }) return ActionResult.error("所选牌不在你的手牌中")
        val cards = selected.filterNotNull()
        val pattern = HandRules.analyze(cards) ?: return ActionResult.error("这组牌不是合法牌型")
        val previous = lastPlay
        if (previous != null && !HandRules.beats(pattern, previous.pattern)) {
            return ActionResult.error("所选牌不能压过上一手")
        }

        val selectedIds = cardIds.toSet()
        player.hand.removeAll { it.id in selectedIds }
        player.successfulPlayCount++
        lastPlay = PublicPlay(player.id, cards.sortedWith(cardComparator), pattern)
        passCount = 0
        if (pattern.type == PatternType.BOMB || pattern.type == PatternType.ROCKET) multiplier *= 2
        statusMessage = "${player.name} 出了${pattern.type.displayName}"
        revision++

        if (player.hand.isEmpty()) {
            finishRound(player)
        } else {
            currentTurnId = nextPlayer(player.id).id
        }
        return ActionResult.ok(statusMessage)
    }

    private fun pass(player: PlayerState): ActionResult {
        if (phase != GamePhase.PLAYING) return ActionResult.error("当前不能不出")
        if (currentTurnId != player.id) return ActionResult.error("还没有轮到你")
        val previous = lastPlay ?: return ActionResult.error("你是本轮领出玩家，不能不出")

        passCount++
        statusMessage = "${player.name} 不出"
        revision++
        if (passCount >= 2) {
            currentTurnId = previous.playerId
            lastPlay = null
            passCount = 0
            statusMessage = "两家不出，重新领牌"
        } else {
            currentTurnId = nextPlayer(player.id).id
        }
        return ActionResult.ok(statusMessage)
    }

    private fun finishRound(winner: PlayerState) {
        val winnerRole = if (winner.id == landlordId) PlayerRole.LANDLORD else PlayerRole.FARMER
        val landlord = players.single { it.id == landlordId }
        val spring = when (winnerRole) {
            PlayerRole.LANDLORD -> players.filter { it.role == PlayerRole.FARMER }.all { it.successfulPlayCount == 0 }
            PlayerRole.FARMER -> landlord.successfulPlayCount == 1
            PlayerRole.UNKNOWN -> false
        }
        if (spring) multiplier *= 2

        val changes = players.associate { player ->
            val change = if (winnerRole == PlayerRole.LANDLORD) {
                if (player.role == PlayerRole.LANDLORD) multiplier * 2 else -multiplier
            } else {
                if (player.role == PlayerRole.LANDLORD) -multiplier * 2 else multiplier
            }
            player.score += change
            player.id to change
        }

        result = RoundResult(winnerRole, winner.id, multiplier, spring, changes)
        completedRounds++
        roundHistory += RoundRecord(
            roundNumber = completedRounds,
            firstBidderId = firstBidderId,
            landlordId = landlord.id,
            winnerRole = winnerRole,
            winnerPlayerId = winner.id,
            multiplier = multiplier,
            spring = spring,
            scoreChanges = changes,
            totalScores = players.associate { it.id to it.score },
        )
        phase = GamePhase.FINISHED
        currentTurnId = null
        val matchComplete = completedRounds >= totalRounds
        players.forEach { it.ready = !matchComplete && (it.isBot || it.autoPlaying) }
        statusMessage = if (matchComplete) {
            "$totalRounds 局已完成"
        } else if (winnerRole == PlayerRole.LANDLORD) {
            "地主获胜"
        } else {
            "农民获胜"
        }
        revision++
    }

    private fun nextPlayer(playerId: String): PlayerState {
        val seat = players.single { it.id == playerId }.seat
        return playerAtSeat((seat + 1) % 3)
    }

    private fun playerAtSeat(seat: Int): PlayerState = players.single { it.seat == seat }

    private fun createPlayer(name: String, seat: Int, isBot: Boolean = false): PlayerState = PlayerState(
        id = UUID.randomUUID().toString(),
        resumeToken = UUID.randomUUID().toString(),
        name = name,
        seat = seat,
        isBot = isBot,
    )
}
