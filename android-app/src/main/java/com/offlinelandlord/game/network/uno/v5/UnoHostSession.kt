package com.offlinelandlord.game.network.uno.v5

import com.offlinelandlord.game.uno.bot.UnoBot
import com.offlinelandlord.game.uno.core.UnoAction
import com.offlinelandlord.game.uno.core.UnoEngine
import com.offlinelandlord.game.uno.core.UnoMatchMode
import com.offlinelandlord.game.uno.core.UnoPhase
import com.offlinelandlord.game.uno.core.UnoPlayer
import java.io.Closeable
import java.util.UUID
import kotlin.random.Random
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class UnoSessionResult<T>(
    val success: Boolean,
    val value: T? = null,
    val error: UnoV5ErrorCode? = null,
    val detail: String? = null,
)

/**
 * Transport-independent UNO authority. Every mutation is serialized by [mutex]; a TCP adapter
 * can therefore call this class from any connection coroutine without racing the engine.
 */
class UnoHostSession(
    val roomCode: String = randomRoomCode(),
    hostName: String,
    config: UnoV5RoomConfig = UnoV5RoomConfig(),
    private val random: Random = Random.Default,
) : Closeable {
    private data class Player(
        val playerId: String,
        var seatIndex: Int,
        val displayName: String,
        val resumeToken: String,
        var connected: Boolean,
        var ready: Boolean,
        val isBot: Boolean,
    )

    private val mutex = Mutex()
    private val players = mutableListOf<Player>()
    private val requests = mutableMapOf<Pair<String, String>, UnoSessionResult<UnoV5RoomView>>()
    private val config: UnoV5RoomConfig = config.also {
        require(it.maxPlayers in 2..4) { "UNO room maxPlayers must be 2..4" }
    }
    private val hostPlayer: Player = Player(
        playerId = newId("player"),
        seatIndex = 0,
        displayName = hostName.trim().ifBlank { "房主" },
        resumeToken = newToken(),
        connected = true,
        ready = false,
        isBot = false,
    )
    private var engine: UnoEngine? = null
    private var revision: Long = 0
    private var closed = false

    init {
        players += hostPlayer
    }

    val hostPlayerId: String get() = hostPlayer.playerId
    val hostResumeToken: String get() = hostPlayer.resumeToken
    val maxPlayers: Int get() = config.maxPlayers

    suspend fun join(displayName: String, requestId: String = newId("join")): UnoSessionResult<UnoV5JoinAcceptedPayload> = mutex.withLock {
        if (closed) return@withLock failure(UnoV5ErrorCode.ROOM_NOT_FOUND)
        if (engine != null) return@withLock failure(UnoV5ErrorCode.GAME_ALREADY_STARTED)
        if (players.size >= config.maxPlayers) return@withLock failure(UnoV5ErrorCode.ROOM_FULL)
        val player = Player(newId("player"), players.size, displayName.trim().ifBlank { "玩家" }, newToken(), true, false, false)
        players += player
        revision++
        UnoSessionResult(true, UnoV5JoinAcceptedPayload(player.playerId, player.resumeToken, roomFor(player.playerId)))
    }

    suspend fun reconnect(playerId: String, resumeToken: String): UnoSessionResult<UnoV5JoinAcceptedPayload> = mutex.withLock {
        val player = players.firstOrNull { it.playerId == playerId }
            ?: return@withLock failure(UnoV5ErrorCode.PLAYER_NOT_FOUND)
        if (player.resumeToken != resumeToken) return@withLock failure(UnoV5ErrorCode.INVALID_RESUME_TOKEN)
        player.connected = true
        revision++
        UnoSessionResult(true, UnoV5JoinAcceptedPayload(player.playerId, player.resumeToken, roomFor(player.playerId)))
    }

    suspend fun ready(playerId: String, requestId: String = newId("ready")) = roomAction(playerId, requestId, UnoV5ActionType.READY)
    suspend fun unready(playerId: String, requestId: String = newId("unready")) = roomAction(playerId, requestId, UnoV5ActionType.UNREADY)

    suspend fun addBot(requesterId: String, displayName: String = "机器人") = mutex.withLock {
        if (requesterId != hostPlayerId) return@withLock failure(UnoV5ErrorCode.NOT_HOST)
        if (engine != null) return@withLock failure(UnoV5ErrorCode.GAME_ALREADY_STARTED)
        if (players.size >= config.maxPlayers) return@withLock failure(UnoV5ErrorCode.ROOM_FULL)
        players += Player(newId("bot"), players.size, displayName, newToken(), true, true, true)
        revision++
        success(roomFor(requesterId))
    }

    suspend fun removeBot(requesterId: String, botId: String) = mutex.withLock {
        if (requesterId != hostPlayerId) return@withLock failure(UnoV5ErrorCode.NOT_HOST)
        if (engine != null) return@withLock failure(UnoV5ErrorCode.GAME_ALREADY_STARTED)
        val bot = players.firstOrNull { it.playerId == botId && it.isBot }
            ?: return@withLock failure(UnoV5ErrorCode.PLAYER_NOT_FOUND)
        players.remove(bot)
        reseatWaitingPlayers()
        revision++
        success(roomFor(requesterId))
    }

    suspend fun startGame(requesterId: String, requestId: String = newId("start")) = mutex.withLock {
        if (requesterId != hostPlayerId) return@withLock failure(UnoV5ErrorCode.NOT_HOST)
        if (engine != null) return@withLock failure(UnoV5ErrorCode.GAME_ALREADY_STARTED)
        if (players.size != config.maxPlayers) return@withLock failure(UnoV5ErrorCode.NOT_ENOUGH_PLAYERS)
        if (players.filterNot { it.isBot }.any { !it.ready }) return@withLock failure(UnoV5ErrorCode.NOT_READY)
        val result = UnoEngine.start(
            players = players.sortedBy { it.seatIndex }.map { UnoPlayer(it.playerId, it.displayName) },
            random = random,
            matchMode = if (config.gameMode == UnoV5GameMode.POINTS_500) UnoMatchMode.POINTS else UnoMatchMode.QUICK,
            targetScore = 500,
        )
        engine = result.engine ?: return@withLock failure(UnoV5ErrorCode.INVALID_CONFIG, result.error?.message)
        revision++
        drainBotsLocked()
        success(roomFor(requesterId))
    }

    suspend fun submitAction(
        playerId: String,
        requestId: String,
        expectedRevision: Long?,
        payload: UnoV5ActionPayload,
    ): UnoSessionResult<UnoV5RoomView> = mutex.withLock {
        val key = playerId to requestId
        requests[key]?.let { return@withLock it }
        val player = players.firstOrNull { it.playerId == playerId }
            ?: return@withLock remember(key, failure(UnoV5ErrorCode.PLAYER_NOT_FOUND))
        if (!player.connected && !player.isBot) return@withLock remember(key, failure(UnoV5ErrorCode.PLAYER_NOT_FOUND))
        if (expectedRevision != null && expectedRevision != revision) {
            return@withLock remember(key, UnoSessionResult(false, roomFor(playerId), UnoV5ErrorCode.STALE_REVISION, "latestRevision=$revision"))
        }
        val result = when (payload.action) {
            UnoV5ActionType.READY -> setReady(player, true)
            UnoV5ActionType.UNREADY -> setReady(player, false)
            UnoV5ActionType.START_GAME -> if (playerId == hostPlayerId) startGameLocked(playerId) else failure(UnoV5ErrorCode.NOT_HOST)
            UnoV5ActionType.ADD_BOT,
            UnoV5ActionType.REMOVE_BOT,
            -> failure(UnoV5ErrorCode.ILLEGAL_ACTION, "Use the room management API")
            else -> applyEngineAction(player, payload)
        }
        if (result.success) {
            revision++
            drainBotsLocked()
        }
        remember(key, result.map { roomFor(playerId) })
    }

    suspend fun disconnect(playerId: String) = mutex.withLock {
        val player = players.firstOrNull { it.playerId == playerId } ?: return@withLock failure(UnoV5ErrorCode.PLAYER_NOT_FOUND)
        if (!player.isBot) player.connected = false
        if (engine != null && engine?.state?.currentPlayerId == playerId) drainBotsLocked()
        revision++
        success(roomFor(playerId))
    }

    suspend fun viewFor(playerId: String): UnoV5RoomView? = mutex.withLock {
        if (players.none { it.playerId == playerId }) null else roomFor(playerId)
    }

    suspend fun currentRevision(): Long = mutex.withLock { revision }

    private fun startGameLocked(requesterId: String): UnoSessionResult<UnoV5RoomView> {
        if (engine != null) return failure(UnoV5ErrorCode.GAME_ALREADY_STARTED)
        if (players.size != config.maxPlayers) return failure(UnoV5ErrorCode.NOT_ENOUGH_PLAYERS)
        if (players.filterNot { it.isBot }.any { !it.ready }) return failure(UnoV5ErrorCode.NOT_READY)
        val start = UnoEngine.start(
            players.sortedBy { it.seatIndex }.map { UnoPlayer(it.playerId, it.displayName) }, random,
            if (config.gameMode == UnoV5GameMode.POINTS_500) UnoMatchMode.POINTS else UnoMatchMode.QUICK, 500,
        )
        engine = start.engine ?: return failure(UnoV5ErrorCode.INVALID_CONFIG, start.error?.message)
        return success(roomFor(requesterId))
    }

    private fun applyEngineAction(player: Player, payload: UnoV5ActionPayload): UnoSessionResult<UnoV5RoomView> {
        val activeEngine = engine ?: return failure(UnoV5ErrorCode.GAME_ALREADY_STARTED, "Game has not started")
        val action = UnoV5PayloadCodec.action(payload) ?: return failure(UnoV5ErrorCode.INVALID_ACTION)
        val applied = activeEngine.applyAction(player.playerId, action)
        if (!applied.success) {
            val code = when (applied.error?.code?.name) {
                "NOT_YOUR_TURN" -> UnoV5ErrorCode.NOT_YOUR_TURN
                else -> UnoV5ErrorCode.ILLEGAL_ACTION
            }
            return failure(code, applied.error?.message)
        }
        return success(roomFor(player.playerId))
    }

    private fun drainBotsLocked() {
        val activeEngine = engine ?: return
        repeat(128) {
            val current = activeEngine.state.currentPlayerId ?: return
            val player = players.firstOrNull { it.playerId == current } ?: return
            // An online human seat is never host-controlled. It must wait for an explicit V5 ACTION.
            if (!shouldHostAutoControlUnoSeat(player.isBot, player.connected)) return
            val action = UnoBot(player.playerId, random).chooseAction(activeEngine) ?: return
            val result = activeEngine.applyAction(player.playerId, action)
            if (!result.success) return
            revision++
        }
    }

    private suspend fun roomAction(playerId: String, requestId: String, action: UnoV5ActionType): UnoSessionResult<UnoV5RoomView> =
        submitAction(playerId, requestId, null, UnoV5ActionPayload(action))

    private fun setReady(player: Player, ready: Boolean): UnoSessionResult<UnoV5RoomView> {
        if (engine != null) return failure(UnoV5ErrorCode.GAME_ALREADY_STARTED)
        player.ready = ready
        return success(roomFor(player.playerId))
    }

    private fun remember(key: Pair<String, String>, result: UnoSessionResult<UnoV5RoomView>): UnoSessionResult<UnoV5RoomView> {
        requests[key] = result
        if (requests.size > 4096) requests.entries.iterator().next().also { requests.remove(it.key) }
        return result
    }

    private fun roomFor(playerId: String): UnoV5RoomView {
        val gameView = engine?.viewFor(playerId)?.let { view ->
            UnoV5GameView(
                selfPlayerId = view.selfPlayerId,
                ownHand = view.ownHand.map(UnoV5PayloadCodec::card),
                players = players.sortedBy { it.seatIndex }.map { playerView(it, view) },
                topDiscard = UnoV5PayloadCodec.card(view.topDiscardCard),
                drawPileCount = view.drawPileCount,
                activeColor = view.activeColor?.name,
                currentPlayerId = view.currentPlayerId,
                direction = view.direction.name,
                phase = view.phase.name,
                roundNumber = view.roundNumber,
                scores = view.scores,
                unoDeclaredPlayerId = view.unoDeclaredPlayerId,
                catchTargetPlayerId = view.catchTargetPlayerId,
                drawnCardId = view.drawnCardId,
                colorChooserPlayerId = view.colorChooserPlayerId,
                roundWinnerId = view.roundWinnerId,
                matchWinnerId = view.matchWinnerId,
                legalActions = activeLegalActions(view.selfPlayerId),
                legalPlayableCardIds = engine?.legalPlayableCards(view.selfPlayerId)?.map { it.cardId }.orEmpty(),
            )
        }
        val status = when (viewGamePhase()) {
            UnoPhase.ROUND_FINISHED -> UnoV5RoomStatus.ROUND_FINISHED
            UnoPhase.MATCH_FINISHED -> UnoV5RoomStatus.MATCH_FINISHED
            null -> UnoV5RoomStatus.WAITING
            else -> UnoV5RoomStatus.PLAYING
        }
        return UnoV5RoomView(roomCode, hostPlayerId, players.sortedBy { it.seatIndex }.map { p -> playerView(p, engine?.viewFor(playerId)) }, config.gameMode, config.maxPlayers, status, revision, gameView)
    }

    private fun playerView(player: Player, view: com.offlinelandlord.game.uno.core.UnoGameView?): UnoV5PlayerView {
        val own = player.playerId == view?.selfPlayerId
        val state = view?.players?.firstOrNull { it.playerId == player.playerId }
        return UnoV5PlayerView(player.playerId, player.seatIndex, player.displayName, player.connected, player.ready, player.isBot, if (own) view.ownHand.map(UnoV5PayloadCodec::card) else emptyList(), state?.remainingCardCount ?: 0, state?.score ?: 0)
    }

    private fun activeLegalActions(playerId: String): Set<UnoV5ActionType> = engine?.availableActions(playerId).orEmpty().mapNotNull {
        when (it.name) {
            "PLAY_CARD" -> UnoV5ActionType.PLAY_CARD
            "DRAW_CARD" -> UnoV5ActionType.DRAW_CARD
            "PLAY_DRAWN_CARD" -> UnoV5ActionType.PLAY_DRAWN_CARD
            "PASS_AFTER_DRAW" -> UnoV5ActionType.PASS_AFTER_DRAW
            "DECLARE_UNO" -> UnoV5ActionType.DECLARE_UNO
            "CATCH_UNO" -> UnoV5ActionType.CATCH_UNO
            "CHOOSE_COLOR" -> UnoV5ActionType.CHOOSE_COLOR
            "START_NEXT_ROUND" -> UnoV5ActionType.START_NEXT_ROUND
            else -> null
        }
    }.toSet()

    private fun viewGamePhase(): UnoPhase? = engine?.state?.phase
    private fun reseatWaitingPlayers() { players.forEachIndexed { index, player -> player.seatIndex = index } }
    private fun newId(prefix: String) = "$prefix-${UUID.randomUUID()}"
    private fun newToken() = UUID.randomUUID().toString()
    private fun <T> success(value: T): UnoSessionResult<T> = UnoSessionResult(true, value)
    private fun <T> failure(code: UnoV5ErrorCode, detail: String? = null): UnoSessionResult<T> = UnoSessionResult(false, error = code, detail = detail)
    private fun <T> UnoSessionResult<T>.map(transform: (T) -> T): UnoSessionResult<T> = if (success && value != null) copy(value = transform(value)) else this

    override fun close() { closed = true }

    companion object {
        fun randomRoomCode(): String = Random.nextInt(100000, 1000000).toString()
    }
}

/** Connected human seats always wait for a deliberate network action. */
internal fun shouldHostAutoControlUnoSeat(isBot: Boolean, connected: Boolean): Boolean = isBot || !connected
