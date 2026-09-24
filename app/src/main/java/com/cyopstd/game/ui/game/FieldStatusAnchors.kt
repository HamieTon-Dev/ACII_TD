package com.cyopstd.game.ui.game

import android.graphics.Paint
import android.graphics.Typeface
import com.cyopstd.game.core.WorldGeometry

/**
 * Where the WAVE and ◇ readouts sit, in world units.
 *
 * These exist because the tutorial has to point an arrow at them, and the
 * readouts are drawn inside the native Canvas world while the tutorial card is
 * Compose. An arrow across that boundary needs a number, and a number typed
 * into the tutorial by hand is a number that goes stale the first time the
 * readout moves — which it did, in 1.18.0, when the two lines were restacked.
 *
 * So the renderer and the tutorial read the same object. [BattlefieldRenderer]
 * draws its plate from these rects rather than computing its own, so the arrow
 * cannot point at where the readout used to be.
 *
 * The rects are constant for a given paint: the plate is deliberately sized
 * from fixed templates rather than from the live text, because a plate measured
 * against the number itself would resize several times a second while crypto
 * ticks up.
 */
data class WorldRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val centerX: Float get() = (left + right) * 0.5f
    val centerY: Float get() = (top + bottom) * 0.5f
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    fun contains(x: Float, y: Float): Boolean =
        x >= left && x <= right && y >= top && y <= bottom
}

class FieldStatusAnchors(
    /** The plate both lines sit on. */
    val plate: WorldRect,
    /** The `WAVE n` line. */
    val wave: WorldRect,
    /** The `◇ n` line. */
    val crypto: WorldRect
) {
    companion object {

        /**
         * Measured with the renderer's own paint settings.
         *
         * Taking a paint rather than making one means the caller passes the
         * paint that will actually draw the text, so the measurement and the
         * drawing can never drift apart.
         */
        fun measure(paint: Paint): FieldStatusAnchors {
            val previousSize = paint.textSize
            paint.textSize = BattlefieldRenderer.FIELD_STATUS_TEXT
            val widest = maxOf(
                paint.measureText(BattlefieldRenderer.WAVE_PLATE_TEMPLATE),
                paint.measureText(BattlefieldRenderer.CRYPTO_PLATE_TEMPLATE)
            )
            paint.textSize = previousSize

            val plateWidth = widest + PLATE_PADDING
            val right = WorldGeometry.WIDTH - BattlefieldRenderer.FIELD_STATUS_MARGIN
            val plateRight = right + PLATE_OVERHANG
            val plateLeft = plateRight - plateWidth

            val waveBaseline = BattlefieldRenderer.FIELD_STATUS_BASELINE
            val cryptoBaseline = waveBaseline + BattlefieldRenderer.FIELD_STATUS_LINE
            val textHeight = BattlefieldRenderer.FIELD_STATUS_TEXT

            return FieldStatusAnchors(
                plate = WorldRect(
                    left = plateLeft,
                    top = waveBaseline - textHeight - 2f,
                    right = plateRight,
                    bottom = cryptoBaseline + 10f
                ),
                // A line's box is its baseline lifted by the text height. The
                // text is right-aligned at `right`, so the box runs from the
                // plate's left edge to there.
                wave = WorldRect(
                    left = plateLeft,
                    top = waveBaseline - textHeight,
                    right = right,
                    bottom = waveBaseline + DESCENDER
                ),
                crypto = WorldRect(
                    left = plateLeft,
                    top = cryptoBaseline - textHeight,
                    right = right,
                    bottom = cryptoBaseline + DESCENDER
                )
            )
        }

        /** Space either side of the widest template. */
        const val PLATE_PADDING = 20f

        /** How far the plate runs past the text's right edge. */
        const val PLATE_OVERHANG = 10f

        /** Rough room below a baseline, so a box is not cut through the text. */
        const val DESCENDER = 6f
    }
}

/** The same rectangle in screen pixels, for a Compose layer to point at. */
fun WorldRect.toScreen(transform: WorldTransform): WorldRect = WorldRect(
    left = transform.toScreenX(left),
    top = transform.toScreenY(top),
    right = transform.toScreenX(right),
    bottom = transform.toScreenY(bottom)
)
