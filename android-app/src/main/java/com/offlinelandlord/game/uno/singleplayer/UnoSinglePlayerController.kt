package com.offlinelandlord.game.uno.singleplayer

import com.offlinelandlord.game.uno.bot.UnoBot
import com.offlinelandlord.game.uno.core.UnoAction
import com.offlinelandlord.game.uno.core.UnoActionResult
import com.offlinelandlord.game.uno.core.UnoActionType
import com.offlinelandlord.game.uno.core.UnoColor
import com.offlinelandlord.game.uno.core.UnoEngine
import com.offlinelandlord.game.uno.core.UnoErrorCode
import com.offlinelandlord.game.uno.core.UnoPhase
import com.offlinelandlord.game.uno.core.UnoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random

fun interface UnoBotDelayProvider {
    suspend fun awaitDelay()

    companion object {
        val Immediate = UnoBotDelayProvider { }
        fun fixed(milliseconds: Long): UnoBotDelayProvider = UnoBotDelayProvider {
            if (milliseconds > 0) delay(milliseconds)
        }
    }
}

fun interface UnoSinglePlayerEngineFactory {
    fun create(config: UnoSinglePlayerConfig, random: Random): UnoEngine
}

class UnoSinglePlayerController(
    private val scope: CoroutineScope,
    private val botDelayProvider: UnoBotDelayProvider = UnoBotDelayProvider.fixed(450),
    private val engineFactory: UnoSinglePlayerEngineFactory = UnoSinglePlayerEngineFactory { config, random ->
        val players = (0 until config.playerCount).map { seat ->
            UnoPlayer(
                playerId = "p$seat",
                playerName = if (seat == 0) "你" else "机器人$seat",
            )
        }
        requireNotNull(
            UnoEngine.start(
                players = players,
                random = random,
                matchMode = config.matchMode,
                targetScore = 500,
            ).engine,
        )
    },
) {
    private val _uiState = MutableStateFlow(UnoUiState())
    val uiState: StateFlow<UnoUiState> = _uiState.asStateFlow()

    private val actionMutex = Mutex()
    private val jobLock = Any()
    private var actionChainJob: Job? = null
    private var engine: UnoEngine? = null
    private var botsById: Map<String, UnoBot> = emptyMap()
    private var currentConfig = UnoSinglePlayerConfig()
    private var eventMessage: String? = null
    private var errorMessage: String? = null

    fun startGame(config: UnoSinglePlayerConfig, random: Random = Random.Default) {
        synchronized(jobLock) {
            actionChainJob?.cancel()
            actionChainJob = null
        }
        currentConfig = config
        engine = engineFactory.create(config, random)
        botsById = (1 until config.playerCount).associate { seat ->
            val id = "p$seat"
            id to UnoBot(id, Random(random.nextInt()))
        }
        eventMessage = null
        errorMessage = null
        refresh(isActionInProgress = false, isBotThinking = false)
        scheduleBotChainIfNeeded()
    }

    fun clearGame() {
        synchronized(jobLock) {
            actionChainJob?.cancel()
            actionChainJob = null
        }
        engine = null
        botsById = emptyMap()
        eventMessage = null
        errorMessage = null
        _uiState.value = UnoUiState()
    }

    fun restartMatch(random: Random = Random.Default) = startGame(currentConfig, random)

    fun playCard(cardId: String): Boolean {
        val action = if (_uiState.value.phase == UnoPhase.AFTER_DRAW) {
            UnoAction.PlayDrawnCard(cardId)
        } else {
            UnoAction.PlayCard(cardId)
        }
        return submitHumanAction(action)
    }

    fun drawCard(): Boolean = submitHumanAction(UnoAction.DrawCard)
    fun passAfterDraw(): Boolean = submitHumanAction(UnoAction.PassAfterDraw)
    fun declareUno(): Boolean = submitHumanAction(UnoAction.DeclareUno)
    fun chooseColor(color: UnoColor): Boolean = submitHumanAction(UnoAction.ChooseColor(color))
    fun startNextRound(): Boolean = submitHumanAction(UnoAction.StartNextRound)

    fun catchUno(): Boolean {
        val target = _uiState.value.catchTargetPlayerId ?: return false
        return submitHumanAction(UnoAction.CatchUno(target))
    }

    fun submitHumanAction(action: UnoAction): Boolean {
        val game = engine ?: return false
        synchronized(jobLock) {
            if (actionChainJob?.isActive == true || _uiState.value.isActionInProgress) return false
            if (!isEngineOfferedHumanAction(game, action)) {
                errorMessage = "操作已经过期，请按当前提示继续"
                refresh(isActionInProgress = false, isBotThinking = false)
                return false
            }
            refresh(isActionInProgress = true, isBotThinking = false)
            actionChainJob = scope.launch { processActionChain(action) }
        }
        return true
    }

    suspend fun submitHumanActionAndWait(action: UnoAction): Boolean {
        awaitIdle()
        val game = engine ?: return false
        if (_uiState.value.isActionInProgress || !isEngineOfferedHumanAction(game, action)) return false
        refresh(isActionInProgress = true, isBotThinking = false)
        return processActionChain(action)
    }

    suspend fun awaitIdle() {
        while (true) {
            val job = synchronized(jobLock) { actionChainJob }
            if (job == null || !job.isActive) return
            job.join()
        }
    }

    fun dismissEvent() {
        eventMessage = null
        refreshFromCurrentFlags()
    }

    fun dismissError() {
        errorMessage = null
        refreshFromCurrentFlags()
    }

    fun close() = clearGame()

    private fun scheduleBotChainIfNeeded() {
        val game = engine ?: return
        if (nextBot(game) == null) return
        synchronized(jobLock) {
            if (actionChainJob?.isActive == true) return
            refresh(isActionInProgress = true, isBotThinking = true)
            actionChainJob = scope.launch { processActionChain(initialHumanAction = null) }
        }
    }

    private suspend fun processActionChain(initialHumanAction: UnoAction?): Boolean {
        var successful = true
        try {
            actionMutex.withLock {
                val game = engine ?: return false
                if (initialHumanAction != null) {
                    val result = game.applyAction(UNO_HUMAN_PLAYER_ID, initialHumanAction)
                    successful = handleResult(UNO_HUMAN_PLAYER_ID, initialHumanAction, result)
                    if (!successful) return@withLock
                }

                while (true) {
                    val bot = nextBot(game) ?: break
                    refresh(isActionInProgress = true, isBotThinking = true)
                    botDelayProvider.awaitDelay()
                    val action = bot.chooseAction(game)
                    if (action == null) {
                        errorMessage = "${playerName(bot.playerId)}暂时无法行动"
                        successful = false
                        break
                    }
                    val result = game.applyAction(bot.playerId, action)
                    if (!handleResult(bot.playerId, action, result)) {
                        successful = false
                        break
                    }
                }
            }
        } finally {
            refresh(isActionInProgress = false, isBotThinking = false)
        }
        return successful
    }

    private fun nextBot(game: UnoEngine): UnoBot? {
        val view = game.viewFor(UNO_HUMAN_PLAYER_ID) ?: return null
        if (view.phase == UnoPhase.ROUND_FINISHED || view.phase == UnoPhase.MATCH_FINISHED) return null
        val catcher = botsById.values.firstOrNull {
            UnoActionType.CATCH_UNO in game.availableActions(it.playerId)
        }
        if (catcher != null) return catcher
        return view.currentPlayerId?.let(botsById::get)
    }

    private fun handleResult(playerId: String, action: UnoAction, result: UnoActionResult): Boolean {
        if (!result.success) {
            errorMessage = humanReadableError(result.error?.code)
            refreshFromCurrentFlags()
            return false
        }
        errorMessage = null
        eventMessage = when (action) {
            UnoAction.DeclareUno -> if (playerId == UNO_HUMAN_PLAYER_ID) "已喊 UNO！" else "${playerName(playerId)}：UNO！"
            is UnoAction.CatchUno -> "${playerName(action.targetPlayerId)}未喊UNO，罚摸2张"
            else -> eventMessage
        }
        refreshFromCurrentFlags()
        return true
    }

    private fun isEngineOfferedHumanAction(game: UnoEngine, action: UnoAction): Boolean {
        val available = game.availableActions(UNO_HUMAN_PLAYER_ID)
        return when (action) {
            is UnoAction.PlayCard -> UnoActionType.PLAY_CARD in available && game.legalPlayableCards(UNO_HUMAN_PLAYER_ID).any { it.cardId == action.cardId }
            UnoAction.DrawCard -> UnoActionType.DRAW_CARD in available
            is UnoAction.PlayDrawnCard -> UnoActionType.PLAY_DRAWN_CARD in available && game.legalPlayableCards(UNO_HUMAN_PLAYER_ID).any { it.cardId == action.cardId }
            UnoAction.PassAfterDraw -> UnoActionType.PASS_AFTER_DRAW in available
            UnoAction.DeclareUno -> UnoActionType.DECLARE_UNO in available
            is UnoAction.CatchUno -> UnoActionType.CATCH_UNO in available && action.targetPlayerId == _uiState.value.catchTargetPlayerId
            is UnoAction.ChooseColor -> UnoActionType.CHOOSE_COLOR in available
            UnoAction.StartNextRound -> UnoActionType.START_NEXT_ROUND in available
        }
    }

    private fun refreshFromCurrentFlags() = refresh(
        isActionInProgress = _uiState.value.isActionInProgress,
        isBotThinking = _uiState.value.isBotThinking,
    )

    private fun refresh(isActionInProgress: Boolean, isBotThinking: Boolean) {
        val game = engine ?: return
        _uiState.value = UnoUiStateMapper.from(
            engine = game,
            config = currentConfig,
            isActionInProgress = isActionInProgress,
            isBotThinking = isBotThinking,
            eventMessage = eventMessage,
            errorMessage = errorMessage,
        )
    }

    private fun playerName(playerId: String): String =
        engine?.viewFor(UNO_HUMAN_PLAYER_ID)?.players?.firstOrNull { it.playerId == playerId }?.playerName ?: playerId

    private fun humanReadableError(code: UnoErrorCode?): String = when (code) {
        UnoErrorCode.NOT_YOUR_TURN -> "当前不是你的回合"
        UnoErrorCode.WRONG_PHASE -> "当前不能进行这个操作"
        UnoErrorCode.CARD_NOT_IN_HAND,
        UnoErrorCode.CARD_NOT_PLAYABLE,
        UnoErrorCode.ILLEGAL_WILD_DRAW_FOUR,
        UnoErrorCode.ONLY_DRAWN_CARD_CAN_BE_PLAYED,
        -> "这张牌现在不能出"
        UnoErrorCode.CANNOT_DECLARE_UNO,
        UnoErrorCode.ALREADY_DECLARED_UNO,
        -> "现在不能喊UNO"
        UnoErrorCode.UNO_CATCH_WINDOW_CLOSED,
        UnoErrorCode.CANNOT_CATCH_SELF,
        UnoErrorCode.INVALID_UNO_CATCH_TARGET,
        -> "抓UNO的机会已经结束"
        UnoErrorCode.INVALID_COLOR_CHOICE -> "请选择有效颜色"
        UnoErrorCode.NEXT_ROUND_NOT_AVAILABLE -> "下一局暂时不能开始"
        else -> "操作未完成，请重试"
    }
}
