package com.offlinelandlord.game

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.offlinelandlord.game.ui.GameViewModel
import com.offlinelandlord.game.ui.OfflineLandlordApp
import com.offlinelandlord.game.ui.theme.OfflineLandlordTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            OfflineLandlordTheme {
                val gameViewModel: GameViewModel = viewModel()
                OfflineLandlordApp(gameViewModel)
            }
        }
    }
}

