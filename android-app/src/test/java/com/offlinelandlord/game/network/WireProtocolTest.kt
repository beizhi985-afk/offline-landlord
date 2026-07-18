package com.offlinelandlord.game.network

import com.offlinelandlord.game.core.PlayerAction
import org.junit.Assert.assertEquals
import org.junit.Test

class WireProtocolTest {
    @Test
    fun actionEnvelopeRoundTrips() {
        val original = WireEnvelope(
            type = WireType.ACTION,
            requestId = "request-1",
            playerId = "player-1",
            expectedRevision = 12,
            action = PlayerAction.play(listOf("SPADES_THREE", "HEARTS_THREE")),
        )
        val encoded = wireJson.encodeToString(WireEnvelope.serializer(), original)
        val decoded = wireJson.decodeFromString(WireEnvelope.serializer(), encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun doublingChoiceRoundTripsOnProtocolVersionThree() {
        val original = WireEnvelope(
            type = WireType.ACTION,
            protocolVersion = WIRE_PROTOCOL_VERSION,
            requestId = "double-1",
            playerId = "player-2",
            action = PlayerAction.double(true),
        )

        val decoded = wireJson.decodeFromString(
            WireEnvelope.serializer(),
            wireJson.encodeToString(WireEnvelope.serializer(), original),
        )

        assertEquals(3, decoded.protocolVersion)
        assertEquals(true, decoded.action?.doubleChoice)
    }
}
