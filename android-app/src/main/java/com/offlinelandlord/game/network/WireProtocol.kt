package com.offlinelandlord.game.network

import com.offlinelandlord.game.core.PlayerAction
import com.offlinelandlord.game.core.PlayerGameView
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
enum class WireType {
    JOIN,
    JOIN_ACCEPTED,
    ACTION,
    STATE,
    ERROR,
    PING,
    PONG,
}

@Serializable
data class WireEnvelope(
    val type: WireType,
    val protocolVersion: Int = 0,
    val requestId: String = "",
    val playerId: String? = null,
    val playerName: String? = null,
    val roomCode: String? = null,
    val resumeToken: String? = null,
    val expectedRevision: Long? = null,
    val action: PlayerAction? = null,
    val view: PlayerGameView? = null,
    val message: String? = null,
)

internal val wireJson = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
}

internal const val WIRE_PROTOCOL_VERSION = 2
