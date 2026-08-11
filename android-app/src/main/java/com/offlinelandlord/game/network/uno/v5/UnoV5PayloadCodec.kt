package com.offlinelandlord.game.network.uno.v5

import com.offlinelandlord.game.network.protocol.v5.V5WireEnvelope
import com.offlinelandlord.game.network.protocol.v5.V5WireType
import com.offlinelandlord.game.shared.GameType
import com.offlinelandlord.game.uno.core.UnoAction
import com.offlinelandlord.game.uno.core.UnoCard
import com.offlinelandlord.game.uno.core.UnoCardType
import com.offlinelandlord.game.uno.core.UnoColor
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/** Maps UNO core values to the generic V5 envelope without leaking core models into the protocol. */
object UnoV5PayloadCodec {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = false }

    fun encodeJoin(name: String, config: UnoV5RoomConfig? = null): JsonElement =
        json.encodeToJsonElement(UnoV5JoinPayload.serializer(), UnoV5JoinPayload(name, config))

    fun decodeJoin(payload: JsonElement?): UnoV5JoinPayload? = payload?.let {
        runCatching { json.decodeFromJsonElement(UnoV5JoinPayload.serializer(), it) }.getOrNull()
    }

    fun encodeAction(payload: UnoV5ActionPayload): JsonElement =
        json.encodeToJsonElement(UnoV5ActionPayload.serializer(), payload)

    fun decodeAction(payload: JsonElement?): UnoV5ActionPayload? = payload?.let {
        runCatching { json.decodeFromJsonElement(UnoV5ActionPayload.serializer(), it) }.getOrNull()
    }

    fun encodeRoom(room: UnoV5RoomView): JsonElement =
        json.encodeToJsonElement(UnoV5RoomView.serializer(), room)

    fun decodeRoom(payload: JsonElement?): UnoV5RoomView? = payload?.let {
        runCatching { json.decodeFromJsonElement(UnoV5RoomView.serializer(), it) }.getOrNull()
    }

    fun encodeJoinAccepted(payload: UnoV5JoinAcceptedPayload): JsonElement =
        json.encodeToJsonElement(UnoV5JoinAcceptedPayload.serializer(), payload)

    fun encodeError(code: UnoV5ErrorCode, detail: String? = null): JsonElement =
        json.encodeToJsonElement(UnoV5ErrorPayload.serializer(), UnoV5ErrorPayload(code.name, detail))

    fun action(payload: UnoV5ActionPayload): UnoAction? = when (payload.action) {
        UnoV5ActionType.PLAY_CARD -> payload.cardId?.let(UnoAction::PlayCard)
        UnoV5ActionType.DRAW_CARD -> UnoAction.DrawCard
        UnoV5ActionType.PLAY_DRAWN_CARD -> payload.cardId?.let(UnoAction::PlayDrawnCard)
        UnoV5ActionType.PASS_AFTER_DRAW -> UnoAction.PassAfterDraw
        UnoV5ActionType.CHOOSE_COLOR -> payload.color?.let { runCatching { UnoAction.ChooseColor(UnoColor.valueOf(it)) }.getOrNull() }
        UnoV5ActionType.DECLARE_UNO -> UnoAction.DeclareUno
        UnoV5ActionType.CATCH_UNO -> payload.targetPlayerId?.let(UnoAction::CatchUno)
        UnoV5ActionType.START_NEXT_ROUND -> UnoAction.StartNextRound
        else -> null
    }

    fun card(card: UnoCard): UnoV5Card = UnoV5Card(
        cardId = card.cardId,
        color = card.color?.name,
        type = card.type.name,
        number = card.number,
    )

    fun envelope(
        type: V5WireType,
        payload: JsonElement? = null,
        requestId: String? = null,
        playerId: String? = null,
        roomCode: String? = null,
        resumeToken: String? = null,
        expectedRevision: Long? = null,
        message: String? = null,
    ) = V5WireEnvelope(
        gameType = GameType.UNO,
        type = type,
        requestId = requestId,
        playerId = playerId,
        roomCode = roomCode,
        resumeToken = resumeToken,
        expectedRevision = expectedRevision,
        payload = payload,
        message = message,
    )
}
enum class UnoV5ErrorCode {
    ROOM_NOT_FOUND,
    ROOM_FULL,
    GAME_ALREADY_STARTED,
    NOT_YOUR_TURN,
    ILLEGAL_ACTION,
    STALE_REVISION,
    INVALID_RESUME_TOKEN,
    NOT_HOST,
    NOT_READY,
    INVALID_GAME_TYPE,
    PLAYER_NOT_FOUND,
    INVALID_ACTION,
    NOT_ENOUGH_PLAYERS,
    INVALID_CONFIG,
}
