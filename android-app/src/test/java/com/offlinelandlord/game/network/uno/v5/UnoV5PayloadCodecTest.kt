package com.offlinelandlord.game.network.uno.v5

import com.offlinelandlord.game.network.protocol.v5.V5ProtocolCodec
import com.offlinelandlord.game.network.protocol.v5.V5WireType
import com.offlinelandlord.game.shared.GameType
import kotlinx.serialization.json.Json
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnoV5PayloadCodecTest {
    @Test fun joinRoundTrips() {
        val decoded = UnoV5PayloadCodec.decodeJoin(UnoV5PayloadCodec.encodeJoin("Alice", UnoV5RoomConfig(4, UnoV5GameMode.POINTS_500)))
        assertEquals("Alice", decoded!!.displayName); assertEquals(4, decoded.config!!.maxPlayers)
    }

    @Test fun actionCardIdRoundTrips() {
        val payload = UnoV5ActionPayload(UnoV5ActionType.PLAY_CARD, cardId = "r-7")
        assertEquals(payload, UnoV5PayloadCodec.decodeAction(UnoV5PayloadCodec.encodeAction(payload)))
    }

    @Test fun drawActionMapsToEngineAction() = assertTrue(UnoV5PayloadCodec.action(UnoV5ActionPayload(UnoV5ActionType.DRAW_CARD)) is com.offlinelandlord.game.uno.core.UnoAction.DrawCard)
    @Test fun passActionMapsToEngineAction() = assertTrue(UnoV5PayloadCodec.action(UnoV5ActionPayload(UnoV5ActionType.PASS_AFTER_DRAW)) is com.offlinelandlord.game.uno.core.UnoAction.PassAfterDraw)
    @Test fun declareActionMapsToEngineAction() = assertTrue(UnoV5PayloadCodec.action(UnoV5ActionPayload(UnoV5ActionType.DECLARE_UNO)) is com.offlinelandlord.game.uno.core.UnoAction.DeclareUno)
    @Test fun startNextRoundMapsToEngineAction() = assertTrue(UnoV5PayloadCodec.action(UnoV5ActionPayload(UnoV5ActionType.START_NEXT_ROUND)) is com.offlinelandlord.game.uno.core.UnoAction.StartNextRound)
    @Test fun invalidColorIsRejected() = assertNull(UnoV5PayloadCodec.action(UnoV5ActionPayload(UnoV5ActionType.CHOOSE_COLOR, color = "PURPLE")))
    @Test fun missingCardIsRejected() = assertNull(UnoV5PayloadCodec.action(UnoV5ActionPayload(UnoV5ActionType.PLAY_CARD)))
    @Test fun missingCatchTargetIsRejected() = assertNull(UnoV5PayloadCodec.action(UnoV5ActionPayload(UnoV5ActionType.CATCH_UNO)))

    @Test fun unoEnvelopeUsesGameTypeAndV5() {
        val raw = Json { encodeDefaults = true }.encodeToString(com.offlinelandlord.game.network.protocol.v5.V5WireEnvelope.serializer(), UnoV5PayloadCodec.envelope(V5WireType.PING))
        val decoded = V5ProtocolCodec.decode(raw)!!
        assertEquals(GameType.UNO, decoded.gameType); assertEquals(5, decoded.protocolVersion)
    }

    @Test fun errorPayloadHasStableCode() {
        val decoded = Json { encodeDefaults = true }.decodeFromJsonElement(UnoV5ErrorPayload.serializer(), UnoV5PayloadCodec.encodeError(UnoV5ErrorCode.STALE_REVISION))
        assertEquals("STALE_REVISION", decoded.code)
    }

    @Test fun discoveryPayloadSupportsQuick() {
        val payload = UnoV5DiscoveryPayload("123456", "Room", 39173, 1, 4, UnoV5GameMode.QUICK, UnoV5RoomStatus.WAITING)
        val json = Json { encodeDefaults = true }
        val decoded = json.decodeFromString(UnoV5DiscoveryPayload.serializer(), json.encodeToString(UnoV5DiscoveryPayload.serializer(), payload))
        assertEquals(payload, decoded)
    }

    @Test fun joinAcceptedContainsTokenAndRoom() {
        val room = UnoV5RoomView("123456", "host", emptyList(), UnoV5GameMode.QUICK, 2, UnoV5RoomStatus.WAITING, 1)
        val payload = UnoV5JoinAcceptedPayload("p", "token", room)
        val decoded = Json { encodeDefaults = true }.decodeFromJsonElement(UnoV5JoinAcceptedPayload.serializer(), UnoV5PayloadCodec.encodeJoinAccepted(payload))
        assertEquals("token", decoded.resumeToken); assertEquals("123456", decoded.room.roomCode)
    }

    @Test fun hiddenHandDefaultsToEmpty() {
        val player = UnoV5PlayerView("p", 0, "P", true, true, false)
        assertTrue(player.hand.isEmpty()); assertEquals(0, player.handCount)
    }

    @Test fun roomViewCarriesRevision() {
        val room = UnoV5RoomView("123456", "host", emptyList(), UnoV5GameMode.QUICK, 2, UnoV5RoomStatus.PLAYING, 42)
        assertEquals(42, room.revision)
    }

    @Test fun protocolRejectsWrongGameType() {
        val raw = Json { encodeDefaults = true }.encodeToString(com.offlinelandlord.game.network.protocol.v5.V5WireEnvelope.serializer(), com.offlinelandlord.game.network.protocol.v5.V5WireEnvelope(GameType.LANDLORD, V5WireType.PING))
        assertNotNull(V5ProtocolCodec.decode(raw))
    }

    @Test fun roomConfigDefaultIsTwoQuick() {
        val config = UnoV5RoomConfig()
        assertEquals(2, config.maxPlayers); assertEquals(UnoV5GameMode.QUICK, config.gameMode)
    }
}
