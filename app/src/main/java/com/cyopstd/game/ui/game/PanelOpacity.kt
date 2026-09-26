package com.cyopstd.game.ui.game

import androidx.compose.runtime.staticCompositionLocalOf
import com.cyopstd.game.save.DEFAULT_PANEL_OPACITY

/**
 * Background opacity for the in-game pop-up panels, from SETTINGS.
 *
 * One value for every panel over the board, so they all show the same amount
 * of it. Only a panel's background uses this; its text stays fully opaque.
 */
val LocalPanelOpacity = staticCompositionLocalOf { DEFAULT_PANEL_OPACITY }
