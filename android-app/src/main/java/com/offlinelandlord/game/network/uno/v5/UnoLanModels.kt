package com.offlinelandlord.game.network.uno.v5

import com.offlinelandlord.game.network.ConnectionState

data class UnoLanRoom(
    val host: String,
    val port: Int,
    val roomCode: String,
    val roomName: String,
    val playerCount: Int,
    val maxPlayers: Int,
    val gameMode: UnoV5GameMode,
    val status: UnoV5RoomStatus,
)

/** State exposed to Compose. [room] is always a player-specific public projection. */
data class UnoLanUiState(
    val room: UnoV5RoomView? = null,
    val roomName: String = "UNO 房间",
    val hostAddress: String = "",
    val hostPort: Int = 0,
    val selfPlayerId: String? = null,
    val rooms: List<UnoLanRoom> = emptyList(),
    val isHost: Boolean = false,
    val isBusy: Boolean = false,
    val isDiscovering: Boolean = false,
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val isReconnecting: Boolean = false,
    val errorMessage: String? = null,
    val eventMessage: String? = null,
) {
    val game: UnoV5GameView? get() = room?.game
    val selfId: String? get() = game?.selfPlayerId ?: selfPlayerId
    val selfHand: List<UnoV5Card> get() = game?.ownHand.orEmpty()
    val opponentViews: List<UnoV5PlayerView> get() = game?.players?.filter { it.playerId != selfId }.orEmpty()
    val currentPlayerName: String?
        get() = game?.currentPlayerId?.let { id -> game?.players?.firstOrNull { it.playerId == id }?.displayName }
    val isPlaying: Boolean get() = room?.status == UnoV5RoomStatus.PLAYING
    /** Results are rendered only when the authoritative host explicitly finishes a round or match. */
    val isFinished: Boolean get() = isAuthoritativeUnoResult(room?.status)
}

internal fun isAuthoritativeUnoResult(status: UnoV5RoomStatus?): Boolean =
    status == UnoV5RoomStatus.ROUND_FINISHED || status == UnoV5RoomStatus.MATCH_FINISHED
