package com.cyopstd.game

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import com.cyopstd.game.state.GameViewModel
import com.cyopstd.game.ui.CyOpsApp
import com.cyopstd.game.ui.theme.CyOpsTheme
import com.cyopstd.game.ui.theme.Palette

/**
 * The single activity.
 *
 * Landscape is locked in the manifest and configuration changes are handled here
 * rather than by recreating the activity, so a rotation, a font-scale change or a
 * theme switch never interrupts a match. The ViewModel survives regardless.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: GameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // A tower defence match is watched, not just tapped: keep the display on
        // while the app is foregrounded.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        hideSystemBars()

        // Billing needs an Activity to launch a purchase flow and AdMob needs
        // one to show an interstitial. Both hold it weakly, so handing it over
        // here does not outlive this Activity.
        viewModel.attachActivity(this)

        setContent {
            CyOpsTheme {
                CyOpsApp(
                    viewModel = viewModel,
                    onExitApp = { finishAndRemoveTask() },
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Palette.Background)
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                )
            }
        }
    }

    /** Immersive mode: the battlefield gets the whole screen. */
    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onResume() {
        super.onResume()
        viewModel.onAppResumed()
    }

    override fun onPause() {
        super.onPause()
        // Save here rather than in onStop: onStop is not guaranteed to run if
        // the process is killed aggressively, and a lost run is unforgivable.
        viewModel.onAppPaused()
    }
}
