package com.offlinelandlord.game.core

import kotlinx.serialization.Serializable

@Serializable
enum class GamePhase {
    WAITING,
    BIDDING,
    DOUBLING,
    PLAYING,
    FINISHED,
}

@Serializable
enum class PlayerRole {
    UNKNOWN,
    LANDLORD,
    FARMER,
}

@Serializable
enum class ActionType {
    SET_READY,
    BID,
    DOUBLE,
    PLAY,
    PASS,
    ADD_BOT,
    REMOVE_BOT,
    SET_AUTOPLAY,
}

@Serializable
data class PlayerAction(
    val type: ActionType,
    val ready: Boolean? = null,
    val bid: Int? = null,
    val doubleChoice: Boolean? = null,
    val cardIds: List<String> = emptyList(),
    val targetPlayerId: String? = null,
    val autoPlay: Boolean? = null,
) {
    companion object {
        fun ready(value: Boolean) = PlayerAction(ActionType.SET_READY, ready = value)
        fun bid(value: Int) = PlayerAction(ActionType.BID, bid = value)
        fun double(value: Boolean) = PlayerAction(ActionType.DOUBLE, doubleChoice = value)
        fun play(cardIds: List<String>) = PlayerAction(ActionType.PLAY, cardIds = cardIds)
        fun pass() = PlayerAction(ActionType.PASS)
        fun addBot() = PlayerAction(ActionType.ADD_BOT)
        fun removeBot(playerId: String? = null) = PlayerAction(ActionType.REMOVE_BOT, targetPlayerId = playerId)
        fun autoPlay(value: Boolean) = PlayerAction(ActionType.SET_AUTOPLAY, autoPlay = value)
    }
}

@Serializable
data class PlayerSummary(
    val id: String,
    val name: String,
    val seat: Int,
    val ready: Boolean,
    val connected: Boolean,
    val role: PlayerRole,
    val remainingCards: Int,
    val score: Int,
    val bid: Int? = null,
    val doubleChoice: Boolean? = null,
    val isBot: Boolean = false,
    val isAutoPlaying: Boolean = false,
)

@Serializable
data class PublicPlay(
    val playerId: String,
    val cards: List<Card>,
    val pattern: CardPattern,
)

@Serializable
data class RoundResult(
    val winnerRole: PlayerRole,
    val winnerPlayerId: String,
    val multiplier: Int,
    val spring: Boolean,
    val scoreChanges: Map<String, Int>,
)

@Serializable
data class RoundRecord(
    val roundNumber: Int,
    val firstBidderId: String,
    val landlordId: String,
    val winnerRole: PlayerRole,
    val winnerPlayerId: String,
    val multiplier: Int,
    val spring: Boolean,
    val scoreChanges: Map<String, Int>,
    val totalScores: Map<String, Int>,
)

@Serializable
data class PlayerGameView(
    val roomCode: String,
    val roomName: String,
    val selfPlayerId: String,
    val hostPlayerId: String,
    val phase: GamePhase,
    val players: List<PlayerSummary>,
    val ownHand: List<Card>,
    val bottomCards: List<Card>,
    val landlordId: String? = null,
    val currentTurnId: String? = null,
    val lastPlay: PublicPlay? = null,
    val highestBid: Int = 0,
    val multiplier: Int = 1,
    val totalRounds: Int = 12,
    val currentRound: Int = 1,
    val completedRounds: Int = 0,
    val doublingEnabled: Boolean = true,
    val matchComplete: Boolean = false,
    val result: RoundResult? = null,
    val roundHistory: List<RoundRecord> = emptyList(),
    val revision: Long = 0,
    val statusMessage: String = "",
)

data class ActionResult(
    val success: Boolean,
    val message: String,
) {
    companion object {
        fun ok(message: String = "") = ActionResult(true, message)
        fun error(message: String) = ActionResult(false, message)
    }
}

data class JoinOutcome(
    val success: Boolean,
    val playerId: String? = null,
    val resumeToken: String? = null,
    val message: String = "",
)
