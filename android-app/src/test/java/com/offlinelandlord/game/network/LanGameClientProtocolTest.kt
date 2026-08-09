package com.offlinelandlord.game.network

import com.offlinelandlord.game.core.GameEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LanGameClientProtocolTest {
    @Test
    fun newestLandlordClientDefaultsToV5() {
        LanGameClient().use { client ->
            assertEquals(LandlordProtocolVersion.V5, client.protocol)
        }
    }

    @Test(timeout = 10_000)
    fun roomDiscoveredFromV4AdvertisementUsesTheV4CompatibilityClient() = runBlocking {
        val roomCode = "654321"
        val engine = GameEngine(roomCode, "V4发现房间", "房主")
        val server = LanGameServer(
            roomCode = roomCode,
            onJoin = engine::join,
            onAction = engine::applyAction,
            onDisconnect = engine::disconnect,
            viewFor = engine::viewFor,
        )
        val discoveredRoom = DiscoveredRoom(
            host = "127.0.0.1",
            port = 0,
            roomCode = roomCode,
            roomName = "V4发现房间",
            totalRounds = 12,
            doublingEnabled = true,
            protocol = LandlordProtocolVersion.V4,
        )
        val client = LanGameClient.forDiscoveredRoom(discoveredRoom)
        try {
            server.start(0)
            val result = client.connect(
                discoveredRoom.host,
                server.port,
                discoveredRoom.roomCode,
                "旧客户端",
            )

            assertEquals(LandlordProtocolVersion.V4, client.protocol)
            assertTrue(result.message, result.success)
            assertEquals(roomCode, client.viewState.value?.roomCode)
        } finally {
            client.close()
            server.close()
        }
    }
}
