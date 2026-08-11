package com.offlinelandlord.game.network.uno.v5

import com.offlinelandlord.game.network.protocol.v5.V5ProtocolCodec
import com.offlinelandlord.game.network.protocol.v5.V5WireEnvelope
import com.offlinelandlord.game.network.protocol.v5.V5WireType
import com.offlinelandlord.game.network.protocol.v5.v5WireJson
import com.offlinelandlord.game.network.transport.LanEndpoint
import com.offlinelandlord.game.network.transport.TcpClientTransport
import com.offlinelandlord.game.shared.GameType
import java.net.ServerSocket
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Stage7A.1 acceptance drivers. They intentionally use only HostSession/TCP public APIs. */
class Stage7A1UnoLanValidationTest {
    private val json = Json { ignoreUnknownKeys = false; encodeDefaults = true }
    @Test(timeout = 180_000)
    fun hostSessionQuickPressureCompletesOneThousandMatches() = runBlocking {
        repeat(1_000) { index ->
            val playerCount = 2 + index % 3
            val humanCount = when (index % 3) {
                0 -> 1
                1 -> minOf(2, playerCount)
                else -> playerCount
            }
            val session = configuredSession(index, playerCount, humanCount, UnoV5GameMode.QUICK)
            try {
                assertTrue(session.startGame(session.hostPlayerId).success)
                driveHostSession(session, points = false, seed = 70_000 + index)
            } finally {
                session.close()
            }
        }
    }

    @Test(timeout = 180_000)
    fun hostSessionPointsPressureCompletesOneHundredMatches() = runBlocking {
        repeat(100) { index ->
            val playerCount = 2 + index % 3
            val humanCount = if (index % 2 == 0) minOf(2, playerCount) else playerCount
            val session = configuredSession(index, playerCount, humanCount, UnoV5GameMode.POINTS_500)
            try {
                assertTrue(session.startGame(session.hostPlayerId).success)
                driveHostSession(session, points = true, seed = 80_000 + index)
            } finally {
                session.close()
            }
        }
    }

    @Test(timeout = 120_000)
    fun reconnectPressurePreservesIdentityAcrossTwoHundredSessions() = runBlocking {
        repeat(200) { index ->
            val session = UnoHostSession(
                roomCode = "%06d".format(200_000 + index),
                hostName = "Host",
                config = UnoV5RoomConfig(2),
                random = Random(90_000 + index),
            )
            try {
                val guest = session.join("Remote").value ?: error("join failed at $index")
                assertTrue(session.ready(session.hostPlayerId).success)
                assertTrue(session.ready(guest.playerId).success)
                assertTrue(session.startGame(session.hostPlayerId).success)
                val before = requireNotNull(session.viewFor(guest.playerId))
                assertTrue(session.disconnect(guest.playerId).success)
                if (index % 5 == 0) {
                    assertEquals(
                        UnoV5ErrorCode.INVALID_RESUME_TOKEN,
                        session.reconnect(guest.playerId, "wrong-$index").error,
                    )
                }
                val resumed = session.reconnect(guest.playerId, guest.resumeToken)
                assertTrue(resumed.success)
                val after = requireNotNull(resumed.value)
                assertEquals(guest.playerId, after.playerId)
                assertEquals(guest.resumeToken, after.resumeToken)
                val player = after.room.players.single { it.playerId == guest.playerId }
                assertEquals(before.players.single { it.playerId == guest.playerId }.seatIndex, player.seatIndex)
                assertTrue(player.connected)
                assertEquals(before.game?.scores?.keys, after.room.game?.scores?.keys)
            } finally {
                session.close()
            }
        }
    }

    @Test(timeout = 120_000)
    fun tcpSessionLifecyclePressureCompletesOneHundredCycles() = runBlocking {
        repeat(100) { index ->
            val session = UnoHostSession(
                roomCode = "%06d".format(300_000 + index),
                hostName = "Host",
                config = UnoV5RoomConfig(2),
                random = Random(100_000 + index),
            )
            val server = UnoV5HostServer(session)
            val client = TcpClientTransport()
            var usedPort = 0
            try {
                server.start(0)
                usedPort = server.port
                client.connect(LanEndpoint("127.0.0.1", usedPort), readTimeoutMillis = 3_000)
                client.send(joinEnvelope(session.roomCode, "Remote").wire())
                val accepted = awaitType(client, V5WireType.JOIN_ACCEPTED)
                assertEquals(GameType.UNO, accepted.gameType)
                assertEquals(session.roomCode, accepted.roomCode)
            } finally {
                client.close()
                server.close()
            }
            ServerSocket(usedPort).use { rebound -> assertEquals(usedPort, rebound.localPort) }
        }
    }

    @Test(timeout = 60_000)
    fun twoHumanTcpQuickMatchFinishesAndKeepsHandsPrivate() = runBlocking {
        runTcpQuickMatch(remoteHuman = true, botCount = 0, expectedPlayers = 2)
    }

    @Test(timeout = 60_000)
    fun humanAndBotTcpQuickMatchFinishes() = runBlocking {
        runTcpQuickMatch(remoteHuman = false, botCount = 1, expectedPlayers = 2)
    }

    @Test(timeout = 90_000)
    fun fourSeatMixedTcpQuickMatchFinishesWithStableSeats() = runBlocking {
        runTcpQuickMatch(remoteHuman = true, botCount = 2, expectedPlayers = 4)
    }

    private suspend fun configuredSession(index: Int, playerCount: Int, humanCount: Int, mode: UnoV5GameMode): UnoHostSession {
        val session = UnoHostSession(
            roomCode = "%06d".format(400_000 + index),
            hostName = "Host",
            config = UnoV5RoomConfig(playerCount, mode),
            random = Random(50_000 + index),
        )
        val humans = mutableListOf(session.hostPlayerId)
        repeat(humanCount - 1) { seat -> humans += session.join("Human-$seat").value!!.playerId }
        repeat(playerCount - humanCount) { session.addBot(session.hostPlayerId) }
        humans.forEach { assertTrue(session.ready(it).success) }
        return session
    }

    private suspend fun driveHostSession(session: UnoHostSession, points: Boolean, seed: Int) {
        var previousRevision = session.currentRevision()
        var actions = 0
        while (true) {
            val room = requireNotNull(session.viewFor(session.hostPlayerId))
            assertStablePlayers(room)
            val game = room.game ?: error("game did not start, seed=$seed")
            if (game.phase == "MATCH_FINISHED") {
                assertNotNull(game.matchWinnerId)
                if (points) assertTrue(game.scores.values.any { it >= 500 })
                return
            }
            val actor = room.players.firstOrNull { it.playerId == game.currentPlayerId && !it.isBot }
                ?: room.players.firstOrNull { !it.isBot }
                ?: error("no human actor, seed=$seed")
            val actorRoom = requireNotNull(session.viewFor(actor.playerId))
            val actorGame = requireNotNull(actorRoom.game)
            val action = choosePublicAction(actorGame)
            val before = session.currentRevision()
            val result = session.submitAction(actor.playerId, "pressure-$seed-$actions", before, action)
            assertTrue("failed iteration=$actions seed=$seed detail=${result.detail}", result.success)
            val after = session.currentRevision()
            assertTrue(after > previousRevision)
            previousRevision = after
            actions++
            assertTrue("action limit seed=$seed", actions < 20_000)
        }
    }

    private suspend fun runTcpQuickMatch(remoteHuman: Boolean, botCount: Int, expectedPlayers: Int) {
        val session = UnoHostSession(
            roomCode = "512345",
            hostName = "Host",
            config = UnoV5RoomConfig(expectedPlayers),
            random = Random(20260811 + expectedPlayers),
        )
        val server = UnoV5HostServer(session)
        val host = TcpClientTransport()
        val remote = TcpClientTransport()
        try {
            server.start(0)
            host.connect(LanEndpoint("127.0.0.1", server.port), readTimeoutMillis = 5_000)
            host.send(hostResumeEnvelope(session).wire())
            val hostAccepted = awaitType(host, V5WireType.JOIN_ACCEPTED)
            assertEquals(session.hostPlayerId, hostAccepted.playerId)

            if (remoteHuman) {
                remote.connect(LanEndpoint("127.0.0.1", server.port), readTimeoutMillis = 5_000)
                remote.send(joinEnvelope(session.roomCode, "Remote").wire())
                awaitType(remote, V5WireType.JOIN_ACCEPTED)
            }
            var hostRoom = awaitStateFor(host, session.hostPlayerId, session.currentRevision())
            var remoteRoom: UnoV5RoomView? = if (remoteHuman) awaitStateFor(remote, null, session.currentRevision()) else null

            repeat(botCount) {
                val before = session.currentRevision()
                host.send(actionEnvelope(hostRoom, UnoV5ActionPayload(UnoV5ActionType.ADD_BOT), "add-bot-$it", expectedRevision = before).wire())
                hostRoom = awaitStateFor(host, session.hostPlayerId, awaitRevisionGreater(session, before))
            }
            var before = session.currentRevision()
            host.send(actionEnvelope(hostRoom, UnoV5ActionPayload(UnoV5ActionType.READY), "host-ready", expectedRevision = before).wire())
            hostRoom = awaitStateFor(host, session.hostPlayerId, awaitRevisionGreater(session, before))
            if (remoteHuman) {
                val remoteId = remoteRoom!!.game?.selfPlayerId ?: remoteRoom.players.first { it.playerId != session.hostPlayerId }.playerId
                before = session.currentRevision()
                remote.send(actionEnvelope(remoteRoom!!, UnoV5ActionPayload(UnoV5ActionType.READY), "remote-ready", remoteId, before).wire())
                remoteRoom = awaitStateFor(remote, remoteId, awaitRevisionGreater(session, before))
                hostRoom = awaitStateFor(host, session.hostPlayerId, remoteRoom!!.revision)
            }
            before = session.currentRevision()
            host.send(actionEnvelope(hostRoom, UnoV5ActionPayload(UnoV5ActionType.START_GAME), "start-game", expectedRevision = before).wire())
            hostRoom = awaitStateFor(host, session.hostPlayerId, awaitRevisionGreater(session, before))
            if (remoteHuman) remoteRoom = awaitStateFor(remote, remoteRoom!!.game?.selfPlayerId, hostRoom.revision)
            assertStablePlayers(hostRoom)

            val remoteId = remoteRoom?.game?.selfPlayerId
                ?: remoteRoom?.players?.firstOrNull { it.playerId != session.hostPlayerId }?.playerId
            var duplicateChecked = false
            var staleChecked = false
            var actions = 0
            while (hostRoom.game?.phase != "MATCH_FINISHED") {
                assertPrivateState(hostRoom, session.hostPlayerId)
                if (remoteRoom != null) assertPrivateState(remoteRoom!!, remoteId)
                val hostGame = requireNotNull(hostRoom.game)
                val remoteGame = remoteRoom?.game
                val catchSource = when {
                    remoteGame != null && UnoV5ActionType.CATCH_UNO in remoteGame.legalActions -> remote
                    UnoV5ActionType.CATCH_UNO in hostGame.legalActions -> host
                    else -> null
                }
                val actorId = when {
                    catchSource === remote -> requireNotNull(remoteId)
                    catchSource === host -> session.hostPlayerId
                    hostGame.currentPlayerId == session.hostPlayerId -> session.hostPlayerId
                    remoteGame?.currentPlayerId == remoteId -> requireNotNull(remoteId)
                    else -> error("no network human turn")
                }
                val actorClient = if (actorId == session.hostPlayerId) host else remote
                val actorRoom = if (actorId == session.hostPlayerId) hostRoom else requireNotNull(remoteRoom)
                val action = choosePublicAction(requireNotNull(actorRoom.game))
                val before = session.currentRevision()
                assertTrue("wire revision must not regress", actorRoom.revision >= before)
                if (!staleChecked && action.action !in setOf(UnoV5ActionType.CATCH_UNO, UnoV5ActionType.DECLARE_UNO)) {
                    actorClient.send(actionEnvelope(actorRoom, action, "stale-$actions", actorId, before - 1).wire())
                    val error = awaitType(actorClient, V5WireType.ERROR)
                    assertEquals("STALE_REVISION", errorPayload(error).code)
                    awaitStateFor(actorClient, actorId, before)
                    staleChecked = true
                }
                val requestId = if (!duplicateChecked) "duplicate-action" else "action-$actions"
                actorClient.send(actionEnvelope(actorRoom, action, requestId, actorId, before).wire())
                val expectedRevision = before + 1
                var observedRevision = expectedRevision
                var duplicateRoom: UnoV5RoomView? = null
                if (!duplicateChecked && requestId == "duplicate-action") {
                    val first = awaitStateFor(actorClient, actorId, awaitRevisionGreater(session, before))
                    actorClient.send(actionEnvelope(actorRoom, action, requestId, actorId, before).wire())
                    val second = awaitStateFor(actorClient, actorId, first.revision)
                    assertEquals(first.revision, second.revision)
                    observedRevision = second.revision
                    duplicateRoom = second
                    duplicateChecked = true
                }
                if (actorId == session.hostPlayerId) {
                    hostRoom = duplicateRoom ?: awaitStateFor(host, session.hostPlayerId, observedRevision)
                    if (remoteHuman) remoteRoom = awaitStateFor(remote, remoteId, hostRoom.revision)
                } else {
                    remoteRoom = duplicateRoom ?: awaitStateFor(remote, remoteId, observedRevision)
                    hostRoom = awaitStateFor(host, session.hostPlayerId, remoteRoom!!.revision)
                }
                actions++
                assertStablePlayers(hostRoom)
                assertTrue("TCP action limit", actions < 20_000)
            }
            assertTrue("stale revision rejection was not exercised", staleChecked)
            assertTrue("duplicate request idempotency was not exercised", duplicateChecked)
            assertNotNull(hostRoom.game?.matchWinnerId)
            assertEquals("MATCH_FINISHED", hostRoom.game?.phase)
        } finally {
            remote.close()
            host.close()
            server.close()
        }
    }

    private fun choosePublicAction(game: UnoV5GameView): UnoV5ActionPayload = when {
        UnoV5ActionType.CATCH_UNO in game.legalActions -> UnoV5ActionPayload(UnoV5ActionType.CATCH_UNO, targetPlayerId = game.catchTargetPlayerId)
        UnoV5ActionType.DECLARE_UNO in game.legalActions -> UnoV5ActionPayload(UnoV5ActionType.DECLARE_UNO)
        UnoV5ActionType.CHOOSE_COLOR in game.legalActions -> UnoV5ActionPayload(UnoV5ActionType.CHOOSE_COLOR, color = "RED")
        UnoV5ActionType.PLAY_CARD in game.legalActions -> UnoV5ActionPayload(UnoV5ActionType.PLAY_CARD, cardId = game.legalPlayableCardIds.first())
        UnoV5ActionType.PLAY_DRAWN_CARD in game.legalActions -> UnoV5ActionPayload(UnoV5ActionType.PLAY_DRAWN_CARD, cardId = game.drawnCardId)
        UnoV5ActionType.PASS_AFTER_DRAW in game.legalActions -> UnoV5ActionPayload(UnoV5ActionType.PASS_AFTER_DRAW)
        UnoV5ActionType.DRAW_CARD in game.legalActions -> UnoV5ActionPayload(UnoV5ActionType.DRAW_CARD)
        UnoV5ActionType.START_NEXT_ROUND in game.legalActions -> UnoV5ActionPayload(UnoV5ActionType.START_NEXT_ROUND)
        else -> error("no legal public action in phase=${game.phase}")
    }

    private fun assertStablePlayers(room: UnoV5RoomView) {
        assertEquals(room.maxPlayers, room.players.size)
        assertEquals(room.players.size, room.players.map { it.seatIndex }.distinct().size)
        assertEquals(room.players.size, room.players.map { it.playerId }.distinct().size)
    }

    private fun assertPrivateState(room: UnoV5RoomView, selfId: String?) {
        val game = room.game ?: return
        if (selfId != null) assertEquals(selfId, game.selfPlayerId)
        game.players.filter { it.playerId != game.selfPlayerId }.forEach { assertTrue(it.hand.isEmpty()) }
        assertTrue(game.ownHand.map { it.cardId }.distinct().size == game.ownHand.size)
    }

    private fun hostResumeEnvelope(session: UnoHostSession): V5WireEnvelope = V5WireEnvelope(
        gameType = com.offlinelandlord.game.shared.GameType.UNO,
        type = V5WireType.JOIN,
        requestId = "host-resume",
        playerId = session.hostPlayerId,
        roomCode = session.roomCode,
        resumeToken = session.hostResumeToken,
        payload = UnoV5PayloadCodec.encodeJoin("Host"),
    )

    private fun joinEnvelope(roomCode: String, displayName: String): V5WireEnvelope = UnoV5PayloadCodec.envelope(
        V5WireType.JOIN,
        UnoV5PayloadCodec.encodeJoin(displayName),
        requestId = "join-$displayName",
        roomCode = roomCode,
    )

    private fun actionEnvelope(
        room: UnoV5RoomView,
        action: UnoV5ActionPayload,
        requestId: String,
        playerId: String = room.game?.selfPlayerId ?: room.hostPlayerId,
        expectedRevision: Long? = room.revision,
    ): V5WireEnvelope = UnoV5PayloadCodec.envelope(
        V5WireType.ACTION,
        UnoV5PayloadCodec.encodeAction(action),
        requestId,
        playerId,
        room.roomCode,
        expectedRevision = expectedRevision,
    )

    private fun V5WireEnvelope.wire(): String = v5WireJson.encodeToString(V5WireEnvelope.serializer(), this)

    private fun errorPayload(envelope: V5WireEnvelope): UnoV5ErrorPayload = json.decodeFromJsonElement(
        UnoV5ErrorPayload.serializer(),
        requireNotNull(envelope.payload),
    )

    private suspend fun awaitType(client: TcpClientTransport, expected: V5WireType): V5WireEnvelope = withTimeout(5_000) {
        while (true) {
            val envelope = V5ProtocolCodec.decode(client.receive() ?: error("TCP closed before $expected")) ?: continue
            if (envelope.type == expected) return@withTimeout envelope
        }
        error("unreachable")
    }

    private suspend fun awaitStateFor(client: TcpClientTransport, selfId: String?, minimumRevision: Long): UnoV5RoomView = withTimeout(5_000) {
        while (true) {
            val envelope = V5ProtocolCodec.decode(client.receive() ?: error("TCP closed before STATE")) ?: continue
            if (envelope.type != V5WireType.STATE) continue
            val room = UnoV5PayloadCodec.decodeRoom(envelope.payload) ?: continue
            if (room.revision >= minimumRevision) {
                assertPrivateState(room, selfId)
                return@withTimeout room
            }
        }
        error("unreachable")
    }

    private suspend fun awaitRevisionGreater(session: UnoHostSession, previous: Long): Long = withTimeout(5_000) {
        while (true) {
            val current = session.currentRevision()
            if (current > previous) return@withTimeout current
            delay(1)
        }
        error("unreachable")
    }
}
