package com.offlinelandlord.game.network.uno.v5

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UnoV5HostServerTest {
    @Test fun tcpJoinReturnsPrivateRoomView() = runBlocking {
        val session = UnoHostSession(roomCode = "123456", hostName = "Host", config = UnoV5RoomConfig(2))
        val server = UnoV5HostServer(session)
        val client = UnoV5Client()
        try {
            server.start(0)
            val result = client.connect("127.0.0.1", server.port, "123456", "Guest")
            assertTrue(result.success)
            assertEquals(2, result.value!!.players.size)
            assertTrue(result.value.players.any { it.playerId == client.playerId })
        } finally {
            client.close(); server.close()
        }
    }

    @Test fun tcpWrongRoomIsRejected() = runBlocking {
        val session = UnoHostSession(roomCode = "123456", hostName = "Host")
        val server = UnoV5HostServer(session); val client = UnoV5Client()
        try {
            server.start(0)
            val result = client.connect("127.0.0.1", server.port, "654321", "Guest")
            assertEquals(UnoV5ErrorCode.ROOM_NOT_FOUND, result.error)
        } finally {
            client.close(); server.close()
        }
    }

    @Test fun discoveryAdvertisementIsUnoV5() {
        val room = UnoV5RoomView("123456", "host", emptyList(), UnoV5GameMode.QUICK, 2, UnoV5RoomStatus.WAITING, 0)
        val advertisement = UnoV5Discovery.advertisement(room, "UNO", 39173)
        assertEquals(com.offlinelandlord.game.shared.GameType.UNO, advertisement.gameType)
        assertEquals(5, advertisement.protocolVersion)
        assertEquals(39173, advertisement.hostPort)
    }
}
