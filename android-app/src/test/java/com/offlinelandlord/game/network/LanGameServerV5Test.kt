package com.offlinelandlord.game.network

import com.offlinelandlord.game.core.GameEngine
import com.offlinelandlord.game.core.PlayerAction
import com.offlinelandlord.game.network.landlord.v5.LandlordV5PayloadCodec
import com.offlinelandlord.game.network.protocol.v5.V5ProtocolCodec
import com.offlinelandlord.game.network.protocol.v5.V5WireEnvelope
import com.offlinelandlord.game.network.protocol.v5.V5WireType
import com.offlinelandlord.game.network.protocol.v5.v5WireJson
import com.offlinelandlord.game.network.transport.LanEndpoint
import com.offlinelandlord.game.network.transport.TcpClientTransport
import com.offlinelandlord.game.shared.GameType
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LanGameServerV5Test {
    @Test(timeout = 12_000)
    fun v5LandlordJoinIsAcceptedAndUnoJoinIsExplicitlyRejected() = runBlocking {
        val roomCode = "123456"
        val engine = GameEngine(roomCode, "V5测试", "房主")
        val server = server(roomCode, engine)
        val landlord = TcpClientTransport()
        val unsupported = TcpClientTransport()
        try {
            server.start(0)
            landlord.connect(LanEndpoint("127.0.0.1", server.port), readTimeoutMillis = 3_000)
            landlord.send(v5Join(roomCode, "V5玩家"))
            val accepted = awaitV5(landlord, V5WireType.JOIN_ACCEPTED)

            assertEquals(5, accepted.protocolVersion)
            assertEquals(GameType.LANDLORD, accepted.gameType)
            assertEquals(roomCode, accepted.roomCode)
            assertEquals("V5玩家", LandlordV5PayloadCodec.decodeJoinAccepted(accepted.payload)?.view?.players?.last()?.name)

            unsupported.connect(LanEndpoint("127.0.0.1", server.port), readTimeoutMillis = 3_000)
            unsupported.send(
                v5WireJson.encodeToString(
                    V5WireEnvelope.serializer(),
                    V5WireEnvelope(
                        gameType = GameType.UNO,
                        type = V5WireType.JOIN,
                        roomCode = roomCode,
                        payload = LandlordV5PayloadCodec.encodeJoin("不应加入"),
                    ),
                ),
            )
            val error = awaitV5(unsupported, V5WireType.ERROR)
            assertEquals(GameType.UNO, error.gameType)
            assertTrue(error.message.orEmpty().contains("暂不支持"))
        } finally {
            landlord.close()
            unsupported.close()
            server.close()
        }
    }

    @Test(timeout = 12_000)
    fun v5ActionUpdatesStateRevisionThroughRealTcpTransport() = runBlocking {
        val roomCode = "123456"
        val engine = GameEngine(roomCode, "动作测试", "房主")
        lateinit var server: LanGameServer
        server = LanGameServer(
            roomCode = roomCode,
            onJoin = engine::join,
            onAction = { playerId, action, revision ->
                engine.applyAction(playerId, action, revision).also { result ->
                    if (result.success) server.broadcastStates()
                }
            },
            onDisconnect = engine::disconnect,
            viewFor = engine::viewFor,
        )
        val client = TcpClientTransport()
        try {
            server.start(0)
            client.connect(LanEndpoint("127.0.0.1", server.port), readTimeoutMillis = 3_000)
            client.send(v5Join(roomCode, "准备玩家"))
            val accepted = awaitV5(client, V5WireType.JOIN_ACCEPTED)
            val playerId = requireNotNull(accepted.playerId)
            val initialRevision = requireNotNull(
                LandlordV5PayloadCodec.decodeJoinAccepted(accepted.payload)?.view?.revision,
            )

            client.send(v5Action(playerId, initialRevision, PlayerAction.ready(true)))
            val updated = awaitV5StateAfter(client, initialRevision)

            assertTrue(updated.revision > initialRevision)
            assertTrue(requireNotNull(engine.viewFor(playerId)).players.single { it.id == playerId }.ready)
        } finally {
            client.close()
            server.close()
        }
    }

    @Test(timeout = 12_000)
    fun v5ResumeTokenRestoresTheSamePlayerWithoutAddingAnotherSeat() = runBlocking {
        val roomCode = "456789"
        val engine = GameEngine(roomCode, "V5重连", "房主")
        val server = server(roomCode, engine)
        val first = TcpClientTransport()
        val resumed = TcpClientTransport()
        try {
            server.start(0)
            first.connect(LanEndpoint("127.0.0.1", server.port), readTimeoutMillis = 3_000)
            first.send(v5Join(roomCode, "首次玩家"))
            val firstAccepted = awaitV5(first, V5WireType.JOIN_ACCEPTED)

            resumed.connect(LanEndpoint("127.0.0.1", server.port), readTimeoutMillis = 3_000)
            resumed.send(v5Join(roomCode, "重连玩家", firstAccepted.resumeToken))
            val resumedAccepted = awaitV5(resumed, V5WireType.JOIN_ACCEPTED)
            val playerId = requireNotNull(resumedAccepted.playerId)
            val resumeView = requireNotNull(LandlordV5PayloadCodec.decodeJoinAccepted(resumedAccepted.payload)?.view)

            assertEquals(firstAccepted.playerId, resumedAccepted.playerId)
            assertEquals(firstAccepted.resumeToken, resumedAccepted.resumeToken)
            assertEquals(2, requireNotNull(engine.viewFor(playerId)).players.size)
            withTimeout(3_000) {
                var bufferedMessage: String?
                do {
                    bufferedMessage = first.receive()
                } while (bufferedMessage != null)
            }

            resumed.send(v5Action(playerId, resumeView.revision, PlayerAction.ready(true)))
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

    @Test(timeout = 12_000)
    fun historicalV4ClientCanJoinTheNewDualProtocolServer() = runBlocking {
        val roomCode = "654321"
        val engine = GameEngine(roomCode, "V4兼容", "房主")
        val server = server(roomCode, engine)
        val client = TcpClientTransport()
        try {
            server.start(0)
            client.connect(LanEndpoint("127.0.0.1", server.port), readTimeoutMillis = 3_000)
            client.send(v4Join(roomCode, "旧客户端"))
            val accepted = awaitV4(client, WireType.JOIN_ACCEPTED)

            assertEquals(WIRE_PROTOCOL_VERSION, accepted.protocolVersion)
            assertEquals("旧客户端", accepted.view?.players?.last()?.name)
        } finally {
            client.close()
            server.close()
        }
    }

    @Test(timeout = 12_000)
    fun mixedV4AndV5ClientsReceiveStatesInTheirOwnWireFormat() = runBlocking {
        val roomCode = "987654"
        val engine = GameEngine(roomCode, "混合协议", "房主")
        val server = server(roomCode, engine)
        val v4Client = TcpClientTransport()
        val v5Client = TcpClientTransport()
        try {
            server.start(0)
            v4Client.connect(LanEndpoint("127.0.0.1", server.port), readTimeoutMillis = 3_000)
            v4Client.send(v4Join(roomCode, "V4玩家"))
            awaitV4(v4Client, WireType.JOIN_ACCEPTED)

            v5Client.connect(LanEndpoint("127.0.0.1", server.port), readTimeoutMillis = 3_000)
            v5Client.send(v5Join(roomCode, "V5玩家"))
            awaitV5(v5Client, V5WireType.JOIN_ACCEPTED)

            val v4State = awaitV4(v4Client, WireType.STATE)
            val v5State = awaitV5(v5Client, V5WireType.STATE)

            assertEquals(WireType.STATE, v4State.type)
            assertEquals(V5WireType.STATE, v5State.type)
            assertEquals(GameType.LANDLORD, v5State.gameType)
            assertTrue(LandlordV5PayloadCodec.decodeView(v5State.payload)?.players?.any { it.name == "V4玩家" } == true)
        } finally {
            v4Client.close()
            v5Client.close()
            server.close()
        }
    }

    private fun server(roomCode: String, engine: GameEngine): LanGameServer = LanGameServer(
        roomCode = roomCode,
        onJoin = engine::join,
        onAction = engine::applyAction,
        onDisconnect = engine::disconnect,
        viewFor = engine::viewFor,
    )

    private fun v5Join(roomCode: String, name: String, resumeToken: String? = null): String =
        v5WireJson.encodeToString(
            V5WireEnvelope.serializer(),
            V5WireEnvelope(
                gameType = GameType.LANDLORD,
                type = V5WireType.JOIN,
                roomCode = roomCode,
                resumeToken = resumeToken,
                payload = LandlordV5PayloadCodec.encodeJoin(name),
            ),
        )

    private fun v5Action(playerId: String, revision: Long, action: PlayerAction): String =
        v5WireJson.encodeToString(
            V5WireEnvelope.serializer(),
            V5WireEnvelope(
                gameType = GameType.LANDLORD,
                type = V5WireType.ACTION,
                playerId = playerId,
                expectedRevision = revision,
                payload = LandlordV5PayloadCodec.encodeAction(action),
            ),
        )

    private fun v4Join(roomCode: String, name: String): String = wireJson.encodeToString(
        WireEnvelope.serializer(),
        WireEnvelope(
            type = WireType.JOIN,
            protocolVersion = WIRE_PROTOCOL_VERSION,
            requestId = "v4-join",
            playerName = name,
            roomCode = roomCode,
        ),
    )

    private suspend fun awaitV5(client: TcpClientTransport, expectedType: V5WireType): V5WireEnvelope {
        repeat(6) {
            val envelope = requireNotNull(V5ProtocolCodec.decode(requireNotNull(client.receive())))
            if (envelope.type == expectedType) return envelope
        }
        error("没有收到 V5 $expectedType")
    }

    private suspend fun awaitV5StateAfter(client: TcpClientTransport, revision: Long) = withTimeout(3_000) {
        while (true) {
            val envelope = requireNotNull(V5ProtocolCodec.decode(requireNotNull(client.receive())))
            if (envelope.type == V5WireType.STATE) {
                val view = LandlordV5PayloadCodec.decodeView(envelope.payload)
                if (view != null && view.revision > revision) return@withTimeout view
            }
        }
        error("unreachable")
    }

    private suspend fun awaitV4(client: TcpClientTransport, expectedType: WireType): WireEnvelope {
        repeat(6) {
            val envelope = wireJson.decodeFromString(WireEnvelope.serializer(), requireNotNull(client.receive()))
            if (envelope.type == expectedType) return envelope
        }
        error("没有收到 V4 $expectedType")
    }
}
