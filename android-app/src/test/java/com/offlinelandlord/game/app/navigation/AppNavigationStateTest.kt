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
    fun selectingUnoOpensPlaceholder() {
        assertEquals(AppRoute.UNO_PLACEHOLDER, AppNavigationState().select(GameType.UNO).route)
    }

    @Test
    fun placeholderCanReturnToGameSelection() {
        val placeholder = AppNavigationState().select(GameType.UNO)
        assertEquals(AppRoute.GAME_SELECTION, placeholder.backToGameSelection().route)
    }

    @Test
    fun gameTypeContainsOnlyLandlordAndUno() {
        assertArrayEquals(arrayOf(GameType.LANDLORD, GameType.UNO), GameType.entries.toTypedArray())
    }
}
