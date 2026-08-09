package com.offlinelandlord.game.network.protocol.v5

import com.offlinelandlord.game.network.landlord.v5.LandlordV5DiscoveryConfigCodec
import com.offlinelandlord.game.shared.GameType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class V5DiscoveryCodecTest {
    @Test
    fun discoveryRequestUsesTheGenericV5Identifier() {
        assertEquals("OFFLINE_GAMES_DISCOVER_V5", V5DiscoveryCodec.discoveryRequest)
        assertTrue(V5DiscoveryCodec.isDiscoveryRequest("OFFLINE_GAMES_DISCOVER_V5"))
        assertFalse(V5DiscoveryCodec.isDiscoveryRequest("DDZ_DISCOVER_V4"))
    }

    @Test
    fun historicalLandlordAdvertisementDecodes() {
        val advertisement = requireNotNull(V5DiscoveryCodec.decodeResponse(GOLDEN_ADVERTISEMENT))
        val config = requireNotNull(LandlordV5DiscoveryConfigCodec.decode(advertisement.gameConfig))

        assertEquals("OFFLINE_GAMES_HOST_V5", advertisement.type)
        assertEquals(5, advertisement.protocolVersion)
        assertEquals(GameType.LANDLORD, advertisement.gameType)
        assertEquals("804073", advertisement.roomCode)
        assertEquals("玩家56的房间", advertisement.roomName)
        assertEquals(39173, advertisement.hostPort)
        assertEquals(2, advertisement.playerCount)
        assertEquals(3, advertisement.maxPlayers)
        assertEquals(12, config.totalRounds)
        assertTrue(config.doublingEnabled)
    }

    @Test
    fun encodingKeepsLandlordOptionsInsideGameConfig() {
        val encoded = V5DiscoveryCodec.encodeResponse(
            V5RoomAdvertisement(
                gameType = GameType.LANDLORD,
                roomCode = "885115",
                roomName = "测试房间",
                hostPort = 39173,
                playerCount = 1,
                maxPlayers = 3,
                gameConfig = LandlordV5DiscoveryConfigCodec.encode(24, false),
            ),
        )
        val root = Json.parseToJsonElement(encoded).jsonObject

        assertEquals("OFFLINE_GAMES_HOST_V5", root["type"]?.toString()?.trim('"'))
        assertTrue(root.containsKey("gameConfig"))
        assertFalse(root.containsKey("totalRounds"))
        assertFalse(root.containsKey("doublingEnabled"))
        assertEquals(24, LandlordV5DiscoveryConfigCodec.decode(root.getValue("gameConfig").jsonObject)?.totalRounds)
    }

    @Test
    fun malformedJsonIsIgnoredSafely() {
        assertNull(V5DiscoveryCodec.decodeResponse("{bad json"))
    }

    @Test
    fun wrongVersionAndUnknownGameTypeAreIgnoredSafely() {
        assertNull(V5DiscoveryCodec.decodeResponse(GOLDEN_ADVERTISEMENT.replace("\"protocolVersion\":5", "\"protocolVersion\":4")))
        assertNull(V5DiscoveryCodec.decodeResponse(GOLDEN_ADVERTISEMENT.replace("\"LANDLORD\"", "\"TETRIS\"")))
    }

    @Test
    fun invalidPortPlayerCountMaxPlayersAndRoomCodeAreIgnored() {
        listOf(
            GOLDEN_ADVERTISEMENT.replace("\"hostPort\":39173", "\"hostPort\":0"),
            GOLDEN_ADVERTISEMENT.replace("\"playerCount\":2", "\"playerCount\":4"),
            GOLDEN_ADVERTISEMENT.replace("\"maxPlayers\":3", "\"maxPlayers\":0"),
            GOLDEN_ADVERTISEMENT.replace("\"roomCode\":\"804073\"", "\"roomCode\":\"bad\""),
            GOLDEN_ADVERTISEMENT.replace(",\"gameConfig\":{\"totalRounds\":12,\"doublingEnabled\":true}", ""),
            GOLDEN_ADVERTISEMENT.replace("\"type\":\"OFFLINE_GAMES_HOST_V5\",", ""),
            GOLDEN_ADVERTISEMENT.replace("\"protocolVersion\":5,", ""),
        ).forEach { invalid ->
            assertNull(V5DiscoveryCodec.decodeResponse(invalid))
        }
    }

    private companion object {
        const val GOLDEN_ADVERTISEMENT = """{"type":"OFFLINE_GAMES_HOST_V5","protocolVersion":5,"gameType":"LANDLORD","roomCode":"804073","roomName":"玩家56的房间","hostPort":39173,"playerCount":2,"maxPlayers":3,"gameConfig":{"totalRounds":12,"doublingEnabled":true}}"""
    }
}
