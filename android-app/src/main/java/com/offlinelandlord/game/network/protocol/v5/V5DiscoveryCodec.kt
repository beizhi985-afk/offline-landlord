package com.offlinelandlord.game.network.protocol.v5

import com.offlinelandlord.game.shared.GameType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/** A game-agnostic V5 LAN room advertisement. */
@Serializable
data class V5RoomAdvertisement(
    val type: String = V5DiscoveryCodec.discoveryResponse,
    val protocolVersion: Int = V5_PROTOCOL_VERSION,
    val gameType: GameType,
    val roomCode: String,
    val roomName: String,
    val hostPort: Int,
    val playerCount: Int,
    val maxPlayers: Int,
    val gameConfig: JsonObject = JsonObject(emptyMap()),
) {
    init {
        require(protocolVersion == V5_PROTOCOL_VERSION) { "V5 房间公告必须使用 protocolVersion = 5" }
    }
}

/**
 * Generic UDP discovery codec. Game-specific values belong solely in [V5RoomAdvertisement.gameConfig].
 */
object V5DiscoveryCodec {
    const val discoveryRequest = "OFFLINE_GAMES_DISCOVER_V5"
    const val discoveryResponse = "OFFLINE_GAMES_HOST_V5"

    fun isDiscoveryRequest(message: String): Boolean = message == discoveryRequest

    fun encodeResponse(advertisement: V5RoomAdvertisement): String =
        v5WireJson.encodeToString(V5RoomAdvertisement.serializer(), advertisement)

    fun decodeResponse(message: String): V5RoomAdvertisement? = runCatching {
        val root = v5WireJson.parseToJsonElement(message).jsonObject
        if (!requiredAdvertisementFields.all(root::containsKey)) return null
        v5WireJson.decodeFromJsonElement(V5RoomAdvertisement.serializer(), root)
    }.getOrNull()?.takeIf(::isValid)

    private fun isValid(advertisement: V5RoomAdvertisement): Boolean =
        advertisement.type == discoveryResponse &&
            advertisement.protocolVersion == V5_PROTOCOL_VERSION &&
            advertisement.roomCode.length == 6 &&
            advertisement.roomCode.all(Char::isDigit) &&
            advertisement.roomName.isNotBlank() &&
            advertisement.hostPort in 1..65535 &&
            advertisement.maxPlayers in 1..16 &&
            advertisement.playerCount in 0..advertisement.maxPlayers

    private val requiredAdvertisementFields = setOf(
        "type",
        "protocolVersion",
        "gameType",
        "roomCode",
        "roomName",
        "hostPort",
        "playerCount",
        "maxPlayers",
        "gameConfig",
    )
}
