package com.offlinelandlord.game.network.protocol.v5

import com.offlinelandlord.game.shared.GameType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Generic message names for the multi-game V5 protocol.
 *
 * Game-specific actions and state are intentionally carried by [V5WireEnvelope.payload].
 */
@Serializable
enum class V5WireType {
    JOIN,
    JOIN_ACCEPTED,
    ACTION,
    STATE,
    ERROR,
    PING,
    PONG,
}

/**
 * Common V5 wire envelope. It knows the game category, but not any game's models.
 */
@Serializable
data class V5WireEnvelope(
    val gameType: GameType,
    val type: V5WireType,
    val protocolVersion: Int = V5_PROTOCOL_VERSION,
    val requestId: String? = null,
    val playerId: String? = null,
    val roomCode: String? = null,
    val resumeToken: String? = null,
    val expectedRevision: Long? = null,
    val payload: JsonElement? = null,
    val message: String? = null,
) {
    init {
        require(protocolVersion == V5_PROTOCOL_VERSION) { "V5 信封必须使用 protocolVersion = 5" }
    }
}

internal val v5WireJson = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
}

const val V5_PROTOCOL_VERSION = 5

/**
 * Safe V5 parsing helpers. Unknown enum values, missing required fields, and malformed JSON
 * are rejected as protocol data instead of escaping into a network coroutine.
 */
object V5ProtocolCodec {
    fun decode(rawMessage: String): V5WireEnvelope? = runCatching {
        val root = v5WireJson.parseToJsonElement(rawMessage).jsonObject
        if (!requiredEnvelopeFields.all(root::containsKey)) return null
        v5WireJson.decodeFromJsonElement(V5WireEnvelope.serializer(), root)
    }.getOrNull()?.takeIf { it.protocolVersion == V5_PROTOCOL_VERSION }

    fun protocolVersionOf(rawMessage: String): Int? = runCatching {
        v5WireJson.parseToJsonElement(rawMessage)
            .jsonObject["protocolVersion"]
            ?.jsonPrimitive
            ?.intOrNull
    }.getOrNull()

    private val requiredEnvelopeFields = setOf(
        "protocolVersion",
        "gameType",
        "type",
        "payload",
    )
}
