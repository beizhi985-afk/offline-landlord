package com.offlinelandlord.game.uno.bot

import com.offlinelandlord.game.uno.core.UnoActionType
import com.offlinelandlord.game.uno.core.UnoEngine
import com.offlinelandlord.game.uno.core.UnoGameState
import com.offlinelandlord.game.uno.core.UnoPhase

enum class UnoBotRunStatus {
    COMPLETED,
    WAITING_FOR_EXTERNAL_PLAYER,
    ACTION_LIMIT_REACHED,
    INVALID_BOT_DECISION,
    ENGINE_REJECTED_ACTION,
}

data class UnoBotRunResult(
    val status: UnoBotRunStatus,
    val actionCount: Int,
    val message: String? = null,
)

class UnoBotGameRunner(
    private val engine: UnoEngine,
    bots: Collection<UnoBot>,
) {
    private val botsById = bots.associateBy { it.playerId }

    init {
        require(botsById.size == bots.size) { "Bot player IDs must be unique" }
    }

    fun runUntilBlocked(
        maxActionCount: Int,
        onActionApplied: ((UnoGameState) -> Unit)? = null,
    ): UnoBotRunResult {
        require(maxActionCount > 0) { "maxActionCount must be positive" }
        var actionCount = 0
        while (actionCount < maxActionCount) {
            val state = engine.state
            if (state.phase == UnoPhase.MATCH_FINISHED) {
                return UnoBotRunResult(UnoBotRunStatus.COMPLETED, actionCount)
            }

            val bot = selectNextBot(state.currentPlayerId, state.phase)
                ?: return UnoBotRunResult(UnoBotRunStatus.WAITING_FOR_EXTERNAL_PLAYER, actionCount)
            val action = bot.chooseAction(engine)
                ?: return UnoBotRunResult(
                    UnoBotRunStatus.INVALID_BOT_DECISION,
                    actionCount,
                    "Bot ${bot.playerId} did not return an Engine-approved action",
                )
            val result = engine.applyAction(bot.playerId, action)
            if (!result.success) {
                return UnoBotRunResult(
                    UnoBotRunStatus.ENGINE_REJECTED_ACTION,
                    actionCount,
                    result.error?.message,
                )
            }
            actionCount++
            onActionApplied?.invoke(result.state)
        }
        return UnoBotRunResult(UnoBotRunStatus.ACTION_LIMIT_REACHED, actionCount)
    }

    private fun selectNextBot(currentPlayerId: String?, phase: UnoPhase): UnoBot? {
        val catcher = botsById.values.firstOrNull {
            UnoActionType.CATCH_UNO in engine.availableActions(it.playerId)
        }
        if (catcher != null) return catcher
        if (phase == UnoPhase.ROUND_FINISHED) {
            return botsById.values.firstOrNull {
                UnoActionType.START_NEXT_ROUND in engine.availableActions(it.playerId)
            }
        }
        return currentPlayerId?.let(botsById::get)
    }
}
