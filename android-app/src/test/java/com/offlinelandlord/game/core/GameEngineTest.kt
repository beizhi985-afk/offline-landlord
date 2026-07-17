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
}

