package com.offlinelandlord.game.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.offlinelandlord.game.app.gameselection.GameSelectionScreen
import com.offlinelandlord.game.app.navigation.AppNavigationState
import com.offlinelandlord.game.app.navigation.AppRoute
import com.offlinelandlord.game.ui.GameViewModel
import com.offlinelandlord.game.ui.OfflineLandlordApp
import com.offlinelandlord.game.uno.core.UnoMatchMode
import com.offlinelandlord.game.uno.singleplayer.UnoGameViewModel
import com.offlinelandlord.game.uno.singleplayer.UnoSinglePlayerConfig
import com.offlinelandlord.game.uno.singleplayer.ui.UnoGameScreen
import com.offlinelandlord.game.uno.singleplayer.ui.UnoHomeScreen
import com.offlinelandlord.game.uno.singleplayer.ui.UnoSinglePlayerSetupScreen

@Composable
fun OfflineGamesApp() {
    var routeName by rememberSaveable { mutableStateOf(AppNavigationState().route.name) }
    val navigation = AppNavigationState(route = AppRoute.valueOf(routeName))
    val updateNavigation: (AppNavigationState) -> Unit = { routeName = it.route.name }
    var unoPlayerCount by rememberSaveable { mutableStateOf(2) }
    var unoMatchModeName by rememberSaveable { mutableStateOf(UnoMatchMode.QUICK.name) }

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

        AppRoute.UNO_HOME -> UnoHomeScreen(
            onSinglePlayer = { updateNavigation(navigation.openUnoSetup()) },
            onBackToGameSelection = { updateNavigation(navigation.backToGameSelection()) },
        )

        AppRoute.UNO_SETUP -> UnoSinglePlayerSetupScreen(
            initialPlayerCount = unoPlayerCount,
            initialMatchMode = UnoMatchMode.valueOf(unoMatchModeName),
            onStartGame = { config ->
                unoPlayerCount = config.playerCount
                unoMatchModeName = config.matchMode.name
                updateNavigation(navigation.startUnoGame())
            },
            onBack = { updateNavigation(navigation.backToUnoHome()) },
        )

        AppRoute.UNO_GAME -> {
            val unoViewModel: UnoGameViewModel = viewModel()
            val config = UnoSinglePlayerConfig(
                playerCount = unoPlayerCount,
                matchMode = UnoMatchMode.valueOf(unoMatchModeName),
            )
            LaunchedEffect(config) { unoViewModel.ensureGame(config) }
            UnoGameScreen(
                viewModel = unoViewModel,
                onReturnToUnoHome = {
                    unoViewModel.leaveGame()
                    updateNavigation(navigation.backToUnoHome())
                },
            )
        }
    }
}
