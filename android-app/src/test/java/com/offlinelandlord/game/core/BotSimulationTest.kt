package com.offlinelandlord.game.core

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BotSimulationTest {
    @Test
    fun twoBotsCanFillRoomAndCompleteManyGamesWithoutIllegalActions() {
        val gameCount = System.getenv("BOT_SIMULATION_GAMES")?.toIntOrNull()?.coerceIn(1, 5_000) ?: 30
        repeat(gameCount) { seed ->
            val engine = GameEngine("123456", "机器人测试", "房主", Random(seed))
            assertTrue(engine.applyAction(engine.hostPlayerId, PlayerAction.addBot()).success)
            assertTrue(engine.applyAction(engine.hostPlayerId, PlayerAction.addBot()).success)
            assertTrue(engine.applyAction(engine.hostPlayerId, PlayerAction.autoPlay(true)).success)
            assertTrue(engine.applyAction(engine.hostPlayerId, PlayerAction.ready(true)).success)

            var actionCount = 0
            while (engine.viewFor(engine.hostPlayerId)?.phase != GamePhase.FINISHED && actionCount < 600) {
                val automatedId = engine.automatedPlayerId()
                assertNotNull("seed=$seed action=$actionCount", automatedId)
                val view = requireNotNull(engine.viewFor(requireNotNull(automatedId)))
                val action = BotBrain.chooseAction(view)
                assertNotNull("seed=$seed action=$actionCount phase=${view.phase}", action)
                val result = engine.applyAction(view.selfPlayerId, requireNotNull(action))
                assertTrue("seed=$seed action=$actionCount ${result.message}", result.success)
                actionCount++
            }

            assertEquals(GamePhase.FINISHED, engine.viewFor(engine.hostPlayerId)?.phase)
            assertTrue(actionCount < 600)
        }
    }

    @Test
    fun reconnectingHumanTakesSeatBackFromAutoplay() {
        val engine = GameEngine("123456", "托管测试", "房主", Random(4))
        val joined = engine.join("玩家二")
        engine.disconnect(requireNotNull(joined.playerId))

        assertTrue(engine.enableAutoPlay(requireNotNull(joined.playerId)).success)
        assertTrue(requireNotNull(engine.viewFor(requireNotNull(joined.playerId))).players.single { it.id == joined.playerId }.isAutoPlaying)

        val resumed = engine.join("玩家二回来", joined.resumeToken)
        assertTrue(resumed.success)
        val summary = requireNotNull(engine.viewFor(requireNotNull(joined.playerId))).players.single { it.id == joined.playerId }
        assertTrue(summary.connected)
        assertFalse(summary.isAutoPlaying)
    }
}
