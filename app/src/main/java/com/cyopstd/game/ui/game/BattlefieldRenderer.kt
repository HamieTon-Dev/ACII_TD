package com.cyopstd.game.ui.game

import android.graphics.Paint
import android.graphics.Typeface
import com.cyopstd.game.core.GameMode
import com.cyopstd.game.core.GameMap
import com.cyopstd.game.core.Maps
import com.cyopstd.game.core.WorldGeometry
import com.cyopstd.game.engine.GameEngine
import com.cyopstd.game.engine.RunPhase
import com.cyopstd.game.model.AgentType
import com.cyopstd.game.model.EffectKind
import com.cyopstd.game.model.Enemy
import com.cyopstd.game.model.EnemyType
import com.cyopstd.game.ui.theme.CoreSkin
import com.cyopstd.game.ui.theme.LivingBackground
import com.cyopstd.game.ui.theme.Palette
import androidx.compose.ui.graphics.toArgb
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Draws the whole battlefield onto a single native Canvas.
 *
 * This is the reason the game can put 70 packets, 140 projectiles and a hundred
 * ASCII flourishes on screen at once: it is one draw pass with a handful of
 * reused Paint objects, rather than hundreds of Compose Text nodes each with
 * their own layout, measurement and recomposition cost. Compose still owns the
 * menus, HUD and dialogs, where its layout system is an asset rather than a
 * tax — that split is the core rendering decision of the project.
 *
 * The renderer is stateless apart from its Paints: it is handed the engine and
 * paints whatever it finds.
 */
class BattlefieldRenderer {

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    private val thinTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.CENTER
    }

    private val leftTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.LEFT
    }

    private val rightTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.RIGHT
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }

    // Colour constants resolved once.
    private val colBackground = Palette.Background.toArgb()
    private val colSurfaceSunken = Palette.SurfaceSunken.toArgb()
    private val colGrid = Palette.GridLine.toArgb()
    private val colDivider = Palette.Divider.toArgb()
    private val colLedGreen = Palette.ServerLedGreen.toArgb()
    private val colLedAmber = Palette.ServerLedAmber.toArgb()
    private val colCyan = Palette.Cyan.toArgb()
    private val colCyanDim = Palette.CyanDim.toArgb()
    private val colGreen = Palette.Green.toArgb()
    private val colRed = Palette.Red.toArgb()
    private val colOrange = Palette.Orange.toArgb()
    private val colMagenta = Palette.Magenta.toArgb()
    private val colPurple = Palette.Purple.toArgb()
    private val colBlue = Palette.Blue.toArgb()
    private val colCrypto = Palette.Crypto.toArgb()
    private val colText = Palette.TextPrimary.toArgb()

    /**
     * Plain white, for the handful of readouts that have to win.
     *
     * Not a palette entry: the palette is the game's cool cyber tint, and every
     * colour in it is chosen to sit *back*. These three numbers -- an agent's
     * level, the core's integrity and the corner readouts -- are the ones a
     * player looks for on purpose, so they are the ones allowed to be white.
     */
    private val colWhite = 0xFFFFFFFF.toInt()
    private val colMuted = Palette.TextMuted.toArgb()
    private val colSecondary = Palette.TextSecondary.toArgb()

    /** Scratch buffer used for drawing single characters without allocating. */
    private val charBuffer = CharArray(1)

    /** Reused so the per-frame chip drawing allocates nothing. */
    private val scratchRect = android.graphics.RectF()

    /**
     * Glyph widths, measured once per threat type. The width only depends on
     * the type's glyph and scale, both constant, so measuring on every frame
     * for every enemy on the field would be pure waste.
     */
    private val glyphWidths = FloatArray(EnemyType.entries.size) { -1f }

    /** Enemy draw order, back to front. Grown once, never reallocated. */
    private var enemyOrder = IntArray(0)

    /**
     * Vignette over the backdrop, built once.
     *
     * The field is wider than the action in it, and a flat fill across 1600
     * units of near-black reads as an empty document rather than as a place.
     * Darkening the periphery settles the eye on the routes and the core
     * without putting anything new on screen to read.
     */
    private val vignette = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        shader = android.graphics.RadialGradient(
            WorldGeometry.WIDTH * 0.46f,
            WorldGeometry.HEIGHT * 0.5f,
            WorldGeometry.WIDTH * 0.62f,
            intArrayOf(0x00000000, 0x00000000, 0x66000000.toInt()),
            floatArrayOf(0f, 0.55f, 1f),
            android.graphics.Shader.TileMode.CLAMP
        )
    }

    // The backdrop colour band, and the only state the renderer keeps beyond
    // its Paints. Every five waves the band changes; rather than snapping, the
    // old and new colours are cross-faded over a couple of seconds so the shift
    // registers as the room's light changing rather than as a flicker.
    /** The CORE-SERVER look, set from the player's store selection. */
    var coreSkin: CoreSkin = CoreSkin.DEFAULT

    /** The animated backdrop, set from the player's store selection. */
    var livingBackground: LivingBackground = LivingBackground.NONE

    /**
     * The SPECTRUM agent skin: agents drift through the colour wheel instead
     * of sitting at their fixed class colour.
     */
    var spectrumAgents: Boolean = false

    /** Seconds of animation, handed to the spectrum skin each frame. */
    private var frameTime: Float = 0f

    private var tintBand = -1
    private var tintFrom = Palette.backdropBands[0].toArgb()
    private var tintTo = Palette.backdropBands[0].toArgb()
    private var tintStartedAt = 0f

    fun draw(
        canvas: android.graphics.Canvas,
        engine: GameEngine,
        transform: WorldTransform,
        options: BattlefieldRenderOptions,
        selection: BattlefieldSelection,
        time: Float
    ) {
        frameTime = time
        // Taken once per frame, before anything is drawn.
        map = engine.map
        advanceRackAnimation(time, engine)
        val tint = backdropTint(engine.currentWave, time)
        // The letterbox picks up a trace of the same shift so the shift reads
        // as lighting rather than as a coloured rectangle on a black screen.
        canvas.drawColor(blend(colBackground, tint, 0.35f))

        val save = canvas.save()
        canvas.translate(transform.offsetX, transform.offsetY)
        canvas.scale(transform.scale, transform.scale)

        // Screen shake on server impact. Kept small — a hit should register, not
        // make the battlefield unreadable.
        if (options.screenShake && engine.serverHitFlash > 0f) {
            val strength = (engine.serverHitFlash / 0.45f).coerceIn(0f, 1f) * 7f
            canvas.translate(
                sin(time * 61f) * strength,
                cos(time * 73f) * strength * 0.6f
            )
        }

        drawBackdrop(canvas, engine, options, time)
        drawLanes(canvas, engine, options, time)
        drawServer(canvas, engine, time)
        drawDeploymentNodes(canvas, engine, selection, time)
        drawTarpitFields(canvas, engine)
        drawRangeIndicator(canvas, engine, selection, options)
        drawAgents(canvas, engine, selection, time)
        // Under the threats, deliberately: a 700-unit blast would otherwise
        // hide the wave that is still coming while the player celebrates the
        // one that is not.
        drawShardBursts(canvas, engine)
        drawEnemies(canvas, engine, time)
        drawProjectiles(canvas, engine)
        drawEffects(canvas, engine)
        // Drawn last, over everything.
        //
        // There is a row of nineteen deployment nodes along y=38, which is
        // exactly where these two readouts live, so whichever is drawn second
        // wins the corner. The wave and the crypto are numbers the player
        // looks up mid-fight; a node is a bracket they can still see the rest
        // of and can still tap. So the readouts go on top, on a plate, and
        // the corner node shows through it.
        drawFieldStatus(canvas, engine)
        drawBossBanner(canvas, engine, time)

        canvas.restoreToCount(save)
    }

    // ------------------------------------------------------------- backdrop

    private fun drawBackdrop(
        canvas: android.graphics.Canvas,
        engine: GameEngine,
        options: BattlefieldRenderOptions,
        time: Float
    ) {
        val tint = backdropTint(engine.currentWave, time)
        fillPaint.color = tint
        fillPaint.alpha = 255
        canvas.drawRect(0f, 0f, WorldGeometry.WIDTH, WorldGeometry.HEIGHT, fillPaint)

        // Static grid: always drawn, it costs almost nothing and gives the
        // battlefield a sense of scale. It leans towards the current tint so
        // it never fights the backdrop it sits on.
        strokePaint.color = blend(colGrid, tint, 0.45f)
        strokePaint.alpha = 70
        strokePaint.strokeWidth = 1f
        var x = 0f
        while (x <= WorldGeometry.WIDTH) {
            canvas.drawLine(x, 0f, x, WorldGeometry.HEIGHT, strokePaint)
            x += 80f
        }
        var y = 0f
        while (y <= WorldGeometry.HEIGHT) {
            canvas.drawLine(0f, y, WorldGeometry.WIDTH, y, strokePaint)
            y += 80f
        }

        canvas.drawRect(0f, 0f, WorldGeometry.WIDTH, WorldGeometry.HEIGHT, vignette)

        if (!options.backgroundAnimation || options.batterySaver) return

        drawLivingBackground(canvas, engine, time)

        // Drifting binary chatter, sparse enough to stay behind the gameplay.
        thinTextPaint.textSize = 15f
        thinTextPaint.color = colGrid
        for (i in 0 until BACKDROP_GLYPHS) {
            val seedX = (i * 137 % 1600).toFloat()
            val seedY = (i * 311 % 760).toFloat()
            val drift = (time * (16f + (i % 7) * 7f)) % (WorldGeometry.WIDTH + 120f)
            val px = (seedX + drift) % WorldGeometry.WIDTH
            val alpha = (40 + 55 * abs(sin(time * 0.7f + i))).toInt().coerceIn(0, 255)
            thinTextPaint.alpha = alpha
            charBuffer[0] = if ((i + (time * 2).toInt()) % 2 == 0) '0' else '1'
            canvas.drawText(charBuffer, 0, 1, px, seedY, thinTextPaint)
        }
        thinTextPaint.alpha = 255
    }

    /**
     * The backdrop colour for [wave], eased across band changes.
     *
     * Returns the plain band colour whenever a transition is not running, so
     * the common case costs a divide and an array lookup.
     */
    private fun backdropTint(wave: Int, time: Float): Int {
        val band = (wave - 1).coerceAtLeast(0) / Palette.BACKDROP_BAND_WAVES
        if (band != tintBand) {
            tintFrom = if (tintBand < 0) Palette.backdropBand(wave).toArgb() else tintTo
            tintTo = Palette.backdropBand(wave).toArgb()
            tintBand = band
            tintStartedAt = time
        }
        val elapsed = time - tintStartedAt
        if (elapsed >= TINT_FADE_SECONDS) return tintTo
        val linear = (elapsed / TINT_FADE_SECONDS).coerceIn(0f, 1f)
        // Smoothstep: no visible start or stop to the fade.
        return blend(tintFrom, tintTo, linear * linear * (3f - 2f * linear))
    }

    /** Mix [target] into [base] by [amount], ignoring alpha (both are opaque). */
    private fun blend(base: Int, target: Int, amount: Float): Int {
        val t = amount.coerceIn(0f, 1f)
        val r = ((base shr 16 and 0xFF) + ((target shr 16 and 0xFF) - (base shr 16 and 0xFF)) * t).toInt()
        val g = ((base shr 8 and 0xFF) + ((target shr 8 and 0xFF) - (base shr 8 and 0xFF)) * t).toInt()
        val b = ((base and 0xFF) + ((target and 0xFF) - (base and 0xFF)) * t).toInt()
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    /**
     * Wave and crypto, small and dim, in the corners of the field itself.
     *
     * The same two numbers are already in the Compose strip above the
     * battlefield. They are repeated here because those are the two you check
     * constantly while placing agents, and looking away from the lanes to read
     * them costs you the thing you were watching.
     *
     * Everything about this is chosen to stay out of the way: it sits in the
     * dead band above the top lane (which starts at y=73), it is drawn before
     * the lanes and everything on them so gameplay always paints over it, and
     * it runs at two-thirds alpha so it reads as a watermark rather than as
     * another panel.
     */
    /**
     * WAVE and ◇ CRYPTO, stacked in the top-right corner.
     *
     * They used to sit in opposite corners, and both were in trouble there. The
     * top-left is crowded — the ATTACK ORIGIN marker is directly under it and
     * the first lane starts at y=73, which leaves no room for a second line —
     * and *both* corners sat on the row of deployment nodes along y=38, so in
     * 1.17.0 the readouts had to be drawn last simply to win the pixels.
     *
     * The block above the rack is the one piece of the field nothing else uses:
     * no node is placed past x=1240 and the rack itself starts at y=168, so a
     * stack here collides with nothing at all. Putting the wave directly above
     * the money also puts the two numbers a player checks most in one glance
     * instead of at opposite ends of a 1600-unit board.
     */
    /**
     * Where the two readouts are, for the tutorial to point at.
     *
     * Computed once: the plate is sized from fixed templates, so it does not
     * move. Drawn *from* this rather than alongside it, so an arrow aimed here
     * can never point at where the readout used to be.
     */
    /**
     * The level being drawn, taken from the engine at the top of every frame.
     *
     * An instance field rather than a parameter on a dozen private draw calls,
     * and read from the engine rather than from a global: the renderer draws
     * what the simulation is running, and the one thing that must be
     * impossible is for those two to disagree about which map that is.
     */
    private var map: GameMap = Maps.PERIMETER

    val fieldStatusAnchors: FieldStatusAnchors by lazy {
        FieldStatusAnchors.measure(rightTextPaint)
    }

    private fun drawFieldStatus(canvas: android.graphics.Canvas, engine: GameEngine) {
        rightTextPaint.textSize = FIELD_STATUS_TEXT
        // Never WAVE 0. Before the first wave there is nothing behind you and
        // wave 1 is what is coming, which is what the HUD ("--") and the
        // preparation banner ("PERIMETER READY") already say in their own way.
        // The readout was the only thing claiming a wave zero existed.
        val waveText = "WAVE ${engine.currentWave.coerceAtLeast(1)}"
        val cryptoText = "\u25C7 ${engine.crypto}"

        val anchors = fieldStatusAnchors
        val right = WorldGeometry.WIDTH - FIELD_STATUS_MARGIN
        fillPaint.color = colBackground
        fillPaint.alpha = FIELD_STATUS_PLATE_ALPHA
        canvas.drawRoundRect(
            anchors.plate.left,
            anchors.plate.top,
            anchors.plate.right,
            anchors.plate.bottom,
            6f,
            6f,
            fillPaint
        )
        fillPaint.alpha = 255

        rightTextPaint.color = colWhite
        rightTextPaint.alpha = 255
        canvas.drawText(waveText, right, FIELD_STATUS_BASELINE, rightTextPaint)

        rightTextPaint.color = colCrypto
        canvas.drawText(
            cryptoText,
            right,
            FIELD_STATUS_BASELINE + FIELD_STATUS_LINE,
            rightTextPaint
        )

        // The run's name, set smaller than either number and away from them:
        // it identifies the run without competing with the figures that change
        // during it.
        textPaint.textSize = RUN_NAME_TEXT
        textPaint.color = if (engine.mode == GameMode.STANDARD) colMuted else colOrange
        textPaint.alpha = RUN_NAME_ALPHA
        canvas.drawText(
            engine.mode.runName,
            WorldGeometry.WIDTH * 0.5f,
            FIELD_STATUS_BASELINE - 4f,
            textPaint
        )
        textPaint.alpha = 255
    }

    /**
     * The player's animated backdrop, if they own one.
     *
     * Drawn inside the backdrop pass, so the lanes, threats, agents and the
     * core all paint over it. Every effect is capped by the background's own
     * [LivingBackground.intensity], which is in the low tens out of 255 — the
     * point is something you notice in a quiet moment between waves, not
     * something you have to see past during one.
     */
    private fun drawLivingBackground(
        canvas: android.graphics.Canvas,
        engine: GameEngine,
        time: Float
    ) {
        val skin = livingBackground
        if (skin == LivingBackground.NONE) return
        val tint = skin.tint.toArgb()
        val peak = skin.intensity
        val w = WorldGeometry.WIDTH
        val h = WorldGeometry.HEIGHT

        when (skin) {
            LivingBackground.NONE -> Unit

            LivingBackground.DRIFT -> {
                strokePaint.color = tint
                strokePaint.strokeWidth = 2f
                for (i in 0 until 26) {
                    val span = w + 420f
                    val x = ((i * 137f) + time * skin.speed * span) % span - 210f
                    val y = (i * 311f) % h
                    strokePaint.alpha = (peak * (0.4f + 0.6f * abs(sin(i * 1.7f)))).toInt()
                    canvas.drawLine(x, y, x + 150f, y + 84f, strokePaint)
                }
            }

            LivingBackground.LATTICE -> {
                // Grid nodes that swell with how much traffic is on the board.
                val load = (engine.activeEnemyCount() / 16f).coerceIn(0f, 1f)
                fillPaint.color = tint
                var gx = 60f
                var index = 0
                while (gx < w) {
                    var gy = 60f
                    while (gy < h) {
                        val phase = sin(time * skin.speed * 6.28f + index * 0.7f)
                        val radius = 2.2f + 2.4f * (0.5f + 0.5f * phase) * (0.6f + load)
                        fillPaint.alpha = (peak * (0.45f + 0.55f * (0.5f + 0.5f * phase))).toInt()
                        canvas.drawCircle(gx, gy, radius, fillPaint)
                        gy += 100f
                        index++
                    }
                    gx += 100f
                }
                strokePaint.color = tint
                strokePaint.alpha = (peak * 0.35f).toInt()
                strokePaint.strokeWidth = 1f
                var ly = 60f
                while (ly < h) {
                    canvas.drawLine(60f, ly, w - 60f, ly, strokePaint)
                    ly += 100f
                }
            }

            LivingBackground.AURORA -> {
                // Wide soft bands. Drawn as a few thick, very faint strokes
                // that slide across each other.
                strokePaint.strokeWidth = 86f
                for (i in 0 until 5) {
                    val drift = sin(time * skin.speed * 6.28f + i * 1.3f)
                    val y = h * (0.18f + i * 0.17f) + drift * 46f
                    strokePaint.color = tint
                    strokePaint.alpha = (peak * (0.35f + 0.4f * (0.5f + 0.5f * drift))).toInt()
                    canvas.drawLine(0f, y, w, y + drift * 30f, strokePaint)
                }
                strokePaint.strokeWidth = 2f
            }

            LivingBackground.RAINFALL -> {
                thinTextPaint.textSize = 14f
                thinTextPaint.color = tint
                for (col in 0 until 34) {
                    val x = 24f + col * 46f
                    val speed = 90f + (col % 5) * 46f
                    val head = (time * speed * skin.speed * 4f + col * 73f) % (h + 190f)
                    for (n in 0 until 6) {
                        val y = head - n * 26f
                        if (y < 0f || y > h) continue
                        thinTextPaint.alpha = (peak * (1f - n / 6f)).toInt().coerceAtLeast(0)
                        charBuffer[0] = if (((col + n + (time * 3).toInt()) % 2) == 0) '1' else '0'
                        canvas.drawText(charBuffer, 0, 1, x, y, thinTextPaint)
                    }
                }
                thinTextPaint.alpha = 255
            }

            LivingBackground.PULSE -> {
                // Rings leaving the core, so the eye is drawn towards the thing
                // being defended rather than away from it.
                val cx = WorldGeometry.SERVER_X
                val cy = WorldGeometry.CORE_Y
                strokePaint.color = tint
                strokePaint.strokeWidth = 2f
                for (i in 0 until 5) {
                    val phase = (time * skin.speed + i / 5f) % 1f
                    strokePaint.alpha = (peak * (1f - phase)).toInt().coerceAtLeast(0)
                    canvas.drawCircle(cx, cy, 90f + phase * 1250f, strokePaint)
                }
            }
        }
        strokePaint.alpha = 255
        fillPaint.alpha = 255
        strokePaint.strokeWidth = 2f
    }

    // ---------------------------------------------------------------- lanes

    /** Reusable path objects; the renderer must not allocate per frame. */
    private val lanePath = android.graphics.Path()
    private val pathScratch = FloatArray(3)

    /**
     * Draw the serpentine routes.
     *
     * Each route is one polyline stroked twice: a wide pass in the border
     * colour, then a narrower pass in the corridor fill. That gives a bordered
     * corridor of exact width around arbitrary bends, which drawing rectangles
     * per segment cannot do without ugly notches at every corner.
     */
    private fun drawLanes(
        canvas: android.graphics.Canvas,
        engine: GameEngine,
        options: BattlefieldRenderOptions,
        time: Float
    ) {
        for (lane in 0 until map.laneCount) {
            val points = map.laneWaypoints[lane]

            lanePath.rewind()
            lanePath.moveTo(points[0].x, points[0].y)
            for (i in 1 until points.size) lanePath.lineTo(points[i].x, points[i].y)

            // Corridor border.
            strokePaint.color = colCyanDim
            strokePaint.alpha = 150
            strokePaint.strokeWidth = WorldGeometry.LANE_HEIGHT
            strokePaint.strokeCap = android.graphics.Paint.Cap.ROUND
            strokePaint.strokeJoin = android.graphics.Paint.Join.ROUND
            canvas.drawPath(lanePath, strokePaint)

            // Corridor interior. The living background pulls it towards its
            // own palette rather than replacing it, so the corridor keeps its
            // value and stays readable against the backdrop.
            val base = Palette.laneTints[lane % Palette.laneTints.size].toArgb()
            strokePaint.color = livingBackground.laneTint
                ?.let { blend(base, it.toArgb(), LANE_TINT_STRENGTH) }
                ?: base
            strokePaint.alpha = 255
            strokePaint.strokeWidth = WorldGeometry.LANE_HEIGHT - 4f
            canvas.drawPath(lanePath, strokePaint)

            strokePaint.strokeCap = android.graphics.Paint.Cap.BUTT
            strokePaint.strokeJoin = android.graphics.Paint.Join.MITER

            drawLaneFlow(canvas, lane, options, time)
            drawLaneLabel(canvas, lane)
        }

        // Entry marker, sitting in the gap between the field status line above
        // it and the top lane below it.
        val entry = map.entryPoint(0)
        leftTextPaint.textSize = 17f
        leftTextPaint.color = colRed
        leftTextPaint.alpha = 200
        canvas.drawText(
            "ATTACK ORIGIN",
            FIELD_STATUS_MARGIN,
            entry.y - WorldGeometry.LANE_HEIGHT * 0.5f - 12f,
            leftTextPaint
        )
        leftTextPaint.alpha = 255
    }

    /** Flowing chevrons along a route, oriented to whichever bend they are on. */
    private fun drawLaneFlow(
        canvas: android.graphics.Canvas,
        lane: Int,
        options: BattlefieldRenderOptions,
        time: Float
    ) {
        val length = map.laneLength[lane]
        val spacing = 96f
        val speed = if (options.backgroundAnimation && !options.batterySaver) 46f else 0f
        val shift = (time * speed) % spacing
        val baseAlpha = if (options.batterySaver) 70 else 125

        thinTextPaint.textSize = 16f
        thinTextPaint.color = colCyanDim

        var distance = shift
        while (distance < length - 30f) {
            map.positionAt(lane, distance, pathScratch)
            val pulse = 0.55f + 0.45f * sin(distance * 0.012f - time * 2.6f)
            thinTextPaint.alpha = (baseAlpha * pulse).toInt().coerceIn(0, 255)

            val save = canvas.save()
            canvas.translate(pathScratch[0], pathScratch[1])
            canvas.rotate(Math.toDegrees(pathScratch[2].toDouble()).toFloat())
            canvas.drawText(">>", 0f, 5f, thinTextPaint)
            canvas.restoreToCount(save)

            distance += spacing
        }
        thinTextPaint.alpha = 255
    }

    /** Route label, placed just off the corridor at the entry. */
    /**
     * The route's name, painted inside its own corridor like a road marking.
     *
     * There is nowhere outside the corridor to put it. Above the lane it
     * collides with the ATTACK ORIGIN marker; below, it lands inside the first
     * deployment node's bracket, because a node must clear the route by 58
     * units and the gap between the lane edge and the top of that bracket is
     * only eight. Inside the corridor nothing else is ever drawn, and threats
     * passing over it occlude it cleanly now that they carry opaque chips.
     */
    private fun drawLaneLabel(canvas: android.graphics.Canvas, lane: Int) {
        val entry = map.entryPoint(lane)
        leftTextPaint.textSize = 14f
        leftTextPaint.color = colCyanDim
        leftTextPaint.alpha = 165
        canvas.drawText(
            "ROUTE ${'A' + lane}",
            FIELD_STATUS_MARGIN + 2f,
            entry.y + 5f,
            leftTextPaint
        )
        leftTextPaint.alpha = 255
    }

    // --------------------------------------------------------------- server

    /**
     * CORE-SERVER: a rack-mounted box drawn from ASCII box characters, with
     * blinking activity LEDs that speed up while traffic is being processed and
     * turn into an alarm pattern when the server is hurt.
     */
    // ------------------------------------------------- rack animation clocks

    /**
     * Phases for the two rack animations that change speed with board load.
     *
     * The integration, and the reason it has to be an integration rather than
     * `time * rate`, is in [RackAnimation]. It was a real bug: the chase
     * teleported and the LEDs flickered every time a threat died.
     */
    private val rack = RackAnimation()

    private fun advanceRackAnimation(time: Float, engine: GameEngine) {
        rack.advance(time, rackLoad(engine))
    }

    /** How busy the board is, 0..1.6. Drives both rack animations' speed. */
    private fun rackLoad(engine: GameEngine): Float =
        (engine.activeEnemyCount() / 14f).coerceIn(0f, 1.6f)

    private fun drawServer(canvas: android.graphics.Canvas, engine: GameEngine, time: Float) {
        val left = WorldGeometry.SERVER_X
        val top = WorldGeometry.SERVER_TOP
        val right = left + WorldGeometry.SERVER_WIDTH
        val bottom = top + WorldGeometry.SERVER_HEIGHT

        val hpFraction = if (engine.serverMaxHp <= 0) 0f
        else engine.serverHp.toFloat() / engine.serverMaxHp
        val damaged = engine.serverHitFlash > 0f
        // Damage and critical integrity override the skin on purpose: a skin
        // may never make "the core is being hit" harder to read than it is on
        // the default one.
        val accent = when {
            damaged -> colRed
            hpFraction <= 0.3f -> colOrange
            else -> coreSkin.accent.toArgb()
        }

        // Chassis.
        fillPaint.color = coreSkin.chassis.toArgb()
        fillPaint.alpha = 240
        canvas.drawRect(left, top, right, bottom, fillPaint)

        drawCoreFlourish(canvas, left, top, right, bottom, time)

        strokePaint.color = accent
        strokePaint.alpha = if (damaged) 255 else 190
        strokePaint.strokeWidth = 3f
        canvas.drawRect(left, top, right, bottom, strokePaint)

        if (damaged) {
            glowPaint.color = colRed
            glowPaint.alpha = (140 * (engine.serverHitFlash / 0.45f)).toInt().coerceIn(0, 255)
            glowPaint.strokeWidth = 10f
            canvas.drawRect(left - 6f, top - 6f, right + 6f, bottom + 6f, glowPaint)
        }

        // Top rule: +================+
        leftTextPaint.textSize = 19f
        leftTextPaint.color = accent
        leftTextPaint.alpha = 220
        canvas.drawText("+================+", left + 14f, top + 26f, leftTextPaint)

        // Identity and integrity.
        leftTextPaint.textSize = 22f
        leftTextPaint.color = colText
        canvas.drawText("| CORE-SERVER    |", left + 14f, top + 56f, leftTextPaint)

        leftTextPaint.textSize = 19f
        leftTextPaint.color = accent
        canvas.drawText("| [::::SYSTEM:::]|", left + 14f, top + 84f, leftTextPaint)
        leftTextPaint.color = coreSkin.trim.toArgb()
        canvas.drawText("| DATA CORE      |", left + 14f, top + 110f, leftTextPaint)

        // Activity LEDs. They get busier the more traffic is on the board,
        // which turns the rack into an at-a-glance load indicator.
        //
        // Two colours, not one. A real rack sits mostly green — link and power
        // — with amber scattered through it for activity and warnings, and
        // that mix is most of what makes it read as hardware. Which light is
        // which is fixed per position rather than random per frame, because
        // an LED that changes colour is not an LED.
        val load = rackLoad(engine)
        thinTextPaint.textSize = 20f
        for (row in 0 until LED_ROWS) {
            val ly = top + 140f + row * 26f
            for (col in 0 until LED_COLUMNS) {
                val lx = left + 30f + col * 25f
                val seed = row * 7.31f + col * 3.77f
                val pulse = sin(rack.ledPhase + seed)
                val lit = pulse > (0.55f - load * 0.35f)
                // Roughly one light in three is the second colour.
                val amber = (row * LED_COLUMNS + col) % 3 == 1
                thinTextPaint.color = when {
                    damaged && lit -> colRed
                    lit && amber -> colLedAmber
                    lit -> colLedGreen
                    else -> colMuted
                }
                thinTextPaint.alpha = if (lit) 235 else 70
                charBuffer[0] = if (lit) '•' else '.'
                canvas.drawText(charBuffer, 0, 1, lx, ly, thinTextPaint)
            }
        }
        thinTextPaint.alpha = 255

        drawRacetrack(canvas, left, right, top, bottom, damaged, load, time)

        // Integrity bar: [==========]
        val barLeft = left + 26f
        val barRight = right - 26f
        val barTop = bottom - 76f
        val barBottom = barTop + 22f

        fillPaint.color = colSurfaceSunken
        fillPaint.alpha = 255
        canvas.drawRect(barLeft, barTop, barRight, barBottom, fillPaint)

        fillPaint.color = Palette.healthColor(hpFraction).toArgb()
        canvas.drawRect(
            barLeft, barTop,
            barLeft + (barRight - barLeft) * hpFraction.coerceIn(0f, 1f), barBottom,
            fillPaint
        )

        strokePaint.color = accent
        strokePaint.strokeWidth = 2f
        strokePaint.alpha = 200
        canvas.drawRect(barLeft, barTop, barRight, barBottom, strokePaint)

        // Integrity figures. The number the chase circuit is drawn around, so
        // it is set large enough to be worth framing.
        textPaint.textSize = INTEGRITY_TEXT
        textPaint.color = colWhite
        textPaint.alpha = 255
        canvas.drawText(
            "${engine.serverHp} / ${engine.serverMaxHp}",
            (left + right) * 0.5f, bottom - 20f, textPaint
        )

        // Alarm banner while integrity is critical.
        if (hpFraction <= 0.3f && engine.phase != RunPhase.GAME_OVER) {
            val flash = (0.5f + 0.5f * sin(time * 7f))
            textPaint.textSize = 17f
            textPaint.color = colRed
            textPaint.alpha = (120 + 135 * flash).toInt().coerceIn(0, 255)
            canvas.drawText("!! INTEGRITY LOW !!", (left + right) * 0.5f, top - 14f, textPaint)
            textPaint.alpha = 255
        }
    }

    /**
     * The mark that makes a skin recognisable at a glance.
     *
     * Drawn under the rack's text and LEDs rather than over them, so a skin can
     * change how the core *looks* without ever making its readouts — identity,
     * integrity, the load lights — harder to read.
     */
    private fun drawCoreFlourish(
        canvas: android.graphics.Canvas,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        time: Float
    ) {
        val cx = (left + right) * 0.5f
        val cy = (top + bottom) * 0.5f
        val accent = coreSkin.accent.toArgb()

        // Clipped to the rack. A flourish is decoration on a specific object,
        // and several of them (traces, grids, rings) are drawn with maths that
        // naturally overruns the box -- PCB's trace stubs were escaping below
        // the chassis. Clipping means a new skin cannot leak onto the
        // battlefield no matter how its pattern is generated.
        val clip = canvas.save()
        canvas.clipRect(left, top, right, bottom)

        when (coreSkin.flourish) {
            CoreSkin.Flourish.NONE -> Unit

            CoreSkin.Flourish.RING -> {
                // Containment: concentric rings that breathe.
                val pulse = 0.5f + 0.5f * sin(time * 1.6f)
                for (i in 0 until 4) {
                    strokePaint.color = accent
                    strokePaint.alpha = (58 - i * 11).coerceAtLeast(8)
                    strokePaint.strokeWidth = 1.6f
                    canvas.drawCircle(cx, cy, 52f + i * 26f + pulse * 7f, strokePaint)
                }
            }

            CoreSkin.Flourish.TRACES -> {
                // Gold PCB traces: right-angled runs, like a board.
                strokePaint.color = accent
                strokePaint.strokeWidth = 1.4f
                strokePaint.alpha = 40
                var y = top + 34f
                var step = 0
                while (y < bottom - 24f) {
                    val inset = 18f + (step % 3) * 26f
                    canvas.drawLine(left + inset, y, right - inset, y, strokePaint)
                    canvas.drawLine(right - inset, y, right - inset, y + 30f, strokePaint)
                    y += 62f
                    step++
                }
            }

            CoreSkin.Flourish.FROST -> {
                // Ice: short radiating needles from the centre.
                strokePaint.color = accent
                strokePaint.strokeWidth = 1.3f
                for (i in 0 until 18) {
                    val angle = i * (TWO_PI_F / 18f) + time * 0.06f
                    val inner = 40f
                    val outer = 104f + 26f * sin(i * 2.1f)
                    strokePaint.alpha = 34
                    canvas.drawLine(
                        cx + cos(angle) * inner, cy + sin(angle) * inner,
                        cx + cos(angle) * outer, cy + sin(angle) * outer,
                        strokePaint
                    )
                }
            }

            CoreSkin.Flourish.SCANLINES -> {
                // CRT phosphor: tight horizontal lines plus a bright band that
                // sweeps down the rack like a refresh.
                strokePaint.color = accent
                strokePaint.strokeWidth = 1f
                var y = top + 4f
                while (y < bottom) {
                    strokePaint.alpha = 22
                    canvas.drawLine(left + 3f, y, right - 3f, y, strokePaint)
                    y += 4f
                }
                val sweep = top + ((time * 46f) % (bottom - top))
                for (k in 0 until 10) {
                    strokePaint.alpha = (52 - k * 5).coerceAtLeast(0)
                    canvas.drawLine(left + 3f, sweep + k, right - 3f, sweep + k, strokePaint)
                }
            }

            CoreSkin.Flourish.CASCADE -> {
                // Code falling inside the rack itself.
                thinTextPaint.textSize = 13f
                thinTextPaint.color = accent
                for (col in 0 until 8) {
                    val cxx = left + 22f + col * 30f
                    val head = ((time * (34f + col * 9f)) + col * 57f) %
                        (WorldGeometry.SERVER_HEIGHT + 120f)
                    for (n in 0 until 9) {
                        val yy = top + head - n * 17f
                        if (yy < top + 6f || yy > bottom - 6f) continue
                        thinTextPaint.alpha = (58 - n * 6).coerceAtLeast(0)
                        charBuffer[0] = CASCADE_GLYPHS[(col * 7 + n + (time * 4).toInt()) %
                            CASCADE_GLYPHS.size]
                        canvas.drawText(charBuffer, 0, 1, cxx, yy, thinTextPaint)
                    }
                }
                thinTextPaint.alpha = 255
            }

            CoreSkin.Flourish.GRID -> {
                // A grid receding towards a vanishing point at the core.
                strokePaint.color = accent
                strokePaint.strokeWidth = 1.3f
                val vx = (left + right) * 0.5f
                val vy = (top + bottom) * 0.5f
                for (i in -4..4) {
                    strokePaint.alpha = 46
                    canvas.drawLine(vx, vy, vx + i * 120f, bottom, strokePaint)
                    canvas.drawLine(vx, vy, vx + i * 120f, top, strokePaint)
                }
                for (i in 1 until 7) {
                    val t = i / 7f
                    val spread = t * t * (bottom - vy)
                    strokePaint.alpha = (52 * (1f - t * 0.6f)).toInt()
                    canvas.drawLine(left + 3f, vy + spread, right - 3f, vy + spread, strokePaint)
                    canvas.drawLine(left + 3f, vy - spread, right - 3f, vy - spread, strokePaint)
                }
            }

            CoreSkin.Flourish.STARFIELD -> {
                // Deep space: a fixed field of faint points that twinkle.
                fillPaint.color = accent
                for (i in 0 until 46) {
                    val px = left + 16f + (i * 97 % (WorldGeometry.SERVER_WIDTH - 32f).toInt())
                    val py = top + 16f + (i * 53 % (WorldGeometry.SERVER_HEIGHT - 32f).toInt())
                    fillPaint.alpha = (30 + 55 * abs(sin(time * 0.8f + i))).toInt().coerceIn(0, 255)
                    canvas.drawCircle(px, py, 1.5f, fillPaint)
                }
            }
        }
        canvas.restoreToCount(clip)
        strokePaint.alpha = 255
        fillPaint.alpha = 255
    }

    /**
     * The chase around the integrity block.
     *
     * Lights run a closed circuit around the integrity bar and its numbers
     * with comets travelling it and fading behind, like the addressable strips
     * these racks are actually built with. Framing the readout rather than
     * sitting beside it means the chase draws the eye to the number that
     * matters, and it speeds up with board load, so it carries the same
     * information as the LED grid in a form the eye catches peripherally.
     *
     * NEONGRID skims its lights slowly between blue and green like a
     * holographic foil. That is the one place a skin colours a light, because
     * it is the skin's signature rather than an indicator: the green-and-amber
     * status LEDs above are untouched.
     */
    private fun drawRacetrack(
        canvas: android.graphics.Canvas,
        left: Float,
        right: Float,
        top: Float,
        bottom: Float,
        damaged: Boolean,
        load: Float,
        time: Float
    ) {
        val style = coreSkin.chase
        val holographic = style != CoreSkin.Chase.LOOP
        val ring = style == CoreSkin.Chase.RING_HOLOGRAPHIC
        val count = if (ring) RING_LEDS else LOOP_LEDS
        val cx = (left + right) * 0.5f

        // The circuit encloses the bar (which starts 76 units up from the
        // bottom of the rack) and the figures beneath it.
        val loopLeft = left + 13f
        val loopRight = right - 13f
        val loopTop = bottom - 90f
        val loopBottom = bottom - 8f
        val sideW = loopRight - loopLeft
        val sideH = loopBottom - loopTop
        val perimeter = 2f * (sideW + sideH)

        // Two comets on opposite sides of the circuit, so it reads as a loop
        // rather than as a single dot sliding back and forth. The head comes
        // from an integrated phase, not from `time * rate` -- see
        // advanceRackAnimation for why that distinction is the whole bug.
        val head = rack.chasePhase

        for (i in 0 until count) {
            val at = i / count.toFloat()
            val px: Float
            val py: Float
            if (ring) {
                val angle = at * TWO_PI_F - TWO_PI_F * 0.25f
                px = cx + cos(angle) * RING_RADIUS
                py = bottom - 114f + sin(angle) * RING_RADIUS
            } else {
                // Walk the perimeter clockwise from the top-left corner.
                val d = at * perimeter
                when {
                    d < sideW -> { px = loopLeft + d; py = loopTop }
                    d < sideW + sideH -> { px = loopRight; py = loopTop + (d - sideW) }
                    d < 2f * sideW + sideH -> {
                        px = loopRight - (d - sideW - sideH); py = loopBottom
                    }
                    else -> { px = loopLeft; py = loopBottom - (d - 2f * sideW - sideH) }
                }
            }

            var best = 1f
            for (comet in 0 until 2) {
                val h = (head + comet * 0.5f) % 1f
                var gap = h - at
                if (gap < 0f) gap += 1f
                if (gap < best) best = gap
            }
            val glow = (1f - best * RACETRACK_TAIL).coerceIn(0f, 1f)

            fillPaint.color = when {
                damaged -> colRed
                holographic -> holographic(time, at)
                glow > 0.55f -> colLedGreen
                else -> colLedAmber
            }
            fillPaint.alpha = (22 + 233 * glow * glow).toInt().coerceIn(0, 255)
            canvas.drawCircle(px, py, 2.2f + 2.0f * glow, fillPaint)
        }
        fillPaint.alpha = 255
    }

    /**
     * Holographic foil: a slow blue-to-green skim with a spatial offset, so
     * neighbouring lights sit at slightly different points in the sweep and
     * the circuit shows a gradient rather than flashing as one block.
     */
    private fun holographic(time: Float, position: Float): Int {
        val phase = 0.5f + 0.5f * sin(TWO_PI_F * (time * HOLO_SPEED + position * 0.85f))
        val r = (0x3A + (0x2B - 0x3A) * phase).toInt()
        val g = (0x86 + (0xFF - 0x86) * phase).toInt()
        val b = (0xFF + (0x9C - 0xFF) * phase).toInt()
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    // ----------------------------------------------------------------- nodes

    private fun drawDeploymentNodes(
        canvas: android.graphics.Canvas,
        engine: GameEngine,
        selection: BattlefieldSelection,
        time: Float
    ) {
        val pending = selection.pendingAgent
        val placing = pending != null
        val affordable = pending?.let { engine.crypto >= it.cost } ?: false

        for (node in map.nodes) {
            if (engine.agentAt(node.id) != null) continue

            // A spot the agent being placed could not actually shoot from is
            // shown, but shown as unusable, so the deploy overlay reads as
            // advice rather than as a field of identical brackets.
            val inReach = pending == null || node.laneDistance <= pending.baseRange
            val pulse = 0.6f + 0.4f * sin(time * 4.4f + node.id * 0.6f)

            strokePaint.strokeWidth = if (placing && inReach) 2.4f else 1.4f
            strokePaint.color = when {
                !placing -> colCyanDim
                !inReach -> colMuted
                affordable -> colGreen
                else -> colOrange
            }
            strokePaint.alpha = when {
                !placing -> 55
                !inReach -> 45
                else -> (140 + 115 * pulse).toInt().coerceIn(0, 255)
            }

            val r = WorldGeometry.NODE_RADIUS
            // A bracket pair rather than a circle: it reads as a terminal slot.
            canvas.drawLine(node.x - r, node.y - r, node.x - r + 10f, node.y - r, strokePaint)
            canvas.drawLine(node.x - r, node.y - r, node.x - r, node.y - r + 10f, strokePaint)
            canvas.drawLine(node.x + r, node.y - r, node.x + r - 10f, node.y - r, strokePaint)
            canvas.drawLine(node.x + r, node.y - r, node.x + r, node.y - r + 10f, strokePaint)
            canvas.drawLine(node.x - r, node.y + r, node.x - r + 10f, node.y + r, strokePaint)
            canvas.drawLine(node.x - r, node.y + r, node.x - r, node.y + r - 10f, strokePaint)
            canvas.drawLine(node.x + r, node.y + r, node.x + r - 10f, node.y + r, strokePaint)
            canvas.drawLine(node.x + r, node.y + r, node.x + r, node.y + r - 10f, strokePaint)

            if (placing) {
                thinTextPaint.textSize = 18f
                thinTextPaint.color = strokePaint.color
                thinTextPaint.alpha = strokePaint.alpha
                // A cross marks a spot worth taking; a dash, one that is not.
                canvas.drawText(if (inReach) "+" else "-", node.x, node.y + 7f, thinTextPaint)
                thinTextPaint.alpha = 255
            }
        }
    }

    /**
     * Range rings are only drawn for a selected or about-to-be-placed agent,
     * never permanently — a battlefield full of overlapping circles is unreadable.
     */
    private fun drawRangeIndicator(
        canvas: android.graphics.Canvas,
        engine: GameEngine,
        selection: BattlefieldSelection,
        options: BattlefieldRenderOptions
    ) {
        if (!options.showAgentRange) return

        val selected = selection.selectedNodeId?.let { engine.agentAt(it) }
        if (selected != null) {
            drawScanRing(canvas, selected.x, selected.y, selected.range(), colCyan)
            return
        }

        val pending = selection.pendingAgent ?: return

        // While placing, a ring on every free node would be unreadable noise. We
        // draw one representative preview instead, on the free node nearest the
        // middle of the battlefield, so the player can judge how much lane a
        // deployment actually covers before committing to a node.
        var preview: com.cyopstd.game.core.NodePosition? = null
        var bestDistanceSq = Float.MAX_VALUE
        val centreX = WorldGeometry.SERVER_X * 0.5f
        val centreY = WorldGeometry.HEIGHT * 0.5f
        for (node in map.nodes) {
            if (engine.agentAt(node.id) != null) continue
            val dx = node.x - centreX
            val dy = node.y - centreY
            val distanceSq = dx * dx + dy * dy
            if (distanceSq < bestDistanceSq) {
                bestDistanceSq = distanceSq
                preview = node
            }
        }
        val node = preview ?: return
        drawScanRing(canvas, node.x, node.y, pending.baseRange, colGreen, preview = true)
    }

    private fun drawScanRing(
        canvas: android.graphics.Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        color: Int,
        preview: Boolean = false
    ) {
        fillPaint.color = color
        fillPaint.alpha = if (preview) 14 else 22
        canvas.drawCircle(cx, cy, radius, fillPaint)

        strokePaint.color = color
        strokePaint.alpha = if (preview) 110 else 170
        strokePaint.strokeWidth = 2f
        canvas.drawCircle(cx, cy, radius, strokePaint)

        // ASCII scan ticks around the perimeter instead of a plain outline.
        thinTextPaint.textSize = 14f
        thinTextPaint.color = color
        thinTextPaint.alpha = if (preview) 120 else 190
        var angle = 0f
        while (angle < 6.28f) {
            val px = cx + cos(angle) * radius
            val py = cy + sin(angle) * radius
            charBuffer[0] = '·'
            canvas.drawText(charBuffer, 0, 1, px, py + 4f, thinTextPaint)
            angle += 0.30f
        }
        thinTextPaint.textSize = 13f
        canvas.drawText("SCAN RANGE ${radius.toInt()}", cx, cy - radius - 8f, thinTextPaint)
        thinTextPaint.alpha = 255
    }

    // ---------------------------------------------------------------- agents

    /**
     * The TARPIT's slow field, drawn faintly and permanently.
     *
     * Range rings are otherwise only shown for a selected or about-to-be-placed
     * agent, because a board full of overlapping circles is unreadable. This
     * one is the exception it earns: the field *is* the unit, and an area
     * effect you cannot see the edge of is guesswork. Kept at a tenth alpha so
     * several of them still read as texture rather than as clutter.
     */
    private fun drawTarpitFields(canvas: android.graphics.Canvas, engine: GameEngine) {
        for (agent in engine.agents.items) {
            if (!agent.active || agent.type != AgentType.TARPIT) continue
            strokePaint.color = colBlue
            strokePaint.alpha = 26
            strokePaint.strokeWidth = 1.5f
            canvas.drawCircle(agent.x, agent.y, agent.range(), strokePaint)
        }
        strokePaint.alpha = 255
    }

    private fun drawAgents(
        canvas: android.graphics.Canvas,
        engine: GameEngine,
        selection: BattlefieldSelection,
        time: Float
    ) {
        for (agent in engine.agents.items) {
            if (!agent.active) continue
            val selected = selection.selectedNodeId == agent.nodeId
            val color = agentColor(agent.type)

            // Selection pulse.
            if (selected) {
                val pulse = 0.5f + 0.5f * sin(time * 5.2f)
                strokePaint.color = colCyan
                strokePaint.alpha = (110 + 140 * pulse).toInt().coerceIn(0, 255)
                strokePaint.strokeWidth = 2.5f
                canvas.drawCircle(agent.x, agent.y, WorldGeometry.NODE_RADIUS + 8f, strokePaint)
            }

            // Platform.
            fillPaint.color = color
            fillPaint.alpha = if (agent.disruptedFor > 0f) 18 else 34
            canvas.drawCircle(agent.x, agent.y, WorldGeometry.NODE_RADIUS, fillPaint)

            strokePaint.color = if (agent.disruptedFor > 0f) colRed else color
            strokePaint.alpha = 190
            strokePaint.strokeWidth = 1.8f
            canvas.drawCircle(agent.x, agent.y, WorldGeometry.NODE_RADIUS, strokePaint)

            // Muzzle flash ring.
            if (agent.fireFlash > 0f) {
                glowPaint.color = color
                glowPaint.alpha = (200 * (agent.fireFlash / 0.13f)).toInt().coerceIn(0, 255)
                glowPaint.strokeWidth = 4f
                canvas.drawCircle(agent.x, agent.y, WorldGeometry.NODE_RADIUS + 5f, glowPaint)
            }

            // Level-up burst.
            if (agent.upgradeFlash > 0f) {
                val t = 1f - (agent.upgradeFlash / 0.6f).coerceIn(0f, 1f)
                glowPaint.color = colCrypto
                glowPaint.alpha = (200 * (1f - t)).toInt().coerceIn(0, 255)
                glowPaint.strokeWidth = 3f
                canvas.drawCircle(agent.x, agent.y, WorldGeometry.NODE_RADIUS + 6f + t * 26f, glowPaint)
            }

            // Glyph: [F] / [F+] / [F++] / [F#] / [F##]
            textPaint.textSize = 24f
            textPaint.color = if (agent.disruptedFor > 0f) colRed else color
            textPaint.alpha = 255
            canvas.drawText(agent.type.renderedGlyph(agent.level), agent.x, agent.y + 8f, textPaint)

            // Level under the agent, in plain white at full strength.
            //
            // It used to be 12pt of dim secondary text, which on a phone at
            // arm's length was a smudge -- and the level is the one number a
            // player checks constantly while deciding what to upgrade next.
            // White rather than the agent's colour so it reads the same on
            // every class and against every backdrop.
            thinTextPaint.textSize = AGENT_LEVEL_TEXT
            thinTextPaint.color = colWhite
            thinTextPaint.alpha = 255
            canvas.drawText(
                "L${agent.level}",
                agent.x,
                agent.y + WorldGeometry.NODE_RADIUS + 19f,
                thinTextPaint
            )

            if (agent.damageBuff > 1f) {
                thinTextPaint.color = colPurple
                canvas.drawText("^", agent.x + 22f, agent.y - 20f, thinTextPaint)
            }
            if (agent.disruptedFor > 0f) {
                thinTextPaint.color = colRed
                canvas.drawText("JAM", agent.x, agent.y - WorldGeometry.NODE_RADIUS - 10f, thinTextPaint)
            }
            thinTextPaint.alpha = 255
        }
    }

    // --------------------------------------------------------------- enemies

    /**
     * Threats on the field.
     *
     * Each one is drawn as a **chip**: an opaque plate with a coloured border
     * and the ASCII tag inside it. That plate is the whole point. A label like
     * `[SQL2]` is sixty units wide, and when two threats close up on each other
     * — which happens constantly, because a fast type catches a slow one — bare
     * text drawn over bare text turns into unreadable mush like `[BBBIB]`.
     * With an opaque chip the nearer one simply covers the one behind it, which
     * reads as depth rather than as damage.
     *
     * For that to look deliberate rather than arbitrary, the chips are drawn
     * back to front by progress along the route, so the threat closest to the
     * core is always the one on top.
     */
    private fun drawEnemies(canvas: android.graphics.Canvas, engine: GameEngine, time: Float) {
        val items = engine.enemies.items
        if (enemyOrder.size < items.size) enemyOrder = IntArray(items.size)

        var count = 0
        for (i in items.indices) if (items[i].active) enemyOrder[count++] = i

        // Insertion sort: the list is small, nearly sorted frame to frame, and
        // this allocates nothing, which a Comparator-based sort would not.
        for (i in 1 until count) {
            val value = enemyOrder[i]
            val progress = items[value].progress
            var j = i - 1
            while (j >= 0 && items[enemyOrder[j]].progress > progress) {
                enemyOrder[j + 1] = enemyOrder[j]
                j--
            }
            enemyOrder[j + 1] = value
        }

        for (k in 0 until count) {
            val enemy = items[enemyOrder[k]]
            if (enemy.isBoss) drawBoss(canvas, enemy, time) else drawThreatChip(canvas, enemy)
        }
        textPaint.alpha = 255
        thinTextPaint.alpha = 255
    }

    private fun drawThreatChip(canvas: android.graphics.Canvas, enemy: Enemy) {
        val glyphSize = 26f * enemy.type.glyphScale
        val halfWidth = glyphWidth(enemy, glyphSize) * 0.5f + 9f
        val halfHeight = glyphSize * 0.62f + 3f

        // Threats walk in from off the left edge. The fade is measured from
        // the chip's own leading edge rather than its centre, so a chip is
        // still invisible while any part of it would be clipped by the edge of
        // the field, and reaches full opacity exactly as it clears.
        val entering = ((enemy.x - halfWidth) / CHIP_FADE_IN).coerceIn(0f, 1f)
        if (entering <= 0.02f) return

        val flashing = enemy.hitFlash > 0f
        val baseColor = enemyColor(enemy)
        val borderColor = if (enemy.slowRemaining > 0f) colBlue else baseColor

        scratchRect.set(
            enemy.x - halfWidth, enemy.y - halfHeight,
            enemy.x + halfWidth, enemy.y + halfHeight
        )

        // Fully opaque, and tinted a little towards the threat's own colour so
        // the plate reads as a unit rather than as a hole cut in the lane.
        // Anything less than opaque lets the chip behind bleed through, which
        // is the whole smearing problem back again, only fainter.
        fillPaint.color = blend(colBackground, baseColor, 0.13f)
        fillPaint.alpha = (255 * entering).toInt()
        canvas.drawRoundRect(scratchRect, CHIP_RADIUS, CHIP_RADIUS, fillPaint)

        strokePaint.color = borderColor
        strokePaint.alpha = ((if (flashing) 255 else 195) * entering).toInt()
        strokePaint.strokeWidth = 1.6f
        canvas.drawRoundRect(scratchRect, CHIP_RADIUS, CHIP_RADIUS, strokePaint)

        // Elites get a second outline rather than the old circle, which sat at
        // a fixed radius and so cut through the wider tags.
        if (enemy.isElite) {
            scratchRect.inset(-3.5f, -3.5f)
            strokePaint.color = colMagenta
            strokePaint.alpha = (185 * entering).toInt()
            strokePaint.strokeWidth = 1.4f
            canvas.drawRoundRect(scratchRect, CHIP_RADIUS + 2f, CHIP_RADIUS + 2f, strokePaint)
            scratchRect.inset(3.5f, 3.5f)
        }

        textPaint.textSize = glyphSize
        textPaint.color = if (flashing) colText else baseColor
        textPaint.alpha = (255 * entering).toInt()
        canvas.drawText(enemy.renderedGlyph(), enemy.x, enemy.y + glyphSize * 0.34f, textPaint)

        if (enemy.encrypted) {
            // Tucked against the chip's top edge. Any higher and it collides
            // with whatever chip is drawn behind and above this one.
            thinTextPaint.textSize = 12f
            thinTextPaint.color = colPurple
            thinTextPaint.alpha = (230 * entering).toInt()
            canvas.drawText("{#}", enemy.x, enemy.y - halfHeight - 2f, thinTextPaint)
        }

        drawHealthTrack(
            canvas,
            centerX = enemy.x,
            top = enemy.y + halfHeight + 3f,
            halfWidth = halfWidth,
            height = 4f,
            fraction = (enemy.health / enemy.maxHealth).coerceIn(0f, 1f),
            alpha = entering,
            always = false
        )
    }

    private fun drawBoss(canvas: android.graphics.Canvas, enemy: Enemy, time: Float) {
        val halfWidth = 74f
        val halfHeight = 46f
        scratchRect.set(
            enemy.x - halfWidth, enemy.y - halfHeight,
            enemy.x + halfWidth, enemy.y + halfHeight
        )

        fillPaint.color = Palette.RedDeep.toArgb()
        fillPaint.alpha = 150
        canvas.drawRoundRect(scratchRect, 6f, 6f, fillPaint)

        strokePaint.color = colRed
        strokePaint.alpha = 235
        strokePaint.strokeWidth = 3f
        canvas.drawRoundRect(scratchRect, 6f, 6f, strokePaint)

        val pulse = 0.5f + 0.5f * sin(time * 5.5f + enemy.phase)
        scratchRect.inset(-6f, -6f)
        glowPaint.color = colRed
        glowPaint.alpha = (55 + 105 * pulse).toInt().coerceIn(0, 255)
        glowPaint.strokeWidth = 5f
        canvas.drawRoundRect(scratchRect, 9f, 9f, glowPaint)
        scratchRect.inset(6f, 6f)

        val glyphSize = 26f * enemy.type.glyphScale
        textPaint.textSize = glyphSize
        textPaint.color = if (enemy.hitFlash > 0f) colText else enemyColor(enemy)
        textPaint.alpha = 255
        canvas.drawText(enemy.renderedGlyph(), enemy.x, enemy.y + glyphSize * 0.34f, textPaint)

        if (enemy.encrypted) {
            thinTextPaint.textSize = 14f
            thinTextPaint.color = colPurple
            thinTextPaint.alpha = 230
            canvas.drawText("{#}", enemy.x, enemy.y - halfHeight - 16f, thinTextPaint)
        }

        drawHealthTrack(
            canvas,
            centerX = enemy.x,
            top = enemy.y - halfHeight - 13f,
            halfWidth = halfWidth,
            height = 7f,
            fraction = (enemy.health / enemy.maxHealth).coerceIn(0f, 1f),
            alpha = 1f,
            always = true
        )
    }

    /**
     * A health bar that is actually a bar.
     *
     * The previous version drew its empty track in the sunken surface colour,
     * which is a shade off the backdrop and therefore invisible. All you saw
     * was the coloured fill — a short orange stub floating to the left of its
     * threat, reading as a rendering fault rather than as a health bar. The
     * track is now drawn in the divider colour, so the bar has a visible
     * length to be a fraction of.
     */
    private fun drawHealthTrack(
        canvas: android.graphics.Canvas,
        centerX: Float,
        top: Float,
        halfWidth: Float,
        height: Float,
        fraction: Float,
        alpha: Float,
        always: Boolean
    ) {
        if (!always && fraction >= 0.999f) return

        fillPaint.color = colDivider
        fillPaint.alpha = (230 * alpha).toInt()
        canvas.drawRect(centerX - halfWidth, top, centerX + halfWidth, top + height, fillPaint)

        fillPaint.color = when {
            fraction > 0.55f -> colGreen
            fraction > 0.25f -> colOrange
            else -> colRed
        }
        fillPaint.alpha = (255 * alpha).toInt()
        canvas.drawRect(
            centerX - halfWidth,
            top,
            centerX - halfWidth + halfWidth * 2f * fraction,
            top + height,
            fillPaint
        )
    }

    /** Measured once per threat type; the glyph and its scale never change. */
    /**
     * How wide this threat's chip has to be.
     *
     * Measured from what will actually be drawn, not from the type's glyph:
     * a boss draws its variant's mark now, and `[GG]` is a character narrower
     * than `[!!!]`. A chip sized for the wrong string is a chip that does not
     * fit its label, and the chip exists precisely so labels stop smearing
     * into each other.
     *
     * Ordinary threats keep the per-type cache, because there are dozens of
     * them on screen and their glyph never varies. A boss is measured live,
     * which costs nothing: there are at most a handful, once every five waves.
     */
    private fun glyphWidth(enemy: Enemy, size: Float): Float {
        if (enemy.isBoss) {
            textPaint.textSize = size
            return textPaint.measureText(enemy.renderedGlyph())
        }
        val cached = glyphWidths[enemy.type.ordinal]
        if (cached >= 0f) return cached
        textPaint.textSize = size
        val measured = textPaint.measureText(enemy.type.glyph)
        glyphWidths[enemy.type.ordinal] = measured
        return measured
    }

    // ------------------------------------------------------------ projectiles

    private fun drawProjectiles(canvas: android.graphics.Canvas, engine: GameEngine) {
        for (projectile in engine.projectiles.items) {
            if (!projectile.active) continue

            val color = agentColor(projectile.sourceType)
            val save = canvas.save()
            canvas.translate(projectile.x, projectile.y)
            canvas.rotate(Math.toDegrees(projectile.angle.toDouble()).toFloat())

            textPaint.textSize = if (projectile.heavy) 22f else 18f
            textPaint.color = if (projectile.heavy) colOrange else color
            textPaint.alpha = 255
            canvas.drawText(projectile.style.trail, 0f, 6f, textPaint)

            canvas.restoreToCount(save)
        }
    }

    // ---------------------------------------------------------------- effects

    /**
     * The pixel blast a boss or elite leaves behind.
     *
     * Every shard is *derived* rather than stored: angle, speed and size come
     * out of the effect's seed, so a three-hundred-piece explosion is one
     * pooled object and allocates nothing. That is the same trick the backdrop
     * columns and the rack's chase lights use, and it is what lets the engine
     * keep its promise of no per-frame allocation while still throwing this
     * much across the screen.
     *
     * The shards ease outward — fast at first, then slowing, as if through
     * drag — and shrink and fade as they go. `1 - (1 - p)^2` is the whole of
     * the physics, which is as much as a half-second effect can show.
     */
    private fun drawShardBursts(canvas: android.graphics.Canvas, engine: GameEngine) {
        for (effect in engine.effects.items) {
            if (!effect.active || effect.kind != EffectKind.SHARD_BURST) continue

            val progress = effect.progress
            // Held bright, then dropped late. A squared fade -- the obvious
            // first choice -- put the blast at a third of its alpha by the
            // time the shards had spread over a 700-unit disc, which rendered
            // as a faint speckle rather than as an explosion. Brightness has
            // to outlast the spread.
            val remaining = 1f - progress
            val fade = if (progress < 0.55f) 1f else remaining / 0.45f
            val reach = 1f - remaining * remaining
            val shards = if (engine.batterySaver) SHARDS / 3 else SHARDS

            fillPaint.color = effect.colorArgb
            var state = effect.seed
            for (i in 0 until shards) {
                // xorshift: a different angle and speed per shard, the same
                // ones every frame of the same explosion.
                state = state xor (state shl 13)
                state = state xor (state ushr 17)
                state = state xor (state shl 5)
                val a = (state ushr 8 and 0xFFFF) / 65535f
                val b = (state ushr 20 and 0x7FF) / 2047f

                val angle = a * TWO_PI_F
                // Squared so shards bunch nearer the middle and thin out at the
                // edge, which reads as a blast rather than as a ring.
                val distance = effect.scale * (0.15f + 0.85f * b * b) * reach
                val px = effect.x + cos(angle) * distance
                val py = effect.y + sin(angle) * distance

                fillPaint.alpha = (255 * fade).toInt().coerceIn(0, 255)
                // Chunky on purpose: a one-pixel shard on a phone is not an
                // explosion, it is dust.
                val size = (2.4f + 5.0f * b) * (0.55f + 0.45f * fade)
                canvas.drawRect(px, py, px + size, py + size, fillPaint)
            }

            // The shockwave: a ring that expands and thins, over the first
            // third. It is what makes the moment read as a detonation rather
            // than as confetti, and it is drawn white-hot at the centre
            // because every explosion worth looking at has a core.
            if (progress < 0.35f) {
                val flash = 1f - progress / 0.35f
                glowPaint.color = effect.colorArgb
                glowPaint.alpha = (235 * flash).toInt().coerceIn(0, 255)
                glowPaint.strokeWidth = 4f + 34f * (1f - flash)
                canvas.drawCircle(
                    effect.x,
                    effect.y,
                    26f + effect.scale * 0.42f * (1f - flash),
                    glowPaint
                )

                fillPaint.color = 0xFFFFFFFF.toInt()
                fillPaint.alpha = (215 * flash * flash).toInt().coerceIn(0, 255)
                canvas.drawCircle(effect.x, effect.y, 10f + 46f * flash, fillPaint)
                fillPaint.color = effect.colorArgb
            }
            fillPaint.alpha = 255
        }
    }


    private fun drawEffects(canvas: android.graphics.Canvas, engine: GameEngine) {
        for (effect in engine.effects.items) {
            if (!effect.active) continue
            val progress = effect.progress
            val fade = (1f - progress).coerceIn(0f, 1f)

            when (effect.kind) {
                // Drawn in its own pass, under the threats. See drawShardBursts.
                EffectKind.SHARD_BURST -> Unit

                EffectKind.HIT -> {
                    textPaint.textSize = 18f * effect.scale
                    textPaint.color = effect.colorArgb
                    textPaint.alpha = (255 * fade).toInt().coerceIn(0, 255)
                    canvas.drawText(effect.text, effect.x, effect.y, textPaint)
                }

                EffectKind.DEATH -> {
                    // [*] -> + -> . -> gone
                    val glyph = when {
                        progress < 0.33f -> "[*]"
                        progress < 0.66f -> "+"
                        else -> "."
                    }
                    textPaint.textSize = 24f
                    textPaint.color = effect.colorArgb
                    textPaint.alpha = (255 * fade).toInt().coerceIn(0, 255)
                    canvas.drawText(glyph, effect.x, effect.y, textPaint)
                }

                EffectKind.BOSS_DEATH -> drawBossExplosion(canvas, effect.x, effect.y, progress, fade)

                EffectKind.DAMAGE_NUMBER -> {
                    thinTextPaint.textSize = 17f * effect.scale
                    thinTextPaint.color = effect.colorArgb
                    thinTextPaint.alpha = (235 * fade).toInt().coerceIn(0, 255)
                    canvas.drawText(effect.text, effect.x, effect.y, thinTextPaint)
                }

                EffectKind.CRYPTO_GAIN -> {
                    textPaint.textSize = 18f
                    textPaint.color = effect.colorArgb
                    textPaint.alpha = (255 * fade).toInt().coerceIn(0, 255)
                    canvas.drawText(effect.text, effect.x, effect.y, textPaint)
                }

                EffectKind.UPGRADE, EffectKind.TEXT -> {
                    textPaint.textSize = 19f * effect.scale
                    textPaint.color = effect.colorArgb
                    textPaint.alpha = (255 * fade).toInt().coerceIn(0, 255)
                    canvas.drawText(effect.text, effect.x, effect.y, textPaint)
                }
            }
        }
        textPaint.alpha = 255
        thinTextPaint.alpha = 255
    }

    /**
     * The boss death sequence: a terminal-style starburst that expands outward.
     *
     *          *
     *        *###*
     *      **#####**
     *        *###*
     *          *
     */
    private fun drawBossExplosion(
        canvas: android.graphics.Canvas,
        x: Float,
        y: Float,
        progress: Float,
        fade: Float
    ) {
        val expand = 1f + progress * 1.7f
        textPaint.textSize = 22f
        textPaint.alpha = (255 * fade).toInt().coerceIn(0, 255)

        for (row in BOSS_EXPLOSION.indices) {
            val line = BOSS_EXPLOSION[row]
            val dy = (row - BOSS_EXPLOSION.size / 2) * 24f * expand
            textPaint.color = if (row % 2 == 0) colOrange else colRed
            canvas.drawText(line, x, y + dy, textPaint)
        }
        textPaint.alpha = 255
    }

    // --------------------------------------------------------- boss warning

    private fun drawBossBanner(canvas: android.graphics.Canvas, engine: GameEngine, time: Float) {
        if (engine.phase != RunPhase.BOSS_WARNING) return

        val flash = 0.5f + 0.5f * sin(time * 9f)

        fillPaint.color = Palette.RedDeep.toArgb()
        fillPaint.alpha = (70 + 60 * flash).toInt().coerceIn(0, 255)
        canvas.drawRect(0f, 0f, WorldGeometry.WIDTH, WorldGeometry.HEIGHT, fillPaint)

        strokePaint.color = colRed
        strokePaint.strokeWidth = 8f
        strokePaint.alpha = (150 + 105 * flash).toInt().coerceIn(0, 255)
        canvas.drawRect(6f, 6f, WorldGeometry.WIDTH - 6f, WorldGeometry.HEIGHT - 6f, strokePaint)

        val cx = WorldGeometry.WIDTH * 0.5f
        textPaint.textSize = 46f
        textPaint.color = colRed
        textPaint.alpha = 255
        canvas.drawText("!!! CYBERATTACK INCOMING !!!", cx, WorldGeometry.HEIGHT * 0.40f, textPaint)

        // Named, now that bosses have names. A player who has learned what
        // [GG] does should be told which one is coming while there is still
        // time to build for it.
        val variant = engine.activeBossVariant
        textPaint.textSize = 30f
        textPaint.color = colOrange
        canvas.drawText(
            "${variant.glyph}  ${variant.displayName}",
            cx,
            WorldGeometry.HEIGHT * 0.50f,
            textPaint
        )

        thinTextPaint.textSize = 19f
        thinTextPaint.color = colSecondary
        thinTextPaint.alpha = 235
        canvas.drawText(variant.signature, cx, WorldGeometry.HEIGHT * 0.555f, thinTextPaint)
        thinTextPaint.alpha = 255

        val modifiers = engine.activeBossModifiers
        if (modifiers.isNotEmpty()) {
            thinTextPaint.textSize = 20f
            thinTextPaint.color = colCrypto
            thinTextPaint.alpha = 255
            canvas.drawText(
                modifiers.joinToString("  /  ") { it.displayName },
                cx, WorldGeometry.HEIGHT * 0.615f, thinTextPaint
            )
        }

        thinTextPaint.textSize = 18f
        thinTextPaint.color = colSecondary
        canvas.drawText(
            "WAVE ${engine.currentWave}  —  BRACE THE PERIMETER",
            cx, WorldGeometry.HEIGHT * 0.66f, thinTextPaint
        )
    }

    // ---------------------------------------------------------------- colors

    /**
     * An agent's colour.
     *
     * With the SPECTRUM skin on, every agent drifts slowly around the colour
     * wheel — but each *class* keeps its own offset, so a FIREWALL and an IDS
     * are still different colours at any instant. That matters more than the
     * effect does: colour is how the board is read at a glance, and a skin
     * that made every agent the same colour at once would be a skin that costs
     * the player information they paid nothing to lose.
     */
    private fun agentColor(type: AgentType): Int {
        if (!spectrumAgents) return classColor(type)
        val offset = type.ordinal / AgentType.entries.size.toFloat()
        return spectrum(frameTime * SPECTRUM_SPEED + offset)
    }

    /**
     * A point on the colour wheel, as a saturated, bright colour.
     *
     * Hand-rolled rather than via HSV so it allocates nothing: this is called
     * once per agent per frame.
     */
    private fun spectrum(position: Float): Int {
        val h = ((position % 1f) + 1f) % 1f * 6f
        val sector = h.toInt()
        val f = h - sector
        val top = 255
        val bottom = 70
        val rise = (bottom + (top - bottom) * f).toInt()
        val fall = (top - (top - bottom) * f).toInt()
        val (r, g, b) = when (sector) {
            0 -> Triple(top, rise, bottom)
            1 -> Triple(fall, top, bottom)
            2 -> Triple(bottom, top, rise)
            3 -> Triple(bottom, fall, top)
            4 -> Triple(rise, bottom, top)
            else -> Triple(top, bottom, fall)
        }
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun classColor(type: AgentType): Int = when (type) {
        AgentType.TARPIT -> colBlue
        AgentType.FIREWALL -> colGreen
        AgentType.IDS -> colCyan
        AgentType.IPS -> colBlue
        AgentType.ANALYST -> colCrypto
        AgentType.CRYPTOGRAPHER -> colPurple
        AgentType.ZERO_DAY_HUNTER -> colOrange
        AgentType.AI_SENTINEL -> colCyan
        AgentType.QUANTUM_DEFENDER -> colPurple
        AgentType.ROOT_ADMIN -> colGreen
        AgentType.NETWORK_ARCHITECT -> colPurple
    }

    private fun enemyColor(enemy: Enemy): Int = when {
        enemy.isBoss -> colRed
        enemy.isElite -> colMagenta
        enemy.encrypted -> colPurple
        else -> when (enemy.type) {
            EnemyType.BOT -> colOrange
            EnemyType.DDOS -> colMagenta
            EnemyType.EXPLOIT -> colOrange
            else -> colRed
        }
    }

    internal companion object {
        /** Lights around the circuit that frames the integrity block. */
        const val LOOP_LEDS = 46

        /** NEONGRID's ring: lights around it, its radius, and its skim rate. */
        const val RING_LEDS = 26
        const val RING_RADIUS = 30f
        const val HOLO_SPEED = 0.11f

        /** How quickly a racetrack comet fades behind itself. */
        const val RACETRACK_TAIL = 7f

        const val LED_ROWS = 5
        const val LED_COLUMNS = 8
        const val BACKDROP_GLYPHS = 42

        /** Pixels thrown by one boss or elite death blast. */
        const val SHARDS = 420

        /** How long a backdrop colour change takes to cross-fade. */
        const val TINT_FADE_SECONDS = 2.5f

        // The in-field wave and crypto readouts. The baseline is set so the
        // text clears both the top of the field and the ATTACK ORIGIN label
        // below it, and so the whole thing stays above the top lane at y=73.
        const val FIELD_STATUS_TEXT = 35f
        const val FIELD_STATUS_BASELINE = 40f
        /** Baseline-to-baseline gap between the wave and the crypto line. */
        const val FIELD_STATUS_LINE = 44f
        const val FIELD_STATUS_MARGIN = 16f
        /** How dark the plate under a corner readout is. */
        const val FIELD_STATUS_PLATE_ALPHA = 185

        /** Plates are sized for these, so they never resize during a run. */
        const val WAVE_PLATE_TEMPLATE = "WAVE 000"
        const val CRYPTO_PLATE_TEMPLATE = "\u25C7 000000"

        /** The level under an agent. Read constantly; sized to be readable. */
        const val AGENT_LEVEL_TEXT = 17f

        /** The core's integrity figures, inside the chase circuit. */
        const val INTEGRITY_TEXT = 27f



        /** The run name: smaller and dimmer than the corner readouts. */
        const val RUN_NAME_TEXT = 15f
        const val RUN_NAME_ALPHA = 130

        /** Turns of the colour wheel per second for the SPECTRUM agent skin. */
        const val SPECTRUM_SPEED = 0.045f

        /** How far a living background pulls the corridor towards its palette. */
        const val LANE_TINT_STRENGTH = 0.72f

        /** Corner rounding on a threat chip. */
        const val CHIP_RADIUS = 5f

        const val TWO_PI_F = 6.2831855f

        /** Glyphs the CASCADE core skin rains inside the rack. */
        val CASCADE_GLYPHS = charArrayOf(
            '0', '1', '<', '>', '/', '\\', '#', '$', '%', '&', '=', '+', '*'
        )

        /** World units a threat fades in over as it enters the field. */
        const val CHIP_FADE_IN = 70f

        val BOSS_EXPLOSION = arrayOf(
            "*",
            "*###*",
            "**#####**",
            "*###*",
            "*"
        )
    }
}
