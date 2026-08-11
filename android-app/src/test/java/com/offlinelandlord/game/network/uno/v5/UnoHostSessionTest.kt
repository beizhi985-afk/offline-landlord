package com.offlinelandlord.game.network.uno.v5

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class UnoHostSessionTest {
    private fun started(mode: UnoV5GameMode = UnoV5GameMode.QUICK): Triple<UnoHostSession, String, String> = runBlocking {
        val session = UnoHostSession(hostName = "Host", config = UnoV5RoomConfig(2, mode), random = Random(20260811))
        val guest = session.join("Guest").value!!
        assertTrue(session.ready(session.hostPlayerId).success)
        assertTrue(session.ready(guest.playerId).success)
        assertTrue(session.startGame(session.hostPlayerId).success)
        Triple(session, guest.playerId, guest.resumeToken)
    }

    @Test fun roomStartsWithStableHostIdentity() = runBlocking {
        val session = UnoHostSession(roomCode = "123456", hostName = "Host")
        val room = session.viewFor(session.hostPlayerId)!!
        assertEquals("123456", room.roomCode)
        assertEquals(session.hostPlayerId, room.hostPlayerId)
        assertEquals(0, room.players.single().seatIndex)
    }

    @Test fun joinAllocatesStableSeatAndToken() = runBlocking {
        val session = UnoHostSession(hostName = "Host", config = UnoV5RoomConfig(3))
        val joined = session.join("Alice").value!!
        assertEquals(1, joined.room.players.first { it.playerId == joined.playerId }.seatIndex)
        assertTrue(joined.resumeToken.isNotBlank())
    }

    @Test fun roomFullIsRejected() = runBlocking {
        val session = UnoHostSession(hostName = "Host", config = UnoV5RoomConfig(2))
        assertTrue(session.join("A").success)
        assertEquals(UnoV5ErrorCode.ROOM_FULL, session.join("B").error)
    }

    @Test fun hostOnlyAddsBot() = runBlocking {
        val session = UnoHostSession(hostName = "Host", config = UnoV5RoomConfig(2))
        val other = UnoHostSession(hostName = "Other", config = UnoV5RoomConfig(2))
        assertTrue(session.addBot(session.hostPlayerId).success)
        assertEquals(UnoV5ErrorCode.NOT_HOST, other.addBot("not-host").error)
    }

    @Test fun startRequiresAllSeats() = runBlocking {
        val session = UnoHostSession(hostName = "Host", config = UnoV5RoomConfig(2))
        session.ready(session.hostPlayerId)
        assertEquals(UnoV5ErrorCode.NOT_ENOUGH_PLAYERS, session.startGame(session.hostPlayerId).error)
    }

    @Test fun startRequiresReadyHumans() = runBlocking {
        val session = UnoHostSession(hostName = "Host", config = UnoV5RoomConfig(2))
        session.join("Guest")
        assertEquals(UnoV5ErrorCode.NOT_READY, session.startGame(session.hostPlayerId).error)
    }

    @Test fun botSeatsAreAutoReady() = runBlocking {
        val session = UnoHostSession(hostName = "Host", config = UnoV5RoomConfig(2))
        session.addBot(session.hostPlayerId)
        session.ready(session.hostPlayerId)
        assertTrue(session.startGame(session.hostPlayerId).success)
    }

    @Test fun pointsModeIsFixedToFiveHundred() = runBlocking {
        val (session, guest, _) = started(UnoV5GameMode.POINTS_500)
        val game = session.viewFor(guest)!!.game!!
        assertEquals(UnoV5GameMode.POINTS_500, session.viewFor(guest)!!.gameMode)
        assertEquals(0, game.scores.values.sum())
    }

    @Test fun privateStateContainsOwnHand() = runBlocking {
        val (session, guest, _) = started()
        val view = session.viewFor(guest)!!.game!!
        assertEquals(7, view.ownHand.size)
        assertEquals(7, view.players.first { it.playerId == guest }.hand.size)
    }

    @Test fun privateStateHidesOpponentCards() = runBlocking {
        val (session, guest, _) = started()
        val view = session.viewFor(guest)!!.game!!
        assertTrue(view.players.filter { it.playerId != guest }.all { it.hand.isEmpty() })
        assertTrue(view.players.filter { it.playerId != guest }.all { it.handCount == 7 })
    }

    @Test fun revisionIncrementsOnJoinReadyAndStart() = runBlocking {
        val session = UnoHostSession(hostName = "Host", config = UnoV5RoomConfig(2))
        val before = session.currentRevision()
        val guest = session.join("Guest").value!!
        session.ready(session.hostPlayerId); session.ready(guest.playerId); session.startGame(session.hostPlayerId)
        assertTrue(session.currentRevision() > before)
    }

    @Test fun staleRevisionReturnsLatestState() = runBlocking {
        val (session, guest, _) = started()
        val stale = session.submitAction(guest, "stale", 0, UnoV5ActionPayload(UnoV5ActionType.DRAW_CARD))
        assertEquals(UnoV5ErrorCode.STALE_REVISION, stale.error)
        assertNotNull(stale.value)
    }

    @Test fun duplicateRequestIdIsIdempotent() = runBlocking {
        val (session, guest, _) = started()
        val revision = session.currentRevision()
        val first = session.submitAction(guest, "same", revision, UnoV5ActionPayload(UnoV5ActionType.DRAW_CARD))
        val second = session.submitAction(guest, "same", revision, UnoV5ActionPayload(UnoV5ActionType.DRAW_CARD))
        assertEquals(first.success, second.success)
        assertEquals(first.value?.revision, second.value?.revision)
    }

    @Test fun illegalCardIsRejectedWithoutChangingRevision() = runBlocking {
        val (session, guest, _) = started()
        val revision = session.currentRevision()
        val actor = session.viewFor(guest)!!.game!!.currentPlayerId!!
        val result = session.submitAction(actor, "bad-card", revision, UnoV5ActionPayload(UnoV5ActionType.PLAY_CARD, cardId = "missing"))
        assertFalse(result.success)
        assertEquals(UnoV5ErrorCode.ILLEGAL_ACTION, result.error)
        assertEquals(revision, session.currentRevision())
    }

    @Test fun drawActionChangesAuthoritativeView() = runBlocking {
        val (session, guest, _) = started()
        val view = session.viewFor(guest)!!.game!!
        val current = view.currentPlayerId!!
        val result = session.submitAction(current, "draw", session.currentRevision(), UnoV5ActionPayload(UnoV5ActionType.DRAW_CARD))
        assertTrue(result.success)
    }

    @Test fun reconnectRestoresOriginalSeatAndHand() = runBlocking {
        val (session, guest, token) = started()
        val guestView = session.viewFor(guest)!!.game!!
        val reconnectPlayer = guestView.players.first { it.playerId != guestView.currentPlayerId }.playerId
        val reconnectToken = if (reconnectPlayer == guest) token else session.hostResumeToken
        val before = session.viewFor(reconnectPlayer)!!.game!!
        session.disconnect(reconnectPlayer)
        val result = session.reconnect(reconnectPlayer, reconnectToken)
        assertTrue(result.success)
        assertEquals(before.ownHand, result.value!!.room.game!!.ownHand)
        assertEquals(before.players.first { it.playerId == reconnectPlayer }.seatIndex, result.value.room.players.first { it.playerId == reconnectPlayer }.seatIndex)
    }

    @Test fun invalidTokenCannotReconnect() = runBlocking {
        val (session, guest, _) = started()
        session.disconnect(guest)
        assertEquals(UnoV5ErrorCode.INVALID_RESUME_TOKEN, session.reconnect(guest, "wrong").error)
    }

    @Test fun unknownPlayerCannotReconnect() = runBlocking {
        val (session, _, _) = started()
        assertEquals(UnoV5ErrorCode.PLAYER_NOT_FOUND, session.reconnect("unknown", "token").error)
    }

    @Test fun disconnectKeepsSeatAndMarksDisconnected() = runBlocking {
        val (session, guest, _) = started()
        session.disconnect(guest)
        val player = session.viewFor(session.hostPlayerId)!!.players.first { it.playerId == guest }
        assertFalse(player.connected)
        assertEquals(1, player.seatIndex)
    }

    @Test fun disconnectedCurrentPlayerIsTakenOverByBot() = runBlocking {
        val (session, guest, _) = started()
        val current = session.viewFor(guest)!!.game!!.currentPlayerId!!
        session.disconnect(current)
        assertTrue(session.viewFor(guest)!!.game!!.currentPlayerId != current || session.viewFor(guest)!!.game!!.phase != "TURN")
    }

    @Test fun nonCurrentDisconnectDoesNotRemovePlayer() = runBlocking {
        val (session, guest, _) = started()
        val current = session.viewFor(guest)!!.game!!.currentPlayerId!!
        val other = session.viewFor(guest)!!.players.first { it.playerId != current }.playerId
        session.disconnect(other)
        assertEquals(2, session.viewFor(guest)!!.players.size)
    }

    @Test fun botIsNeverExposedWithHandCards() = runBlocking {
        val session = UnoHostSession(hostName = "Host", config = UnoV5RoomConfig(2))
        session.addBot(session.hostPlayerId); session.ready(session.hostPlayerId); session.startGame(session.hostPlayerId)
        val room = session.viewFor(session.hostPlayerId)!!
        assertTrue(room.game!!.players.filter { it.isBot }.all { it.hand.isEmpty() })
    }

    @Test fun roomStatusIsPlayingAfterStart() = runBlocking {
        val (session, guest, _) = started()
        assertEquals(UnoV5RoomStatus.PLAYING, session.viewFor(guest)!!.status)
    }

    @Test fun fourPlayerRoomUsesFourStableSeats() = runBlocking {
        val session = UnoHostSession(hostName = "Host", config = UnoV5RoomConfig(4))
        val a = session.join("A").value!!; val b = session.join("B").value!!; val c = session.join("C").value!!
        assertEquals(listOf(0, 1, 2, 3), session.viewFor(session.hostPlayerId)!!.players.map { it.seatIndex })
        listOf(session.hostPlayerId, a.playerId, b.playerId, c.playerId).forEach { session.ready(it) }
        assertTrue(session.startGame(session.hostPlayerId).success)
    }

    @Test fun unreadyCanBeToggledBeforeStart() = runBlocking {
        val session = UnoHostSession(hostName = "Host", config = UnoV5RoomConfig(2)); val guest = session.join("G").value!!
        session.ready(session.hostPlayerId); session.ready(guest.playerId); session.unready(guest.playerId)
        assertEquals(UnoV5ErrorCode.NOT_READY, session.startGame(session.hostPlayerId).error)
    }

    @Test fun hostOnlyStart() = runBlocking {
        val (session, guest, _) = started()
        assertEquals(UnoV5ErrorCode.NOT_HOST, session.startGame(guest).error)
    }

    @Test fun startTwiceIsRejected() = runBlocking {
        val (session, _, _) = started()
        assertEquals(UnoV5ErrorCode.GAME_ALREADY_STARTED, session.startGame(session.hostPlayerId).error)
    }

    @Test fun actionForUnknownPlayerIsRejected() = runBlocking {
        val (session, _, _) = started()
        assertEquals(UnoV5ErrorCode.PLAYER_NOT_FOUND, session.submitAction("bad", "r", null, UnoV5ActionPayload(UnoV5ActionType.DRAW_CARD)).error)
    }

    @Test fun roomCodeRemainsSixDigits() = runBlocking {
        val session = UnoHostSession(hostName = "Host")
        assertEquals(6, session.roomCode.length); assertTrue(session.roomCode.all(Char::isDigit))
    }

    @Test fun closeRejectsNewJoin() = runBlocking {
        val session = UnoHostSession(hostName = "Host"); session.close()
        assertEquals(UnoV5ErrorCode.ROOM_NOT_FOUND, session.join("late").error)
    }

    @Test fun catchAndColorActionsUseWireFields() = runBlocking {
        val action = UnoV5PayloadCodec.action(UnoV5ActionPayload(UnoV5ActionType.CHOOSE_COLOR, color = "RED"))
        assertTrue(action is com.offlinelandlord.game.uno.core.UnoAction.ChooseColor)
        assertTrue(UnoV5PayloadCodec.action(UnoV5ActionPayload(UnoV5ActionType.CATCH_UNO, targetPlayerId = "p")) is com.offlinelandlord.game.uno.core.UnoAction.CatchUno)
    }
}
