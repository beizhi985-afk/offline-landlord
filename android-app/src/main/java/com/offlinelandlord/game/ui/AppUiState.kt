package com.offlinelandlord.game.ui

import com.offlinelandlord.game.core.PlayerGameView
import com.offlinelandlord.game.network.ConnectionState
import com.offlinelandlord.game.network.DiscoveredRoom

data class AppUiState(
    val gameView: PlayerGameView? = null,
    val isHost: Boolean = false,
    val hostAddress: String = "",
    val port: Int = 39173,
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val discoveredRooms: List<DiscoveredRoom> = emptyList(),
    val isDiscovering: Boolean = false,
    val isBusy: Boolean = false,
    val errorMessage: String? = null,
)

