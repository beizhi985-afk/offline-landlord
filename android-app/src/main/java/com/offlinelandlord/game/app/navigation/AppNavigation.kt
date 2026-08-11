package com.offlinelandlord.game.app.navigation

import com.offlinelandlord.game.shared.GameType

enum class AppRoute {
    GAME_SELECTION,
    LANDLORD,
    UNO_HOME,
    UNO_SETUP,
    UNO_GAME,
}

data class AppNavigationState(
    val route: AppRoute = AppRoute.GAME_SELECTION,
) {
    fun select(gameType: GameType): AppNavigationState = copy(
        route = when (gameType) {
            GameType.LANDLORD -> AppRoute.LANDLORD
            GameType.UNO -> AppRoute.UNO_HOME
        },
    )

    fun backToGameSelection(): AppNavigationState = copy(route = AppRoute.GAME_SELECTION)
    fun openUnoSetup(): AppNavigationState = copy(route = AppRoute.UNO_SETUP)
    fun startUnoGame(): AppNavigationState = copy(route = AppRoute.UNO_GAME)
    fun backToUnoHome(): AppNavigationState = copy(route = AppRoute.UNO_HOME)
}
