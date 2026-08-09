package com.offlinelandlord.game.network

import com.offlinelandlord.game.core.GameEngine
import com.offlinelandlord.game.core.PlayerAction
import com.offlinelandlord.game.core.ActionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout

class LanGameClientTest {
    @Test(timeout = 10_000)
    fun clientConnectsToTheRequestedNonZeroServerPort() = runBlocking {
        val roomCode = "654321"
        val engine = GameEngine(roomCode = roomCode, roomName = "TCP 测试房间", hostName = "房主")
        val server = LanGameServer(
            roomCode = roomCode,
            onJoin = engine::join,
            onAction = engine::applyAction,
            onDisconnect = engine::disconnect,
            viewFor = engine::viewFor,
        )
        val client = LanGameClient()

        try {
            server.start(preferredPort = 0)
            assertNotEquals(0, server.port)

            val result = client.connect("127.0.0.1", server.port, roomCode, "加入玩家")

            assertTrue(result.message, result.success)
            assertEquals(ConnectionState.CONNECTED, client.connectionState.value)
            assertEquals(roomCode, client.viewState.value?.roomCode)
        } finally {
            client.close()
            server.close()
        }
    }

    @Test
    fun clientRejectsPortZeroBeforeOpeningSocket() = runBlocking {
        val client = LanGameClient()
        try {
            val result = client.connect("127.0.0.1", 0, "654321", "加入玩家")

            assertFalse(result.success)
            assertEquals(ConnectionState.FAILED, client.connectionState.value)
            assertTrue(result.message.contains("1～65535"))
        } finally {
            client.close()
        }
    }

    @Test(timeout = 10_000)
    fun joinedClientSendsReadyActionAndHostAppliesIt() = runBlocking {
        val roomCode = "123456"
        val engine = GameEngine(roomCode = roomCode, roomName = "动作测试房间", hostName = "房主")
        val receivedAction = CompletableDeferred<PlayerAction>()
        val server = LanGameServer(
            roomCode = roomCode,
            onJoin = engine::join,
            onAction = { playerId, action, revision ->
                receivedAction.complete(action)
                engine.applyAction(playerId, action, revision)
            },
            onDisconnect = engine::disconnect,
            viewFor = engine::viewFor,
        )
        val client = LanGameClient()

        try {
            server.start(preferredPort = 0)
            assertTrue(client.connect("127.0.0.1", server.port, roomCode, "准备玩家").success)

            val sendResult = client.sendAction(PlayerAction.ready(true))
            val action = withTimeout(3_000) { receivedAction.await() }

            assertTrue(sendResult.message, sendResult.success)
            assertEquals(ActionType.SET_READY, action.type)
            assertEquals(true, action.ready)
            val hostView = requireNotNull(engine.viewFor(engine.hostPlayerId))
            assertTrue(hostView.players.first { it.name == "准备玩家" }.ready)
        } finally {
            client.close()
            server.close()
        }
    }
}
