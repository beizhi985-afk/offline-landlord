package com.offlinelandlord.game.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LandlordV4DiscoveryCodecTest {
    @Test
    fun discoveryRequestRemainsHistoricalV4Text() {
        assertEquals("DDZ_DISCOVER_V4", LandlordV4DiscoveryCodec.discoveryRequest)
        assertTrue(LandlordV4DiscoveryCodec.isDiscoveryRequest("DDZ_DISCOVER_V4"))
    }

    @Test
    fun discoveryResponseEncodingRemainsHistoricalV4Text() {
        val encoded = LandlordV4DiscoveryCodec.encodeResponse(
            roomCode = "804073",
            roomName = "玩家56的房间",
            tcpPort = 39173,
            totalRounds = 12,
            doublingEnabled = true,
        )

        assertEquals("DDZ_HOST_V4|804073|玩家56的房间|39173|12|true", encoded)
    }

    @Test
    fun historicalDiscoveryResponseStillDecodes() {
        val decoded = LandlordV4DiscoveryCodec.decodeResponse(
            "DDZ_HOST_V4|885115|玩家90的房间|39173|24|true",
        )

        assertEquals("885115", decoded?.roomCode)
        assertEquals("玩家90的房间", decoded?.roomName)
        assertEquals(39173, decoded?.tcpPort)
        assertEquals(24, decoded?.totalRounds)
        assertEquals(true, decoded?.doublingEnabled)
        assertNull(LandlordV4DiscoveryCodec.decodeResponse("DDZ_HOST_V5|885115|房间|39173|24|true"))
    }
}
