package com.offlinelandlord.game.network.landlord.v5

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Landlord-only values carried inside the generic V5 discovery gameConfig object. */
data class LandlordV5DiscoveryConfig(
    val totalRounds: Int,
    val doublingEnabled: Boolean,
)

object LandlordV5DiscoveryConfigCodec {
    fun encode(totalRounds: Int, doublingEnabled: Boolean): JsonObject = buildJsonObject {
        put("totalRounds", totalRounds)
        put("doublingEnabled", doublingEnabled)
    }

    fun decode(gameConfig: JsonObject): LandlordV5DiscoveryConfig? {
        val totalRounds = gameConfig["totalRounds"]?.jsonPrimitive?.intOrNull
            ?.takeIf { it == 12 || it == 24 }
            ?: return null
        val doublingEnabled = gameConfig["doublingEnabled"]?.jsonPrimitive?.booleanOrNull ?: return null
        return LandlordV5DiscoveryConfig(totalRounds, doublingEnabled)
    }
}
