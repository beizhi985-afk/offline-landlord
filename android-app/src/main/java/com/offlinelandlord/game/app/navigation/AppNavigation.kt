package com.offlinelandlord.game.app.navigation

import com.offlinelandlord.game.shared.GameType

enum class AppRoute {
    GAME_SELECTION,
    LANDLORD,
    UNO_PLACEHOLDER,
}

data class AppNavigationState(
    val route: AppRoute = AppRoute.GAME_SELECTION,
) {
    fun select(gameType: GameType): AppNavigationState = copy(
        route = when (gameType) {
            GameType.LANDLORD -> AppRoute.LANDLORD
            GameType.UNO -> AppRoute.UNO_PLACEHOLDER
        },
    )

    fun backToGameSelection(): AppNavigationState = copy(route = AppRoute.GAME_SELECTION)
}
