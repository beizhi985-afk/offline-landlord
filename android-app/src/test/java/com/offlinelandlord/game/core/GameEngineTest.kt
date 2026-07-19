package com.offlinelandlord.game.core

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameEngineTest {
    @Test
    fun threeReadyPlayersEnterBiddingAndLandlordGetsBottomCards() {
        val engine = GameEngine("123456", "测试房间", "房主", Random(7))
        val second = engine.join("玩家二")
        val third = engine.join("玩家三")
        assertTrue(second.success)
        assertTrue(third.success)

        val ids = listOf(engine.hostPlayerId, second.playerId!!, third.playerId!!)
        ids.forEach { id -> assertTrue(engine.applyAction(id, PlayerAction.ready(true)).success) }

        var hostView = requireNotNull(engine.viewFor(engine.hostPlayerId))
        assertEquals(GamePhase.BIDDING, hostView.phase)
        assertEquals(17, hostView.ownHand.size)
        assertTrue(hostView.bottomCards.isEmpty())

        var firstBid = true
        while (hostView.phase == GamePhase.BIDDING) {
            val bidder = requireNotNull(hostView.currentTurnId)
            val bid = if (firstBid) 1 else 0
            assertTrue(engine.applyAction(bidder, PlayerAction.bid(bid)).success)
            firstBid = false
            hostView = requireNotNull(engine.viewFor(engine.hostPlayerId))
        }

        assertEquals(GamePhase.DOUBLING, hostView.phase)
        while (hostView.phase == GamePhase.DOUBLING) {
            val player = requireNotNull(hostView.currentTurnId)
            assertTrue(engine.applyAction(player, PlayerAction.double(false)).success)
            hostView = requireNotNull(engine.viewFor(engine.hostPlayerId))
        }

        assertEquals(GamePhase.PLAYING, hostView.phase)
        assertNotNull(hostView.landlordId)
        assertEquals(3, hostView.bottomCards.size)
        val handSizes = ids.map { id -> requireNotNull(engine.viewFor(id)).ownHand.size }.sorted()
        assertEquals(listOf(17, 17, 20), handSizes)
        assertEquals(54, handSizes.sum())
    }

    @Test
    fun rejectsFourthPlayerAndStaleRevision() {
        val engine = GameEngine("123456", "测试房间", "房主", Random(2))
        assertTrue(engine.join("玩家二").success)
        assertTrue(engine.join("玩家三").success)
        assertFalse(engine.join("玩家四").success)

        val revision = requireNotNull(engine.viewFor(engine.hostPlayerId)).revision
        assertTrue(engine.applyAction(engine.hostPlayerId, PlayerAction.ready(true), revision).success)
        assertFalse(engine.applyAction(engine.hostPlayerId, PlayerAction.ready(false), revision).success)
    }

    @Test
    fun hidesOtherPlayersHandsFromEveryView() {
        val engine = GameEngine("123456", "测试房间", "房主", Random(3))
        val second = engine.join("玩家二").playerId!!
        val third = engine.join("玩家三").playerId!!
        listOf(engine.hostPlayerId, second, third).forEach { engine.applyAction(it, PlayerAction.ready(true)) }

        val view = requireNotNull(engine.viewFor(second))
        assertEquals(17, view.ownHand.size)
        assertTrue(view.players.all { it.remainingCards == 17 })
    }

    @Test
    fun onlyHostCanManageBotsAndBotsAreReadyImmediately() {
        val engine = GameEngine("123456", "测试房间", "房主", Random(9))
        val second = engine.join("玩家二").playerId!!
        assertFalse(engine.applyAction(second, PlayerAction.addBot()).success)
        assertTrue(engine.applyAction(engine.hostPlayerId, PlayerAction.addBot()).success)

        val waiting = requireNotNull(engine.viewFor(engine.hostPlayerId))
        val bot = waiting.players.single { it.isBot }
        assertTrue(bot.ready)
        assertTrue(bot.isAutoPlaying)
        assertTrue(engine.applyAction(engine.hostPlayerId, PlayerAction.removeBot(bot.id)).success)
        assertEquals(2, requireNotNull(engine.viewFor(engine.hostPlayerId)).players.size)
    }

    @Test
    fun twentyFourRoundModeAndPlayerChoicesIncreaseMultiplier() {
        val engine = GameEngine(
            roomCode = "123456",
            roomName = "二十四局测试",
            hostName = "房主",
            random = Random(11),
            totalRounds = 24,
            doublingEnabled = true,
        )
        val second = engine.join("玩家二").playerId!!
        val third = engine.join("玩家三").playerId!!
        listOf(engine.hostPlayerId, second, third).forEach { engine.applyAction(it, PlayerAction.ready(true)) }

        val bidder = requireNotNull(engine.viewFor(engine.hostPlayerId)).currentTurnId!!
        assertTrue(engine.applyAction(bidder, PlayerAction.bid(3)).success)
        var view = requireNotNull(engine.viewFor(engine.hostPlayerId))
        assertEquals(GamePhase.DOUBLING, view.phase)
        assertEquals(24, view.totalRounds)
        assertEquals(1, view.currentRound)

        listOf(true, false, true).forEach { choice ->
            val current = requireNotNull(view.currentTurnId)
            assertTrue(engine.applyAction(current, PlayerAction.double(choice)).success)
            view = requireNotNull(engine.viewFor(engine.hostPlayerId))
        }

        assertEquals(GamePhase.PLAYING, view.phase)
        assertEquals(12, view.multiplier)
        assertEquals(2, view.players.count { it.doubleChoice == true })
    }

    @Test
    fun disabledDoublingSkipsChoiceStage() {
        val engine = GameEngine(
            roomCode = "123456",
            roomName = "不加倍测试",
            hostName = "房主",
            random = Random(12),
            totalRounds = 12,
            doublingEnabled = false,
        )
        val second = engine.join("玩家二").playerId!!
        val third = engine.join("玩家三").playerId!!
        listOf(engine.hostPlayerId, second, third).forEach { engine.applyAction(it, PlayerAction.ready(true)) }

        val bidder = requireNotNull(engine.viewFor(engine.hostPlayerId)).currentTurnId!!
        assertTrue(engine.applyAction(bidder, PlayerAction.bid(3)).success)
        val view = requireNotNull(engine.viewFor(engine.hostPlayerId))

        assertEquals(GamePhase.PLAYING, view.phase)
        assertFalse(view.doublingEnabled)
        assertTrue(view.players.all { it.doubleChoice == null })
    }

    @Test
    fun everyRedealRandomlyChoosesFirstBidder() {
        val engine = GameEngine("123456", "随机首叫测试", "房主", Random(41))
        val second = engine.join("玩家二").playerId!!
        val third = engine.join("玩家三").playerId!!
        listOf(engine.hostPlayerId, second, third).forEach { engine.applyAction(it, PlayerAction.ready(true)) }

        val starters = mutableSetOf<String>()
        repeat(10) {
            var view = requireNotNull(engine.viewFor(engine.hostPlayerId))
            assertEquals(GamePhase.BIDDING, view.phase)
            starters += requireNotNull(view.currentTurnId)
            repeat(3) {
                val bidder = requireNotNull(view.currentTurnId)
                assertTrue(engine.applyAction(bidder, PlayerAction.bid(0)).success)
                view = requireNotNull(engine.viewFor(engine.hostPlayerId))
            }
        }

        assertTrue("十次重新发牌应出现不同的首叫玩家", starters.size >= 2)
    }
}
