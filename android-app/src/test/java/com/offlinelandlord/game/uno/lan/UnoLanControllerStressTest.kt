package com.offlinelandlord.game.uno.lan

import com.offlinelandlord.game.network.uno.v5.UnoV5ActionType
import com.offlinelandlord.game.network.uno.v5.UnoV5GameMode
import com.offlinelandlord.game.network.uno.v5.UnoV5RoomStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UnoLanControllerStressTest {
    @Test(timeout = 300_000)
    fun controllerQuickCompletesTwoHundredMatches() = runBlocking {
        repeat(200) { matchIndex ->
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val controller = UnoLanController(scope)
            try {
                withTimeout(20_000) {
                    controller.createRoom("房主$matchIndex", 2, UnoV5GameMode.QUICK)
                    await { controller.uiState.value.room != null }
                    controller.addBot()
                    await { controller.uiState.value.room?.players?.size == 2 }
                    controller.ready()
                    await { controller.uiState.value.room?.players?.all { it.ready } == true }
                    controller.startGame()
                    await { controller.uiState.value.room?.status == UnoV5RoomStatus.PLAYING }

                    var actions = 0
                    while (controller.uiState.value.room?.status != UnoV5RoomStatus.MATCH_FINISHED) {
                        val state = controller.uiState.value
                        val game = state.game ?: error("missing game at match=$matchIndex action=$actions")
                        val beforeRevision = state.room!!.revision
                        when {
                            UnoV5ActionType.CHOOSE_COLOR in game.legalActions -> controller.chooseColor("RED")
                            UnoV5ActionType.CATCH_UNO in game.legalActions -> controller.catchUno(game.catchTargetPlayerId)
                            UnoV5ActionType.DECLARE_UNO in game.legalActions -> controller.declareUno()
                            UnoV5ActionType.PLAY_DRAWN_CARD in game.legalActions && game.drawnCardId != null -> controller.playDrawnCard(game.drawnCardId)
                            UnoV5ActionType.PASS_AFTER_DRAW in game.legalActions -> controller.passAfterDraw()
                            UnoV5ActionType.PLAY_CARD in game.legalActions && game.legalPlayableCardIds.isNotEmpty() -> controller.playCard(game.legalPlayableCardIds.first())
                            UnoV5ActionType.DRAW_CARD in game.legalActions -> controller.drawCard()
                            else -> error("no controller action at match=$matchIndex action=$actions phase=${game.phase}")
                        }
                        await {
                            val latest = controller.uiState.value.room
                            latest?.revision?.let { it > beforeRevision } == true || latest?.status == UnoV5RoomStatus.MATCH_FINISHED
                        }
                        assertTrue("controller action limit match=$matchIndex", ++actions < 3_000)
                    }
                    assertEquals(UnoV5RoomStatus.MATCH_FINISHED, controller.uiState.value.room?.status)
                }
            } finally {
                controller.close()
                scope.cancel()
            }
        }
    }

    private suspend fun await(condition: () -> Boolean) {
        while (!condition()) delay(1)
    }
}
