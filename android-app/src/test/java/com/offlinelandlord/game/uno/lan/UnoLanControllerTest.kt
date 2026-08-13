package com.offlinelandlord.game.uno.lan

import com.offlinelandlord.game.network.ConnectionState
import com.offlinelandlord.game.network.uno.v5.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

/** State and controller contract tests for the Compose LAN boundary. */
class UnoLanControllerTest {
    private fun room(status: UnoV5RoomStatus = UnoV5RoomStatus.WAITING, revision: Long = 7L): UnoV5RoomView {
        val players = listOf(
            UnoV5PlayerView("host", 0, "Host", true, true, false, handCount = 7, score = 10),
            UnoV5PlayerView("guest", 1, "Guest", true, false, false, handCount = 8, score = 20),
            UnoV5PlayerView("bot", 2, "Bot", true, true, true, handCount = 6, score = 4),
        )
        return UnoV5RoomView("123456", "host", players, UnoV5GameMode.QUICK, 3, status, revision)
    }

    private fun game(self: String = "guest", current: String? = "guest"): UnoV5GameView = UnoV5GameView(
        selfPlayerId = self,
        ownHand = listOf(UnoV5Card("r1", "RED", "NUMBER", 1)),
        players = room(UnoV5RoomStatus.PLAYING).players,
        topDiscard = UnoV5Card("b2", "BLUE", "NUMBER", 2),
        drawPileCount = 20,
        activeColor = "BLUE",
        currentPlayerId = current,
        direction = "CLOCKWISE",
        phase = "PLAYING",
        roundNumber = 1,
        scores = mapOf("host" to 10, "guest" to 20),
        unoDeclaredPlayerId = null,
        catchTargetPlayerId = null,
        drawnCardId = null,
        colorChooserPlayerId = null,
        roundWinnerId = null,
        matchWinnerId = null,
        legalActions = setOf(UnoV5ActionType.PLAY_CARD, UnoV5ActionType.DRAW_CARD),
        legalPlayableCardIds = listOf("r1"),
    )

    @Test fun initialStateIsDisconnected() { val c = UnoLanController(CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)); assertEquals(ConnectionState.DISCONNECTED, c.uiState.value.connectionState); c.close() }
    @Test fun initialStateHasNoRoom() { assertNull(UnoLanUiState().room) }
    @Test fun initialStateHasNoPrivateHand() { assertTrue(UnoLanUiState().selfHand.isEmpty()) }
    @Test fun initialStateHasNoOpponents() { assertTrue(UnoLanUiState().opponentViews.isEmpty()) }
    @Test fun initialStateIsNotPlaying() { assertFalse(UnoLanUiState().isPlaying) }
    @Test fun initialStateIsNotFinished() { assertFalse(UnoLanUiState().isFinished) }
    @Test fun defaultRoomNameIsStable() { assertEquals("UNO 房间", UnoLanUiState().roomName) }
    @Test fun defaultRoomListIsEmpty() { assertTrue(UnoLanUiState().rooms.isEmpty()) }
    @Test fun defaultHostFlagIsFalse() { assertFalse(UnoLanUiState().isHost) }
    @Test fun defaultReconnectFlagIsFalse() { assertFalse(UnoLanUiState().isReconnecting) }

    @Test fun createRejectsTooFewPlayers() { val c = UnoLanController(CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)); c.createRoom("Host", 1, UnoV5GameMode.QUICK); assertNotNull(c.uiState.value.errorMessage); c.close() }
    @Test fun createRejectsTooManyPlayers() { val c = UnoLanController(CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)); c.createRoom("Host", 5, UnoV5GameMode.QUICK); assertNotNull(c.uiState.value.errorMessage); c.close() }
    @Test fun joinRejectsBlankHost() { assertJoinError("", 39173, "123456") }
    @Test fun joinRejectsHostWithPort() { assertJoinError("192.168.1.2:39173", 39173, "123456") }
    @Test fun joinRejectsZeroPort() { assertJoinError("192.168.1.2", 0, "123456") }
    @Test fun joinRejectsOversizedPort() { assertJoinError("192.168.1.2", 65536, "123456") }
    @Test fun joinRejectsShortRoomCode() { assertJoinError("192.168.1.2", 39173, "12345") }
    @Test fun joinRejectsNonNumericRoomCode() { assertJoinError("192.168.1.2", 39173, "12A456") }

    private fun assertJoinError(host: String, port: Int, code: String) { val c = UnoLanController(CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)); c.joinRoom(host, port, code, "Guest"); assertNotNull(c.uiState.value.errorMessage); c.close() }

    @Test fun lobbyProjectionCarriesRoomCode() { assertEquals("123456", UnoLanUiState(room = room()).room?.roomCode) }
    @Test fun lobbyProjectionCarriesRevision() { assertEquals(7L, UnoLanUiState(room = room()).room?.revision) }
    @Test fun lobbyIdentityIsAvailableBeforeGame() { assertEquals("guest", UnoLanUiState(room = room(), selfPlayerId = "guest").selfId) }
    @Test fun gameIdentityTakesPrecedenceOverLobbyIdentity() { assertEquals("guest", UnoLanUiState(room = room().copy(game = game("guest")), selfPlayerId = "host").selfId) }
    @Test fun opponentProjectionExcludesSelf() { val state = UnoLanUiState(room = room().copy(game = game("guest"))); assertTrue(state.opponentViews.none { it.playerId == "guest" }) }
    @Test fun opponentProjectionKeepsHandPrivate() { val state = UnoLanUiState(room = room().copy(game = game("guest"))); assertTrue(state.opponentViews.all { it.hand.isEmpty() }) }
    @Test fun opponentProjectionKeepsPublicCounts() { val state = UnoLanUiState(room = room().copy(game = game("guest"))); assertEquals(7, state.opponentViews.first { it.playerId == "host" }.handCount) }
    @Test fun currentPlayerNameUsesPublicPlayerView() { val state = UnoLanUiState(room = room().copy(game = game("guest", "host"))); assertEquals("Host", state.currentPlayerName) }
    @Test fun activeGameIsPlaying() { assertTrue(UnoLanUiState(room = room(UnoV5RoomStatus.PLAYING).copy(game = game())).isPlaying) }
    @Test fun finishedMatchIsFinished() { assertTrue(UnoLanUiState(room = room(UnoV5RoomStatus.MATCH_FINISHED).copy(game = game())).isFinished) }
    @Test fun finishedRoundIsAuthoritativeResult() { assertTrue(UnoLanUiState(room = room(UnoV5RoomStatus.ROUND_FINISHED).copy(game = game())).isFinished) }
    @Test fun lanClientDoesNotDeclareWinnerFromEmptyLocalHand() {
        val emptyHandGame = game().copy(ownHand = emptyList())
        val state = UnoLanUiState(room = room(UnoV5RoomStatus.PLAYING).copy(game = emptyHandGame))
        assertFalse(state.isFinished)
        assertNull(state.game!!.roundWinnerId)
        assertNull(state.game!!.matchWinnerId)
    }
    @Test fun lanClientIgnoresOlderOrDuplicateRevision() {
        assertTrue(shouldApplyUnoLanRevision(currentRevision = null, incomingRevision = 5))
        assertTrue(shouldApplyUnoLanRevision(currentRevision = 4, incomingRevision = 5))
        assertFalse(shouldApplyUnoLanRevision(currentRevision = 5, incomingRevision = 5))
        assertFalse(shouldApplyUnoLanRevision(currentRevision = 6, incomingRevision = 5))
    }
    @Test fun ownHandIsVisibleOnlyForSelf() { val state = UnoLanUiState(room = room().copy(game = game())); assertEquals(listOf("r1"), state.selfHand.map { it.cardId }) }
    @Test fun legalActionsAreForwarded() { val state = UnoLanUiState(room = room().copy(game = game())); assertTrue(UnoV5ActionType.DRAW_CARD in state.game!!.legalActions) }
    @Test fun legalCardsAreForwarded() { val state = UnoLanUiState(room = room().copy(game = game())); assertEquals(listOf("r1"), state.game!!.legalPlayableCardIds) }
    @Test fun botIsMarkedInLobby() { assertTrue(room().players.first { it.playerId == "bot" }.isBot) }
    @Test fun playerSeatOrderIsStable() { assertEquals(listOf(0, 1, 2), room().players.map { it.seatIndex }) }
    @Test fun scoreProjectionIsPublic() { assertEquals(20, room().players.first { it.playerId == "guest" }.score) }
    @Test fun pointsModeIsPreserved() { assertEquals(UnoV5GameMode.POINTS_500, UnoLanUiState(room = room().copy(gameMode = UnoV5GameMode.POINTS_500)).room!!.gameMode) }
    @Test fun roomStatusIsPreserved() { assertEquals(UnoV5RoomStatus.WAITING, UnoLanUiState(room = room()).room!!.status) }
    @Test fun emptyCurrentPlayerHasNoName() { assertNull(UnoLanUiState(room = room().copy(game = game(current = null))).currentPlayerName) }
    @Test fun dismissErrorClearsError() { val c = UnoLanController(CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)); c.createRoom("Host", 1, UnoV5GameMode.QUICK); c.dismissError(); assertNull(c.uiState.value.errorMessage); c.close() }
    @Test fun leaveRoomResetsState() { val c = UnoLanController(CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)); c.leaveRoom(); assertNull(c.uiState.value.room); assertEquals(ConnectionState.DISCONNECTED, c.uiState.value.connectionState); c.close() }
    @Test fun allActionsAreSafeBeforeConnection() { val c = UnoLanController(CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)); c.ready(); c.unready(); c.addBot(); c.removeBot("bot"); c.startGame(); c.playCard("r1"); c.drawCard(); c.playDrawnCard("r1"); c.passAfterDraw(); c.chooseColor("RED"); c.declareUno(); c.catchUno("bot"); c.startNextRound(); assertNotNull(c.uiState.value.errorMessage); c.close() }

    @Test fun loopbackHostAndRemoteReachPlayingState() = runBlocking {
        // Keep controller calls on a non-IO dispatcher, matching Compose click callbacks.
        // Network writes must remain safe because the controller moves them to Dispatchers.IO.
        val hostScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val guestScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val host = UnoLanController(hostScope)
        val guest = UnoLanController(guestScope)
        try {
            host.createRoom("Host", 2, UnoV5GameMode.QUICK)
            withTimeout(15_000) { while (host.uiState.value.room == null) delay(50) }
            val hostRoom = host.uiState.value.room!!
            guest.joinRoom("127.0.0.1", host.uiState.value.hostPort, hostRoom.roomCode, "Guest")
            withTimeout(15_000) { while (guest.uiState.value.room?.players?.size != 2) delay(50) }
            host.ready(); guest.ready()
            withTimeout(15_000) { while (host.uiState.value.room?.players?.count { it.ready } != 2) delay(50) }
            host.startGame()
            withTimeout(15_000) { while (host.uiState.value.room?.status != UnoV5RoomStatus.PLAYING || guest.uiState.value.room?.status != UnoV5RoomStatus.PLAYING) delay(50) }
            assertEquals(UnoV5RoomStatus.PLAYING, guest.uiState.value.room?.status)
        } finally { host.close(); guest.close(); hostScope.cancel(); guestScope.cancel() }
    }
}
