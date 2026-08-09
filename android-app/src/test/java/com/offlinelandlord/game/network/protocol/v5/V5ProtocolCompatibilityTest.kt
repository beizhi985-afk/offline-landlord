package com.offlinelandlord.game.network.protocol.v5

import com.offlinelandlord.game.core.ActionType
import com.offlinelandlord.game.core.GamePhase
import com.offlinelandlord.game.network.landlord.v5.LandlordV5PayloadCodec
import com.offlinelandlord.game.shared.GameType
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class V5ProtocolCompatibilityTest {
    @Test
    fun historicalV5JoinFixtureDecodes() {
        val envelope = decode(JOIN_FIXTURE)

        assertEquals(GameType.LANDLORD, envelope.gameType)
        assertEquals(V5WireType.JOIN, envelope.type)
        assertEquals(5, envelope.protocolVersion)
        assertEquals("join-v5-1", envelope.requestId)
        assertEquals("123456", envelope.roomCode)
        assertEquals("新客户端", LandlordV5PayloadCodec.decodeJoin(envelope.payload)?.playerName)
    }

    @Test
    fun historicalV5JoinAcceptedFixtureDecodes() {
        val envelope = decode(JOIN_ACCEPTED_FIXTURE)

        assertEquals(V5WireType.JOIN_ACCEPTED, envelope.type)
        assertEquals("player-2", envelope.playerId)
        assertEquals("resume-2", envelope.resumeToken)
        assertEquals("加入成功", envelope.message)
        assertNull(LandlordV5PayloadCodec.decodeJoinAccepted(envelope.payload)?.view)
    }

    @Test
    fun historicalV5ActionFixtureDecodes() {
        val envelope = decode(ACTION_FIXTURE)

        assertEquals(V5WireType.ACTION, envelope.type)
        assertEquals(12L, envelope.expectedRevision)
        assertEquals(ActionType.SET_READY, LandlordV5PayloadCodec.decodeAction(envelope.payload)?.type)
        assertEquals(true, LandlordV5PayloadCodec.decodeAction(envelope.payload)?.ready)
    }

    @Test
    fun historicalV5StateFixtureDecodes() {
        val envelope = decode(STATE_FIXTURE)
        val view = requireNotNull(LandlordV5PayloadCodec.decodeView(envelope.payload))

        assertEquals(V5WireType.STATE, envelope.type)
        assertEquals(GamePhase.WAITING, view.phase)
        assertEquals("V5房间", view.roomName)
        assertEquals(7L, view.revision)
        assertEquals(12, view.totalRounds)
    }

    @Test
    fun historicalV5ErrorFixtureDecodes() {
        val envelope = decode(ERROR_FIXTURE)

        assertEquals(V5WireType.ERROR, envelope.type)
        assertEquals("V5错误", envelope.message)
        assertEquals("error-v5-1", envelope.requestId)
    }

    @Test
    fun historicalV5PingFixtureDecodes() {
        val envelope = decode(PING_FIXTURE)

        assertEquals(V5WireType.PING, envelope.type)
        assertEquals("ping-v5-1", envelope.requestId)
    }

    @Test
    fun historicalV5PongFixtureDecodes() {
        val envelope = decode(PONG_FIXTURE)

        assertEquals(V5WireType.PONG, envelope.type)
        assertEquals("ping-v5-1", envelope.requestId)
    }

    @Test
    fun currentV5EnvelopeKeepsOnlyGenericFields() {
        val encoded = v5WireJson.encodeToJsonElement(
            V5WireEnvelope.serializer(),
            V5WireEnvelope(
                gameType = GameType.LANDLORD,
                type = V5WireType.ACTION,
                payload = JsonPrimitive("generic payload"),
            ),
        ).jsonObject

        assertEquals(
            setOf(
                "gameType",
                "type",
                "protocolVersion",
                "requestId",
                "playerId",
                "roomCode",
                "resumeToken",
                "expectedRevision",
                "payload",
                "message",
            ),
            encoded.keys,
        )
        assertFalse(encoded.containsKey("action"))
        assertFalse(encoded.containsKey("view"))
        assertEquals(5, encoded["protocolVersion"]?.toString()?.toInt())
    }

    @Test
    fun wrongProtocolVersionIsRejected() {
        assertNull(V5ProtocolCodec.decode(JOIN_FIXTURE.replace("\"protocolVersion\":5", "\"protocolVersion\":4")))
        assertNull(V5ProtocolCodec.decode(JOIN_FIXTURE.replace("\"protocolVersion\":5,", "")))
        assertNull(V5ProtocolCodec.decode(JOIN_FIXTURE.replace("\"payload\":{\"playerName\":\"新客户端\"},", "")))
    }

    @Test
    fun unknownGameTypeIsRejectedSafely() {
        assertNull(V5ProtocolCodec.decode(JOIN_FIXTURE.replace("\"LANDLORD\"", "\"TETRIS\"")))
    }

    @Test
    fun unknownMessageTypeAndWrongLandlordPayloadAreRejectedSafely() {
        assertNull(V5ProtocolCodec.decode(JOIN_FIXTURE.replace("\"JOIN\"", "\"KICK\"")))
        assertNull(LandlordV5PayloadCodec.decodeAction(JsonPrimitive("not-an-action")))
    }

    private fun decode(fixture: String): V5WireEnvelope =
        requireNotNull(V5ProtocolCodec.decode(fixture))

    private companion object {
        const val JOIN_FIXTURE = """{"gameType":"LANDLORD","type":"JOIN","protocolVersion":5,"requestId":"join-v5-1","playerId":null,"roomCode":"123456","resumeToken":"resume-1","expectedRevision":null,"payload":{"playerName":"新客户端"},"message":null}"""
        const val JOIN_ACCEPTED_FIXTURE = """{"gameType":"LANDLORD","type":"JOIN_ACCEPTED","protocolVersion":5,"requestId":"join-v5-1","playerId":"player-2","roomCode":"123456","resumeToken":"resume-2","expectedRevision":null,"payload":{"view":null},"message":"加入成功"}"""
        const val ACTION_FIXTURE = """{"gameType":"LANDLORD","type":"ACTION","protocolVersion":5,"requestId":"action-v5-1","playerId":"player-2","roomCode":null,"resumeToken":null,"expectedRevision":12,"payload":{"type":"SET_READY","ready":true,"bid":null,"doubleChoice":null,"cardIds":[],"targetPlayerId":null,"autoPlay":null},"message":null}"""
        const val STATE_FIXTURE = """{"gameType":"LANDLORD","type":"STATE","protocolVersion":5,"requestId":null,"playerId":null,"roomCode":null,"resumeToken":null,"expectedRevision":null,"payload":{"roomCode":"123456","roomName":"V5房间","selfPlayerId":"player-2","hostPlayerId":"player-1","phase":"WAITING","players":[],"ownHand":[],"bottomCards":[],"landlordId":null,"currentTurnId":null,"lastPlay":null,"highestBid":0,"multiplier":1,"totalRounds":12,"currentRound":1,"completedRounds":0,"doublingEnabled":true,"matchComplete":false,"result":null,"roundHistory":[],"revision":7,"statusMessage":"等待玩家"},"message":null}"""
        const val ERROR_FIXTURE = """{"gameType":"LANDLORD","type":"ERROR","protocolVersion":5,"requestId":"error-v5-1","playerId":null,"roomCode":null,"resumeToken":null,"expectedRevision":null,"payload":null,"message":"V5错误"}"""
        const val PING_FIXTURE = """{"gameType":"LANDLORD","type":"PING","protocolVersion":5,"requestId":"ping-v5-1","playerId":null,"roomCode":null,"resumeToken":null,"expectedRevision":null,"payload":null,"message":null}"""
        const val PONG_FIXTURE = """{"gameType":"LANDLORD","type":"PONG","protocolVersion":5,"requestId":"ping-v5-1","playerId":null,"roomCode":null,"resumeToken":null,"expectedRevision":null,"payload":null,"message":null}"""
    }
}
