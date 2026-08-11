package com.offlinelandlord.game.app.navigation

import com.offlinelandlord.game.shared.GameType
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class AppNavigationStateTest {
    @Test
    fun defaultRouteIsGameSelection() {
        assertEquals(AppRoute.GAME_SELECTION, AppNavigationState().route)
    }

    @Test
    fun selectingLandlordOpensExistingLandlordFlow() {
        assertEquals(AppRoute.LANDLORD, AppNavigationState().select(GameType.LANDLORD).route)
    }

    @Test
    fun selectingUnoOpensUnoHome() {
        assertEquals(AppRoute.UNO_HOME, AppNavigationState().select(GameType.UNO).route)
    }

    @Test
    fun unoHomeCanReturnToGameSelection() {
        val unoHome = AppNavigationState().select(GameType.UNO)
        assertEquals(AppRoute.GAME_SELECTION, unoHome.backToGameSelection().route)
    }

    @Test
    fun unoHomeOpensSinglePlayerSetup() {
        assertEquals(AppRoute.UNO_SETUP, AppNavigationState(AppRoute.UNO_HOME).openUnoSetup().route)
    }

    @Test
    fun setupStartsUnoGame() {
        assertEquals(AppRoute.UNO_GAME, AppNavigationState(AppRoute.UNO_SETUP).startUnoGame().route)
    }

    @Test
    fun tableExitReturnsToUnoHome() {
        assertEquals(AppRoute.UNO_HOME, AppNavigationState(AppRoute.UNO_GAME).backToUnoHome().route)
    }

    @Test
    fun gameTypeContainsOnlyLandlordAndUno() {
        assertArrayEquals(arrayOf(GameType.LANDLORD, GameType.UNO), GameType.entries.toTypedArray())
    }
}
