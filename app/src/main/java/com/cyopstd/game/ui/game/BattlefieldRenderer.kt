package com.cyopstd.game.ui.game

import android.graphics.Paint
import android.graphics.Typeface
import com.cyopstd.game.core.WorldGeometry
import com.cyopstd.game.engine.GameEngine
import com.cyopstd.game.engine.RunPhase
import com.cyopstd.game.model.AgentType
import com.cyopstd.game.model.EffectKind
import com.cyopstd.game.model.Enemy
import com.cyopstd.game.model.EnemyType
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
    private val colMuted = Palette.TextMuted.toArgb()
    private val colSecondary = Palette.TextSecondary.toArgb()

    /** Scratch buffer used for drawing single characters without allocating. */
    private val charBuffer = CharArray(1)

    // The backdrop colour band, and the only state the renderer keeps beyond
    // its Paints. Every five waves the band changes; rather than snapping, the
    // old and new colours are cross-faded over a couple of seconds so the shift
    // registers as the room's light changing rather than as a flicker.
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
        drawRangeIndicator(canvas, engine, selection, options)
        drawAgents(canvas, engine, selection, time)
        drawEnemies(canvas, engine, time)
        drawProjectiles(canvas, engine)
        drawEffects(canvas, engine)
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

        if (!options.backgroundAnimation || options.batterySaver) return

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
        for (lane in 0 until WorldGeometry.LANE_COUNT) {
            val points = WorldGeometry.laneWaypoints[lane]

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

            // Corridor interior.
            strokePaint.color = Palette.laneTints[lane % Palette.laneTints.size].toArgb()
            strokePaint.alpha = 255
            strokePaint.strokeWidth = WorldGeometry.LANE_HEIGHT - 4f
            canvas.drawPath(lanePath, strokePaint)

            strokePaint.strokeCap = android.graphics.Paint.Cap.BUTT
            strokePaint.strokeJoin = android.graphics.Paint.Join.MITER

            drawLaneFlow(canvas, lane, options, time)
            drawLaneLabel(canvas, lane)
        }

        // Entry marker.
        val entry = WorldGeometry.entryPoint(0)
        leftTextPaint.textSize = 18f
        leftTextPaint.color = colRed
        leftTextPaint.alpha = 210
        canvas.drawText("ATTACK ORIGIN", 12f, entry.y - 44f, leftTextPaint)
        leftTextPaint.alpha = 255
    }

    /** Flowing chevrons along a route, oriented to whichever bend they are on. */
    private fun drawLaneFlow(
        canvas: android.graphics.Canvas,
        lane: Int,
        options: BattlefieldRenderOptions,
        time: Float
    ) {
        val length = WorldGeometry.laneLength[lane]
        val spacing = 96f
        val speed = if (options.backgroundAnimation && !options.batterySaver) 46f else 0f
        val shift = (time * speed) % spacing
        val baseAlpha = if (options.batterySaver) 70 else 125

        thinTextPaint.textSize = 16f
        thinTextPaint.color = colCyanDim

        var distance = shift
        while (distance < length - 30f) {
            WorldGeometry.positionAt(lane, distance, pathScratch)
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
    private fun drawLaneLabel(canvas: android.graphics.Canvas, lane: Int) {
        val entry = WorldGeometry.entryPoint(lane)
        leftTextPaint.textSize = 15f
        leftTextPaint.color = colCyanDim
        leftTextPaint.alpha = 200
        canvas.drawText(
            "ROUTE ${'A' + lane}",
            18f,
            entry.y - WorldGeometry.LANE_HEIGHT * 0.5f - 8f,
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
    private fun drawServer(canvas: android.graphics.Canvas, engine: GameEngine, time: Float) {
        val left = WorldGeometry.SERVER_X
        val top = WorldGeometry.SERVER_TOP
        val right = left + WorldGeometry.SERVER_WIDTH
        val bottom = top + WorldGeometry.SERVER_HEIGHT

        val hpFraction = if (engine.serverMaxHp <= 0) 0f
        else engine.serverHp.toFloat() / engine.serverMaxHp
        val damaged = engine.serverHitFlash > 0f
        val accent = when {
            damaged -> colRed
            hpFraction <= 0.3f -> colOrange
            else -> colCyan
        }

        // Chassis.
        fillPaint.color = Palette.Surface.toArgb()
        fillPaint.alpha = 235
        canvas.drawRect(left, top, right, bottom, fillPaint)

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
        leftTextPaint.color = colSecondary
        canvas.drawText("| DATA CORE      |", left + 14f, top + 110f, leftTextPaint)

        // Activity LEDs: rows of "." that blink on independent intervals. They
        // get busier the more packets are in flight, which turns the server into
        // an at-a-glance load indicator.
        val load = (engine.activeEnemyCount() / 14f).coerceIn(0f, 1.6f)
        val blinkRate = 1.4f + load * 3.4f
        thinTextPaint.textSize = 20f
        for (row in 0 until LED_ROWS) {
            val ly = top + 140f + row * 26f
            for (col in 0 until LED_COLUMNS) {
                val lx = left + 30f + col * 25f
                val seed = row * 7.31f + col * 3.77f
                val pulse = sin(time * blinkRate + seed)
                val lit = pulse > (0.55f - load * 0.35f)
                thinTextPaint.color = when {
                    damaged && lit -> colRed
                    lit -> colGreen
                    else -> colMuted
                }
                thinTextPaint.alpha = if (lit) 235 else 70
                charBuffer[0] = if (lit) '•' else '.'
                canvas.drawText(charBuffer, 0, 1, lx, ly, thinTextPaint)
            }
        }
        thinTextPaint.alpha = 255

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

        textPaint.textSize = 20f
        textPaint.color = colText
        textPaint.alpha = 255
        canvas.drawText(
            "${engine.serverHp} / ${engine.serverMaxHp}",
            (left + right) * 0.5f, bottom - 22f, textPaint
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

    // ----------------------------------------------------------------- nodes

    private fun drawDeploymentNodes(
        canvas: android.graphics.Canvas,
        engine: GameEngine,
        selection: BattlefieldSelection,
        time: Float
    ) {
        val placing = selection.pendingAgent != null
        val affordable = selection.pendingAgent?.let { engine.crypto >= it.cost } ?: false

        for (node in WorldGeometry.nodes) {
            val occupied = engine.agentAt(node.id) != null
            if (occupied) continue

            val highlighted = placing && !occupied
            val pulse = 0.6f + 0.4f * sin(time * 4.4f + node.id * 0.6f)

            strokePaint.strokeWidth = if (highlighted) 2.4f else 1.4f
            strokePaint.color = when {
                highlighted && affordable -> colGreen
                highlighted -> colOrange
                else -> colCyanDim
            }
            strokePaint.alpha = if (highlighted) {
                (140 + 115 * pulse).toInt().coerceIn(0, 255)
            } else {
                55
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

            if (highlighted) {
                thinTextPaint.textSize = 18f
                thinTextPaint.color = strokePaint.color
                thinTextPaint.alpha = strokePaint.alpha
                canvas.drawText("+", node.x, node.y + 7f, thinTextPaint)
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
        for (node in WorldGeometry.nodes) {
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

            // Level pip row under the agent.
            thinTextPaint.textSize = 12f
            thinTextPaint.color = colSecondary
            thinTextPaint.alpha = 210
            canvas.drawText("L${agent.level}", agent.x, agent.y + WorldGeometry.NODE_RADIUS + 16f, thinTextPaint)

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

    private fun drawEnemies(canvas: android.graphics.Canvas, engine: GameEngine, time: Float) {
        for (enemy in engine.enemies.items) {
            if (!enemy.active) continue

            val baseColor = enemyColor(enemy)
            val flashing = enemy.hitFlash > 0f
            val glyphSize = 26f * enemy.type.glyphScale

            if (enemy.isBoss) {
                // Boss chassis: a heavier frame so it is unmistakable.
                val w = 74f
                val h = 46f
                fillPaint.color = Palette.RedDeep.toArgb()
                fillPaint.alpha = 120
                canvas.drawRect(enemy.x - w, enemy.y - h, enemy.x + w, enemy.y + h, fillPaint)
                strokePaint.color = colRed
                strokePaint.alpha = 230
                strokePaint.strokeWidth = 3f
                canvas.drawRect(enemy.x - w, enemy.y - h, enemy.x + w, enemy.y + h, strokePaint)

                val pulse = 0.5f + 0.5f * sin(time * 5.5f + enemy.phase)
                glowPaint.color = colRed
                glowPaint.alpha = (60 + 110 * pulse).toInt().coerceIn(0, 255)
                glowPaint.strokeWidth = 5f
                canvas.drawRect(enemy.x - w - 5f, enemy.y - h - 5f, enemy.x + w + 5f, enemy.y + h + 5f, glowPaint)
            } else if (enemy.isElite) {
                strokePaint.color = colMagenta
                strokePaint.alpha = 170
                strokePaint.strokeWidth = 1.6f
                canvas.drawCircle(enemy.x, enemy.y, 24f, strokePaint)
            }

            // Slowed packets get a containment bracket so the effect is visible.
            if (enemy.slowRemaining > 0f) {
                thinTextPaint.textSize = 16f
                thinTextPaint.color = colBlue
                thinTextPaint.alpha = 200
                canvas.drawText("[", enemy.x - 30f, enemy.y + 6f, thinTextPaint)
                canvas.drawText("]", enemy.x + 30f, enemy.y + 6f, thinTextPaint)
                thinTextPaint.alpha = 255
            }

            textPaint.textSize = glyphSize
            textPaint.color = if (flashing) colText else baseColor
            textPaint.alpha = 255
            canvas.drawText(enemy.type.glyph, enemy.x, enemy.y + glyphSize * 0.34f, textPaint)

            // Encrypted marker: a lock-ish ASCII tag above the glyph.
            if (enemy.encrypted) {
                thinTextPaint.textSize = 13f
                thinTextPaint.color = colPurple
                thinTextPaint.alpha = 220
                canvas.drawText("{#}", enemy.x, enemy.y - glyphSize * 0.75f, thinTextPaint)
                thinTextPaint.alpha = 255
            }

            drawEnemyHealthBar(canvas, enemy)
        }
    }

    private fun drawEnemyHealthBar(canvas: android.graphics.Canvas, enemy: Enemy) {
        if (enemy.health >= enemy.maxHealth && !enemy.isBoss) return
        val fraction = (enemy.health / enemy.maxHealth).coerceIn(0f, 1f)
        val halfWidth = if (enemy.isBoss) 72f else 24f
        val barY = enemy.y - (if (enemy.isBoss) 58f else 26f)
        val height = if (enemy.isBoss) 8f else 4f

        fillPaint.color = colSurfaceSunken
        fillPaint.alpha = 220
        canvas.drawRect(enemy.x - halfWidth, barY, enemy.x + halfWidth, barY + height, fillPaint)

        fillPaint.color = when {
            fraction > 0.55f -> colRed
            fraction > 0.25f -> colOrange
            else -> colCrypto
        }
        fillPaint.alpha = 255
        canvas.drawRect(
            enemy.x - halfWidth, barY,
            enemy.x - halfWidth + halfWidth * 2f * fraction, barY + height,
            fillPaint
        )
    }

    // ------------------------------------------------------------ projectiles

    private fun drawProjectiles(canvas: android.graphics.Canvas, engine: GameEngine) {
        for (projectile in engine.projectiles.items) {
            if (!projectile.active) continue

            val color = agentColor(projectile.sourceType)
            val save = canvas.save()
            canvas.translate(projectile.x, projectile.y)
            canvas.rotate(Math.toDegrees(projectile.angle.toDouble()).toFloat())

            textPaint.textSize = if (projectile.critical) 22f else 18f
            textPaint.color = if (projectile.critical) colOrange else color
            textPaint.alpha = 255
            canvas.drawText(projectile.style.trail, 0f, 6f, textPaint)

            canvas.restoreToCount(save)
        }
    }

    // ---------------------------------------------------------------- effects

    private fun drawEffects(canvas: android.graphics.Canvas, engine: GameEngine) {
        for (effect in engine.effects.items) {
            if (!effect.active) continue
            val progress = effect.progress
            val fade = (1f - progress).coerceIn(0f, 1f)

            when (effect.kind) {
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

        textPaint.textSize = 30f
        textPaint.color = colOrange
        canvas.drawText("MAJOR BREACH DETECTED", cx, WorldGeometry.HEIGHT * 0.50f, textPaint)

        val modifiers = engine.activeBossModifiers
        if (modifiers.isNotEmpty()) {
            thinTextPaint.textSize = 20f
            thinTextPaint.color = colCrypto
            thinTextPaint.alpha = 255
            canvas.drawText(
                modifiers.joinToString("  /  ") { it.displayName },
                cx, WorldGeometry.HEIGHT * 0.58f, thinTextPaint
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

    private fun agentColor(type: AgentType): Int = when (type) {
        AgentType.FIREWALL -> colGreen
        AgentType.IDS -> colCyan
        AgentType.IPS -> colBlue
        AgentType.ANALYST -> colCrypto
        AgentType.SANDBOX -> colBlue
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

    private companion object {
        const val LED_ROWS = 5
        const val LED_COLUMNS = 8
        const val BACKDROP_GLYPHS = 42

        /** How long a backdrop colour change takes to cross-fade. */
        const val TINT_FADE_SECONDS = 2.5f

        val BOSS_EXPLOSION = arrayOf(
            "*",
            "*###*",
            "**#####**",
            "*###*",
            "*"
        )
    }
}
