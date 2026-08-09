package com.offlinelandlord.game.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.offlinelandlord.game.app.gameselection.GameSelectionScreen
import com.offlinelandlord.game.app.gameselection.UnoComingSoonScreen
import com.offlinelandlord.game.app.navigation.AppNavigationState
import com.offlinelandlord.game.app.navigation.AppRoute
import com.offlinelandlord.game.ui.GameViewModel
import com.offlinelandlord.game.ui.OfflineLandlordApp

@Composable
fun OfflineGamesApp() {
    var routeName by rememberSaveable { mutableStateOf(AppNavigationState().route.name) }
    val navigation = AppNavigationState(route = AppRoute.valueOf(routeName))
    val updateNavigation: (AppNavigationState) -> Unit = { routeName = it.route.name }

    when (navigation.route) {
        AppRoute.GAME_SELECTION -> GameSelectionScreen(
            onGameSelected = { gameType -> updateNavigation(navigation.select(gameType)) },
        )

        AppRoute.LANDLORD -> {
            // The landlord ViewModel is created only after the user enters this route.
            // Its existing room and network lifecycle remains unchanged.
            val landlordViewModel: GameViewModel = viewModel()
            OfflineLandlordApp(
                viewModel = landlordViewModel,
                onBackToGameSelection = { updateNavigation(navigation.backToGameSelection()) },
            )
        }

        AppRoute.UNO_PLACEHOLDER -> UnoComingSoonScreen(
            onBackToGameSelection = { updateNavigation(navigation.backToGameSelection()) },
        )
    }
}
