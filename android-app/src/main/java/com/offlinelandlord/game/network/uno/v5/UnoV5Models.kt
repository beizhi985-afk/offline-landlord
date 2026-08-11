package com.offlinelandlord.game.network.uno.v5

import kotlinx.serialization.Serializable

/** UNO-specific wire data. These DTOs intentionally do not depend on the UNO rule engine. */
@Serializable
enum class UnoV5GameMode { QUICK, POINTS_500 }

@Serializable
enum class UnoV5RoomStatus { WAITING, PLAYING, ROUND_FINISHED, MATCH_FINISHED }

@Serializable
enum class UnoV5ActionType {
    PLAY_CARD,
    DRAW_CARD,
    PLAY_DRAWN_CARD,
    PASS_AFTER_DRAW,
    CHOOSE_COLOR,
    DECLARE_UNO,
    CATCH_UNO,
    START_NEXT_ROUND,
    READY,
    UNREADY,
    START_GAME,
    ADD_BOT,
    REMOVE_BOT,
}

@Serializable
data class UnoV5RoomConfig(
    val maxPlayers: Int = 2,
    val gameMode: UnoV5GameMode = UnoV5GameMode.QUICK,
)
@Serializable
data class UnoV5JoinPayload(
    val displayName: String,
    val config: UnoV5RoomConfig? = null,
)

@Serializable
data class UnoV5ActionPayload(
    val action: UnoV5ActionType,
    val cardId: String? = null,
    val color: String? = null,
    val targetPlayerId: String? = null,
)

@Serializable
data class UnoV5PlayerView(
    val playerId: String,
    val seatIndex: Int,
    val displayName: String,
    val connected: Boolean,
    val ready: Boolean,
    val isBot: Boolean,
    val hand: List<UnoV5Card> = emptyList(),
    val handCount: Int = hand.size,
    val score: Int = 0,
)

@Serializable
data class UnoV5Card(
    val cardId: String,
    val color: String? = null,
    val type: String,
    val number: Int? = null,
)

@Serializable
data class UnoV5GameView(
    val selfPlayerId: String,
    val ownHand: List<UnoV5Card>,
    val players: List<UnoV5PlayerView>,
    val topDiscard: UnoV5Card,
    val drawPileCount: Int,
    val activeColor: String?,
    val currentPlayerId: String?,
    val direction: String,
    val phase: String,
    val roundNumber: Int,
    val scores: Map<String, Int>,
    val unoDeclaredPlayerId: String?,
    val catchTargetPlayerId: String?,
    val drawnCardId: String?,
    val colorChooserPlayerId: String?,
    val roundWinnerId: String?,
    val matchWinnerId: String?,
    val legalActions: Set<UnoV5ActionType> = emptySet(),
    val legalPlayableCardIds: List<String> = emptyList(),
)

@Serializable
data class UnoV5RoomView(
    val roomCode: String,
    val hostPlayerId: String,
    val players: List<UnoV5PlayerView>,
    val gameMode: UnoV5GameMode,
    val maxPlayers: Int,
    val status: UnoV5RoomStatus,
    val revision: Long,
    val game: UnoV5GameView? = null,
)

@Serializable
data class UnoV5DiscoveryPayload(
    val roomCode: String,
    val roomName: String,
    val hostPort: Int,
    val playerCount: Int,
    val maxPlayers: Int,
    val gameMode: UnoV5GameMode,
    val status: UnoV5RoomStatus,
)

@Serializable
data class UnoV5JoinAcceptedPayload(
    val playerId: String,
    val resumeToken: String,
    val room: UnoV5RoomView,
)

@Serializable
data class UnoV5ErrorPayload(
    val code: String,
    val detail: String? = null,
)
