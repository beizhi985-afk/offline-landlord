package com.offlinelandlord.game.network

import com.offlinelandlord.game.core.ActionType
import com.offlinelandlord.game.core.GamePhase
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class V4WireCompatibilityTest {
    @Test
    fun historicalJoinFixtureDecodes() {
        val decoded = decode(HISTORICAL_JOIN)

        assertEquals(WireType.JOIN, decoded.type)
        assertEquals(4, decoded.protocolVersion)
        assertEquals("join-1", decoded.requestId)
        assertEquals("旧客户端", decoded.playerName)
        assertEquals("123456", decoded.roomCode)
        assertEquals("resume-1", decoded.resumeToken)
    }

    @Test
    fun historicalJoinAcceptedFixtureDecodes() {
        val decoded = decode(HISTORICAL_JOIN_ACCEPTED)

        assertEquals(WireType.JOIN_ACCEPTED, decoded.type)
        assertEquals("player-2", decoded.playerId)
        assertEquals("resume-2", decoded.resumeToken)
        assertEquals("加入成功", decoded.message)
        assertNull(decoded.view)
    }

    @Test
    fun historicalActionFixtureDecodes() {
        val decoded = decode(HISTORICAL_ACTION)

        assertEquals(WireType.ACTION, decoded.type)
        assertEquals(12L, decoded.expectedRevision)
        assertEquals(ActionType.SET_READY, decoded.action?.type)
        assertEquals(true, decoded.action?.ready)
    }

    @Test
    fun historicalStateFixtureDecodes() {
        val decoded = decode(HISTORICAL_STATE)

        assertEquals(WireType.STATE, decoded.type)
        assertEquals(GamePhase.WAITING, decoded.view?.phase)
        assertEquals("旧房间", decoded.view?.roomName)
        assertEquals(7L, decoded.view?.revision)
        assertEquals(12, decoded.view?.totalRounds)
    }

    @Test
    fun historicalErrorFixtureDecodes() {
        val decoded = decode(HISTORICAL_ERROR)

        assertEquals(WireType.ERROR, decoded.type)
        assertEquals("旧版错误", decoded.message)
        assertEquals("error-1", decoded.requestId)
    }

    @Test
    fun currentV4EnvelopeKeepsWireTypesAndFieldNames() {
        assertEquals(
            listOf("JOIN", "JOIN_ACCEPTED", "ACTION", "STATE", "ERROR", "PING", "PONG"),
            WireType.entries.map { it.name },
        )
        val encoded = wireJson.encodeToJsonElement(
            WireEnvelope.serializer(),
            WireEnvelope(type = WireType.PING),
        ).jsonObject

        assertEquals(
            setOf(
                "type",
                "protocolVersion",
                "requestId",
                "playerId",
                "playerName",
                "roomCode",
                "resumeToken",
                "expectedRevision",
                "action",
                "view",
                "message",
            ),
            encoded.keys,
        )
        assertTrue(encoded.containsKey("type"))
        assertEquals(4, WIRE_PROTOCOL_VERSION)
    }

    private fun decode(fixture: String): WireEnvelope =
        wireJson.decodeFromString(WireEnvelope.serializer(), fixture)

    private companion object {
        const val HISTORICAL_JOIN = """{"type":"JOIN","protocolVersion":4,"requestId":"join-1","playerId":null,"playerName":"旧客户端","roomCode":"123456","resumeToken":"resume-1","expectedRevision":null,"action":null,"view":null,"message":null}"""
        const val HISTORICAL_JOIN_ACCEPTED = """{"type":"JOIN_ACCEPTED","protocolVersion":4,"requestId":"join-1","playerId":"player-2","playerName":null,"roomCode":"123456","resumeToken":"resume-2","expectedRevision":null,"action":null,"view":null,"message":"加入成功"}"""
        const val HISTORICAL_ACTION = """{"type":"ACTION","protocolVersion":0,"requestId":"action-1","playerId":"player-2","playerName":null,"roomCode":null,"resumeToken":null,"expectedRevision":12,"action":{"type":"SET_READY","ready":true,"bid":null,"doubleChoice":null,"cardIds":[],"targetPlayerId":null,"autoPlay":null},"view":null,"message":null}"""
        const val HISTORICAL_STATE = """{"type":"STATE","protocolVersion":0,"requestId":"","playerId":null,"playerName":null,"roomCode":null,"resumeToken":null,"expectedRevision":null,"action":null,"view":{"roomCode":"123456","roomName":"旧房间","selfPlayerId":"player-2","hostPlayerId":"player-1","phase":"WAITING","players":[],"ownHand":[],"bottomCards":[],"landlordId":null,"currentTurnId":null,"lastPlay":null,"highestBid":0,"multiplier":1,"totalRounds":12,"currentRound":1,"completedRounds":0,"doublingEnabled":true,"matchComplete":false,"result":null,"roundHistory":[],"revision":7,"statusMessage":"等待玩家"},"message":null}"""
        const val HISTORICAL_ERROR = """{"type":"ERROR","protocolVersion":0,"requestId":"error-1","playerId":null,"playerName":null,"roomCode":null,"resumeToken":null,"expectedRevision":null,"action":null,"view":null,"message":"旧版错误"}"""
    }
}
