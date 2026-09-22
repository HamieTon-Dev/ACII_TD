package com.cyopstd.game.ui.game

import androidx.compose.ui.geometry.Offset
import com.cyopstd.game.core.WorldGeometry
import kotlin.math.min

/**
 * Maps the fixed 1600x760 world onto whatever the device actually gives us,
 * letterboxing to preserve aspect ratio. Every layout decision in the game is
 * expressed in world units and converted here exactly once, so nothing is tied
 * to a particular screen size.
 */
class WorldTransform(val viewWidth: Float, val viewHeight: Float) {

    val scale: Float = if (viewWidth <= 0f || viewHeight <= 0f) {
        1f
    } else {
        min(viewWidth / WorldGeometry.WIDTH, viewHeight / WorldGeometry.HEIGHT)
    }

    val offsetX: Float = (viewWidth - WorldGeometry.WIDTH * scale) * 0.5f
    val offsetY: Float = (viewHeight - WorldGeometry.HEIGHT * scale) * 0.5f

    fun toScreenX(worldX: Float): Float = offsetX + worldX * scale
    fun toScreenY(worldY: Float): Float = offsetY + worldY * scale

    fun toWorld(screen: Offset): Offset = Offset(
        x = if (scale == 0f) 0f else (screen.x - offsetX) / scale,
        y = if (scale == 0f) 0f else (screen.y - offsetY) / scale
    )
}
