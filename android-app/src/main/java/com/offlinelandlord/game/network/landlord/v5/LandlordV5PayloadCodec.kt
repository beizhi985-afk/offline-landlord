package com.offlinelandlord.game.network.landlord.v5

import com.offlinelandlord.game.core.PlayerAction
import com.offlinelandlord.game.core.PlayerGameView
import com.offlinelandlord.game.network.protocol.v5.v5WireJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

/** V5 JOIN payload owned by the Landlord protocol adapter. */
@Serializable
data class LandlordV5JoinPayload(
    val playerName: String,
)

/** V5 JOIN_ACCEPTED payload owned by the Landlord protocol adapter. */
@Serializable
data class LandlordV5JoinAcceptedPayload(
    val view: PlayerGameView? = null,
)

/**
 * Serializes Landlord-specific models into the generic V5 payload slot.
 * The common V5 package consequently has no dependency on Landlord core types.
 */
object LandlordV5PayloadCodec {
    fun encodeJoin(playerName: String): JsonElement =
        v5WireJson.encodeToJsonElement(LandlordV5JoinPayload.serializer(), LandlordV5JoinPayload(playerName))

    fun decodeJoin(payload: JsonElement?): LandlordV5JoinPayload? = payload?.let {
        runCatching { v5WireJson.decodeFromJsonElement(LandlordV5JoinPayload.serializer(), it) }.getOrNull()
    }

    fun encodeJoinAccepted(view: PlayerGameView?): JsonElement =
        v5WireJson.encodeToJsonElement(
            LandlordV5JoinAcceptedPayload.serializer(),
            LandlordV5JoinAcceptedPayload(view),
        )

    fun decodeJoinAccepted(payload: JsonElement?): LandlordV5JoinAcceptedPayload? = payload?.let {
        runCatching { v5WireJson.decodeFromJsonElement(LandlordV5JoinAcceptedPayload.serializer(), it) }.getOrNull()
    }

    fun encodeAction(action: PlayerAction): JsonElement =
        v5WireJson.encodeToJsonElement(PlayerAction.serializer(), action)

    fun decodeAction(payload: JsonElement?): PlayerAction? = payload?.let {
        runCatching { v5WireJson.decodeFromJsonElement(PlayerAction.serializer(), it) }.getOrNull()
    }

    fun encodeView(view: PlayerGameView): JsonElement =
        v5WireJson.encodeToJsonElement(PlayerGameView.serializer(), view)

    fun decodeView(payload: JsonElement?): PlayerGameView? = payload?.let {
        runCatching { v5WireJson.decodeFromJsonElement(PlayerGameView.serializer(), it) }.getOrNull()
    }
}
