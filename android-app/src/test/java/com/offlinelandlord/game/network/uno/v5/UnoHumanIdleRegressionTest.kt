package com.offlinelandlord.game.network.uno.v5

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class UnoHumanIdleRegressionTest {
    @Test fun onlineRemoteHumanIdleDoesNotAdvanceTurn() = runTest {
        val fixture = startedWithCurrent(hostCurrent = false)
        val session = fixture.session
        val remoteId = fixture.currentId
        val before = session.viewFor(remoteId)!!
        advanceTimeBy(5_000); advanceTimeBy(25_000); advanceTimeBy(30_000); advanceTimeBy(240_000); runCurrent()
        assertEquals(before, session.viewFor(remoteId))
        assertEquals(remoteId, session.viewFor(remoteId)!!.game!!.currentPlayerId)
    }

    @Test fun onlineHostHumanIdleDoesNotAdvanceTurn() = runTest {
        val fixture = startedWithCurrent(hostCurrent = true)
        val session = fixture.session
        val hostId = fixture.currentId
        val before = session.viewFor(hostId)!!
        advanceTimeBy(5_000); advanceTimeBy(25_000); advanceTimeBy(30_000); advanceTimeBy(240_000); runCurrent()
        assertEquals(before, session.viewFor(hostId))
        assertEquals(hostId, session.viewFor(hostId)!!.game!!.currentPlayerId)
    }

    @Test fun disconnectIsRequiredBeforeTakeover() = runTest {
        val fixture = startedWithCurrent(hostCurrent = false)
        val session = fixture.session
        val remoteId = fixture.currentId
        val before = session.viewFor(remoteId)!!
        advanceTimeBy(300_000); runCurrent()
        assertEquals(before, session.viewFor(remoteId))
        session.disconnect(remoteId)
        val after = session.viewFor(session.hostPlayerId)!!
        assertFalse(after.players.first { it.playerId == remoteId }.connected)
        assertNotEquals(before.revision, after.revision)
    }

    @Test fun reconnectStopsFutureTakeoverActions() = runTest {
        val fixture = startedWithCurrent(hostCurrent = false)
        val session = fixture.session
        val remoteId = fixture.currentId
        val token = fixture.currentToken
        session.disconnect(remoteId)
        val resumed = session.reconnect(remoteId, token)
        assertTrue(resumed.success)
        assertTrue(resumed.value!!.room.players.first { it.playerId == remoteId }.connected)
        val before = session.viewFor(remoteId)!!
        advanceTimeBy(300_000); runCurrent()
        assertEquals(before, session.viewFor(remoteId))
        assertFalse(shouldHostAutoControlUnoSeat(isBot = false, connected = true))
    }

    @Test fun authenticatedTcpHumanCanIdleBeyondHandshakeTimeout() = runBlocking {
        val session = UnoHostSession(roomCode = "654321", hostName = "房主", config = UnoV5RoomConfig(2))
        val server = UnoV5HostServer(session, handshakeReadTimeoutMillis = 50)
        val client = UnoV5Client()
        try {
            server.start(0)
            val joined = client.connect("127.0.0.1", server.port, session.roomCode, "玩家")
            assertTrue(joined.success)
            assertNotNull(withTimeout(2_000) { client.receive() }) // initial authoritative broadcast
            delay(150)
            client.sendAction(UnoV5ActionPayload(UnoV5ActionType.READY), joined.value!!.revision)
            assertNotNull(withTimeout(2_000) { client.receive() })
            assertTrue(client.connected)
        } finally {
            client.close()
            server.close()
        }
    }

    private data class Fixture(val session: UnoHostSession, val currentId: String, val currentToken: String)

    private suspend fun startedWithCurrent(hostCurrent: Boolean): Fixture {
        repeat(500) { seed ->
            val session = UnoHostSession(hostName = "房主", config = UnoV5RoomConfig(2), random = Random(seed))
            val guest = session.join("玩家").value!!
            session.ready(session.hostPlayerId)
            session.ready(guest.playerId)
            session.startGame(session.hostPlayerId)
            val expected = if (hostCurrent) session.hostPlayerId else guest.playerId
            if (session.viewFor(expected)!!.game!!.currentPlayerId == expected) {
                return Fixture(session, expected, if (hostCurrent) session.hostResumeToken else guest.resumeToken)
            }
            session.close()
        }
        error("unable to create requested current-player fixture")
    }

}
