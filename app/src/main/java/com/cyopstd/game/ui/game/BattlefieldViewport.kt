package com.cyopstd.game.ui.game

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset

/**
 * The player's view of the board: how far in, and where.
 *
 * Deliberately *not* part of the game state. Zoom is a property of looking at
 * the match, not of the match — it must not reach the save, must not be
 * replicated to another device, and must survive a pause without meaning
 * anything to the simulation. Keeping it here means the engine cannot
 * accidentally come to depend on it.
 *
 * Holds only the two numbers. All the arithmetic, including the clamping that
 * stops the board being dragged off-screen, lives in [WorldTransform], so there
 * is exactly one description of how a world point becomes a screen point.
 */
@Stable
class BattlefieldViewport {

    var zoom by mutableFloatStateOf(1f)
        private set

    var panX by mutableFloatStateOf(0f)
        private set

    var panY by mutableFloatStateOf(0f)
        private set

    /** True when the player has zoomed in at all. */
    val isZoomed: Boolean get() = zoom > WorldTransform.MIN_ZOOM + 0.001f

    /**
     * Pinch: change zoom by [factor] about [centroid], keeping the world point
     * under the fingers under the fingers.
     *
     * [current] is the transform the gesture started this frame with, which is
     * what makes the anchoring exact rather than approximate — the world point
     * is resolved against the *old* scale and re-pinned against the new one.
     */
    fun pinch(current: WorldTransform, centroid: Offset, factor: Float) {
        val anchorWorld = current.toWorld(centroid)
        val next = (zoom * factor).coerceIn(WorldTransform.MIN_ZOOM, WorldTransform.MAX_ZOOM)

        // Snap out to exactly fitted rather than resting just above it, so
        // "zoom all the way out" reliably means a centred, unpanned board.
        if (next <= WorldTransform.SNAP_TO_FIT_BELOW) {
            reset()
            return
        }

        zoom = next
        val rebuilt = WorldTransform(current.viewWidth, current.viewHeight, next, panX, panY)
        val wanted = rebuilt.panKeeping(anchorWorld, centroid)
        panX = wanted.x
        panY = wanted.y
    }

    /** Drag the board. Clamping happens in the transform, so this can be naive. */
    fun pan(dx: Float, dy: Float) {
        panX += dx
        panY += dy
    }

    /**
     * Re-clamp against the transform actually in force.
     *
     * [pan] lets the stored values run past the edge; the transform clamps on
     * read. Writing the clamped values back keeps the two in step, so a pan
     * that hit the edge does not accumulate slack that has to be "unwound"
     * before the board moves the other way. Without it, dragging hard against
     * the left edge and then flicking right does nothing for a moment.
     */
    fun settle(transform: WorldTransform) {
        panX = transform.panX
        panY = transform.panY
    }

    /** Back to the fitted, centred board. */
    fun reset() {
        zoom = 1f
        panX = 0f
        panY = 0f
    }
}
