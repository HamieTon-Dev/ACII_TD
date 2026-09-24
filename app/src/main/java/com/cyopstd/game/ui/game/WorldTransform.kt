package com.cyopstd.game.ui.game

import androidx.compose.ui.geometry.Offset
import com.cyopstd.game.core.WorldGeometry
import kotlin.math.max
import kotlin.math.min

/**
 * Maps the fixed 1600x760 world onto whatever the device actually gives us,
 * letterboxing to preserve aspect ratio, and applying the player's zoom and pan.
 *
 * Every layout decision in the game is expressed in world units and converted
 * here **exactly once**, so nothing is tied to a particular screen size. That
 * single conversion point is also why zoom lives here rather than in a Compose
 * `graphicsLayer`: a graphics-layer transform scales the *pixels* the renderer
 * has already drawn and leaves touch handling to be corrected separately, which
 * is the classic way a zoomable board ends up placing agents a finger's width
 * from where they were tapped. Putting the zoom in the transform means drawing
 * and hit-testing cannot disagree, because they are the same arithmetic.
 *
 * @param zoom multiplier on the letterbox fit. 1 is "the whole board, fitted";
 *   values above that magnify around the board's centre plus [panX]/[panY].
 * @param panX horizontal pan in **screen pixels**, clamped by [clampPan].
 * @param panY vertical pan in screen pixels.
 */
class WorldTransform(
    val viewWidth: Float,
    val viewHeight: Float,
    zoom: Float = 1f,
    panX: Float = 0f,
    panY: Float = 0f
) {

    /**
     * The zoom actually in force, clamped at both ends.
     *
     * Clamped *here* rather than only where the gesture is handled. The first
     * version left the upper bound to `BattlefieldViewport.pinch` and this
     * class coerced only the lower one, which meant every caller that built a
     * transform directly — the tutorial arrow, the render tests, anything
     * added later — could produce a scale thirty times the intended maximum
     * and nothing would say so. A bound enforced in one of two places is not a
     * bound.
     */
    val zoom: Float = zoom.coerceIn(MIN_ZOOM, MAX_ZOOM)

    /** The letterbox fit: the scale at which the whole board is visible. */
    val fitScale: Float = if (viewWidth <= 0f || viewHeight <= 0f) {
        1f
    } else {
        min(viewWidth / WorldGeometry.WIDTH, viewHeight / WorldGeometry.HEIGHT)
    }

    val scale: Float = fitScale * this.zoom

    /**
     * How far the board may be pushed from centre before an edge comes inside
     * the viewport, per axis. Zero on an axis the board does not overflow.
     */
    private val slackX: Float = max(0f, (WorldGeometry.WIDTH * scale - viewWidth) / 2f)
    private val slackY: Float = max(0f, (WorldGeometry.HEIGHT * scale - viewHeight) / 2f)

    /**
     * The pan actually in force, after clamping.
     *
     * Clamped rather than merely limited: at zoom 1 the board is letterboxed
     * with bars on one axis and there is nothing to pan *to*, so the slack is
     * zero and the pan collapses to centre. That is what stops the board being
     * dragged off-screen and lost — the requirement is satisfied by the
     * geometry rather than by a separate rule that could be forgotten.
     */
    val panX: Float = panX.coerceIn(-slackX, slackX)
    val panY: Float = panY.coerceIn(-slackY, slackY)

    val offsetX: Float = (viewWidth - WorldGeometry.WIDTH * scale) * 0.5f + this.panX
    val offsetY: Float = (viewHeight - WorldGeometry.HEIGHT * scale) * 0.5f + this.panY

    fun toScreenX(worldX: Float): Float = offsetX + worldX * scale
    fun toScreenY(worldY: Float): Float = offsetY + worldY * scale

    fun toWorld(screen: Offset): Offset = Offset(
        x = if (scale == 0f) 0f else (screen.x - offsetX) / scale,
        y = if (scale == 0f) 0f else (screen.y - offsetY) / scale
    )

    /** A screen distance in world units. Useful for "how big is a fingertip". */
    fun toWorldLength(screenLength: Float): Float =
        if (scale == 0f) 0f else screenLength / scale

    /**
     * The pan that keeps [worldPoint] under [screenPoint] at this zoom.
     *
     * This is what makes a pinch feel attached to the fingers rather than to
     * the middle of the screen: the point between the two fingers when the
     * gesture started should still be between them as it grows.
     */
    fun panKeeping(worldPoint: Offset, screenPoint: Offset): Offset {
        val centredX = (viewWidth - WorldGeometry.WIDTH * scale) * 0.5f
        val centredY = (viewHeight - WorldGeometry.HEIGHT * scale) * 0.5f
        return Offset(
            x = screenPoint.x - worldPoint.x * scale - centredX,
            y = screenPoint.y - worldPoint.y * scale - centredY
        )
    }

    companion object {
        /**
         * Fully zoomed out is the whole board, and no further.
         *
         * Below 1 the board would shrink inside its own letterbox for no
         * benefit, and "the entire normal battlefield is still understandable
         * at minimum zoom" is satisfied exactly by making minimum zoom *be*
         * the fitted board.
         */
        const val MIN_ZOOM = 1f

        /**
         * Chosen from the problem it exists to solve, not picked for feel.
         *
         * Deployment nodes are at least `MIN_NODE_SPACING` (56) world units
         * apart. On the smallest supported window, 568x320dp, the fit scale is
         * 320/760 ≈ 0.42 — except the width binds first at 568/1600 ≈ 0.355,
         * so neighbouring nodes land about 20dp apart. Material's guidance for
         * a touch target is 48dp.
         *
         * 56 world units × 0.355 × zoom ≥ 48dp needs zoom ≥ 2.4. Three gives
         * that with room to spare on the smallest screen and is not so far in
         * that a player loses the lane they are looking at.
         */
        const val MAX_ZOOM = 3f

        /**
         * Below this, snap back to exactly fitted and drop the pan.
         *
         * Serves as the zoom reset. Double-tap was considered and rejected:
         * a single tap is a placement, and a double-tap detector has to hold
         * every single tap for the double-tap timeout before delivering it,
         * which would put a delay on the most common action in the game to
         * support the rarest. Pinching out already ends at the fitted board;
         * this just makes it land exactly there instead of at 1.004.
         */
        const val SNAP_TO_FIT_BELOW = 1.04f
    }
}
