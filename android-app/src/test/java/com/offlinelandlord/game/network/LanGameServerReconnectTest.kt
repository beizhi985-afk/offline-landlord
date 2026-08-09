package com.offlinelandlord.game.network

import com.offlinelandlord.game.core.GameEngine
import com.offlinelandlord.game.core.PlayerAction
import com.offlinelandlord.game.network.transport.LanEndpoint
import com.offlinelandlord.game.network.transport.TcpClientTransport
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LanGameServerReconnectTest {
    @Test(timeout = 10_000)
    fun resumedConnectionKeepsPlayerIdentityAndSupersedesOldConnection() = runBlocking {
        val roomCode = "456789"
        val engine = GameEngine(roomCode, "重连测试", "房主")
        val server = LanGameServer(
            roomCode = roomCode,
            onJoin = engine::join,
            onAction = engine::applyAction,
            onDisconnect = engine::disconnect,
            viewFor = engine::viewFor,
        )
        val first = TcpClientTransport()
        val resumed = TcpClientTransport()
        try {
            server.start(0)
            first.connect(LanEndpoint("127.0.0.1", server.port), readTimeoutMillis = 3_000)
            first.send(joinMessage(roomCode, "首次玩家", null, "join-first"))
            val firstAccepted = decode(requireNotNull(first.receive()))

            resumed.connect(LanEndpoint("127.0.0.1", server.port), readTimeoutMillis = 3_000)
            resumed.send(joinMessage(roomCode, "重连玩家", firstAccepted.resumeToken, "join-resumed"))
            val resumedAccepted = decode(requireNotNull(resumed.receive()))

            assertEquals(firstAccepted.playerId, resumedAccepted.playerId)
            assertEquals(firstAccepted.resumeToken, resumedAccepted.resumeToken)
            delay(150)
            val playerId = requireNotNull(resumedAccepted.playerId)
            val viewAfterReconnect = requireNotNull(engine.viewFor(playerId))
            assertEquals(2, viewAfterReconnect.players.size)
            assertTrue(viewAfterReconnect.players.single { it.id == playerId }.connected)

            resumed.send(
                wireJson.encodeToString(
                    WireEnvelope.serializer(),
                    WireEnvelope(
                        type = WireType.ACTION,
                        requestId = "ready-after-resume",
                        playerId = playerId,
                        expectedRevision = viewAfterReconnect.revision,
                        action = PlayerAction.ready(true),
                    ),
                ),
            )
            withTimeout(3_000) {
                while (!requireNotNull(engine.viewFor(playerId)).players.single { it.id == playerId }.ready) {
                    delay(10)
                }
            }
        } finally {
            first.close()
            resumed.close()
            server.close()
        }
    }

    private fun joinMessage(roomCode: String, name: String, resumeToken: String?, requestId: String): String =
        wireJson.encodeToString(
            WireEnvelope.serializer(),
            WireEnvelope(
                type = WireType.JOIN,
                protocolVersion = WIRE_PROTOCOL_VERSION,
                requestId = requestId,
                playerName = name,
                roomCode = roomCode,
                resumeToken = resumeToken,
            ),
        )

    private fun decode(message: String): WireEnvelope =
        wireJson.decodeFromString(WireEnvelope.serializer(), message)
}
