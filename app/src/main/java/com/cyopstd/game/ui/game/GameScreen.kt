package com.cyopstd.game.ui.game

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.cyopstd.game.core.Balance
import com.cyopstd.game.engine.RunPhase
import com.cyopstd.game.state.GameViewModel
import com.cyopstd.game.ui.common.CompactButton
import com.cyopstd.game.ui.theme.CoreSkin
import com.cyopstd.game.ui.theme.LivingBackground
import com.cyopstd.game.ui.theme.Palette

/**
 * The match screen.
 *
 * Layout is a simple vertical stack — HUD, battlefield, control bar — with every
 * panel and dialog floating over the battlefield rather than shrinking it. The
 * battlefield stays by far the largest area on screen at any phone aspect ratio.
 *
 * The simulation clock lives here: one `withFrameNanos` loop feeds real deltas to
 * the view model for as long as this screen is composed, and stops the moment it
 * is not. Battery saver halves the tick rate without touching game logic —
 * the engine is fed the same total elapsed time either way.
 */
@Composable
fun GameScreen(
    viewModel: GameViewModel,
    onExitToMenu: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val hud = viewModel.hud
    val settings = viewModel.settings
    val selection = viewModel.selection
    val renderer = remember { BattlefieldRenderer() }

    // Cosmetics are resolved against ownership before they reach here, so the
    // renderer can trust the choice without consulting entitlements itself.
    val cosmetics = viewModel.cosmetics
    renderer.coreSkin = CoreSkin.forProduct(cosmetics.coreSkinId)
    renderer.livingBackground = LivingBackground.forProduct(cosmetics.backgroundId)
    renderer.spectrumAgents = cosmetics.spectrumAgents

    val options = remember(settings) {
        BattlefieldRenderOptions(
            backgroundAnimation = settings.backgroundAnimation,
            showAgentRange = settings.showAgentRange,
            screenShake = settings.screenShake,
            batterySaver = settings.batterySaver
        )
    }

    // --- simulation clock -------------------------------------------------
    LaunchedEffect(settings.batterySaver) {
        var previous = withFrameNanos { it }
        var accumulator = 0f
        val minStep = if (settings.batterySaver) 1f / 30f else 0f

        while (true) {
            withFrameNanos { now ->
                val delta = ((now - previous) / 1_000_000_000.0).toFloat()
                previous = now
                accumulator += delta
                if (accumulator >= minStep) {
                    viewModel.onFrame(accumulator)
                    accumulator = 0f
                }
            }
        }
    }

    BackHandler(enabled = true) {
        if (viewModel.gameOverSummary != null) {
            onExitToMenu()
        } else if (!viewModel.paused) {
            viewModel.applyPaused(true)
        } else {
            viewModel.applyPaused(false)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Palette.Background)
    ) {
        GameHud(hud = hud)

        // The battlefield's pixel size, so the tutorial can convert a world
        // rect into somewhere on screen to point at. The Canvas inside
        // Battlefield fills this same Box, so one size serves both.
        var fieldSize by remember { mutableStateOf(IntSize.Zero) }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .onSizeChanged { fieldSize = it }
        ) {
            Battlefield(viewModel, renderer, options)

            // Preparation prompt. One line tall, pinned to the very top of the
            // battlefield, and it hides itself after a few seconds so it cannot
            // sit on top of the lane-1 deployment nodes while you are trying to
            // place an agent. Tapping it dismisses it immediately.
            if (hud.phase == RunPhase.PREPARING &&
                viewModel.prepBannerVisible &&
                viewModel.gameOverSummary == null &&
                viewModel.unlockBanner == null
            ) {
                PreparationBanner(
                    wave = hud.wave,
                    nextIsBoss = hud.nextWaveIsBoss,
                    autoStartIn = hud.autoStartRemaining,
                    onDismiss = viewModel::dismissPrepBanner,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 2.dp)
                )
            }

            viewModel.unlockBanner?.let { type ->
                UnlockBanner(
                    type = type,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 12.dp)
                )
            }

            viewModel.transientMessage?.let { message ->
                TransientMessage(
                    message = message,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(bottom = 60.dp)
                )
            }

            // Agent management panel docks to the right so it never covers the
            // packet entry side, where the player needs to watch for threats.
            val selectedAgent = selection.selectedNodeId?.let { viewModel.engine.agentAt(it) }
            if (selectedAgent != null) {
                AgentManagementPanel(
                    agent = selectedAgent,
                    crypto = hud.crypto,
                    affordableLevels = viewModel.affordableUpgradesForSelection(),
                    onUpgrade = { times -> viewModel.upgradeSelectedAgent(times) },
                    onSell = viewModel::sellSelectedAgent,
                    onCycleTargeting = viewModel::cycleTargetingMode,
                    onClose = viewModel::closeSelection,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(8.dp)
                )
            }

            if (viewModel.showBossPanel) {
                // Reading frameTick subscribes this to the simulation clock, so
                // the numbers move while the panel is open -- which is the
                // whole reason to open it mid-fight.
                @Suppress("UNUSED_EXPRESSION")
                viewModel.frameTick
                val dossier = viewModel.bossDossier()
                if (dossier != null) {
                    BossDossierPanel(
                        dossier = dossier,
                        onClose = viewModel::toggleBossPanel,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                    )
                }
            }

            if (viewModel.showDeployPanel) {
                DeployPanel(
                    crypto = hud.crypto,
                    unlockedAgents = viewModel.unlockedAgents,
                    selected = selection.pendingAgent,
                    onSelect = viewModel::choosePendingAgent,
                    onClose = viewModel::toggleDeployPanel,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(8.dp)
                )
            }

            if (viewModel.tutorialStep >= 0 && viewModel.gameOverSummary == null) {
                val script = TutorialScript.stepAt(viewModel.tutorialStep)
                val target = when (script?.target) {
                    TutorialTarget.WAVE_READOUT -> renderer.fieldStatusAnchors.wave
                    TutorialTarget.CRYPTO_READOUT -> renderer.fieldStatusAnchors.crypto
                    else -> null
                }

                // The arrow, drawn under the card so a long card is never
                // painted over by it, and only once the field has a size to
                // convert against.
                if (target != null && fieldSize.width > 0 && fieldSize.height > 0) {
                    TutorialPointer(
                        target = target,
                        // The same zoom and pan the board is drawn with. The
                        // arrow points at something painted inside the canvas,
                        // so a transform without the viewport in it would
                        // point confidently at empty space the moment anyone
                        // pinched.
                        transform = WorldTransform(
                            fieldSize.width.toFloat(),
                            fieldSize.height.toFloat(),
                            viewModel.viewport.zoom,
                            viewModel.viewport.panX,
                            viewModel.viewport.panY
                        )
                    )
                }

                TutorialOverlay(
                    step = viewModel.tutorialStep,
                    onAdvance = viewModel::advanceTutorial,
                    onBriefing = viewModel::answerBriefing,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                )

                // Skip lives in a corner rather than inside the card. A player
                // who wants out of a tutorial wants out at every step,
                // including the ones that wait for them to tap something
                // specific and therefore have no buttons of their own.
                //
                // Its home is the top right -- which is exactly where the WAVE
                // and CRYPTO readouts are. On the two steps that point at
                // them it moves to the bottom left, clear of the card (top
                // left), the readouts (top right) and the deploy panel (bottom
                // centre). A control that covers the thing it is explaining is
                // worse than no control.
                val skipIsInTheWay = target != null
                CompactButton(
                    text = "SKIP \u00D7",
                    onClick = viewModel::skipTutorial,
                    accent = Palette.TextSecondary,
                    modifier = Modifier
                        .align(
                            if (skipIsInTheWay) Alignment.BottomStart else Alignment.TopEnd
                        )
                        .padding(
                            top = if (skipIsInTheWay) 0.dp else 6.dp,
                            end = if (skipIsInTheWay) 0.dp else 8.dp,
                            start = if (skipIsInTheWay) 10.dp else 0.dp,
                            bottom = if (skipIsInTheWay) 10.dp else 0.dp
                        )
                )
            }
        }

        ControlBar(viewModel = viewModel)
    }

    if (viewModel.paused && viewModel.gameOverSummary == null) {
        PauseOverlay(
            wave = hud.wave,
            onResume = { viewModel.applyPaused(false) },
            onRestart = {
                viewModel.applyPaused(false)
                viewModel.startNewGame()
            },
            onSettings = onOpenSettings,
            onMainMenu = {
                viewModel.applyPaused(false)
                viewModel.leaveMatch()
                onExitToMenu()
            }
        )
    }

    viewModel.gameOverSummary?.let { summary ->
        GameOverOverlay(
            summary = summary,
            onRetry = { viewModel.restartAfterGameOver() },
            onMainMenu = {
                viewModel.abandonMatch()
                onExitToMenu()
            },
            // Null rather than a disabled button: a build with no rewarded ad
            // unit, or a run that has spent its revive, shows no offer at all.
            onWatchAdToRevive =
                if (viewModel.canReviveNow || viewModel.showingReviveAd) {
                    { viewModel.watchAdToRevive() }
                } else {
                    null
                },
            revivesLeft = viewModel.revivesAllowed - viewModel.revivesUsed,
            reviveAdShowing = viewModel.showingReviveAd,
            reviveIsFree = viewModel.reviveIsFree
        )
    }
}

/**
 * The battlefield itself: one Canvas, one draw pass, one tap handler.
 *
 * Reading `viewModel.frameTick` inside the draw lambda is what subscribes this
 * Canvas to the frame clock — it redraws when the tick changes and at no other
 * time.
 */
/** Test handle for the zoomable board. */
const val BATTLEFIELD_TAG = "battlefield"

@Composable
private fun Battlefield(
    viewModel: GameViewModel,
    renderer: BattlefieldRenderer,
    options: BattlefieldRenderOptions
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val viewport = viewModel.viewport
        val transform = remember(widthPx, heightPx, viewport.zoom, viewport.panX, viewport.panY) {
            WorldTransform(widthPx, heightPx, viewport.zoom, viewport.panX, viewport.panY)
        }
        val touchSlop = with(density) { 12.dp.toPx() }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                // Tagged so a test can aim a real multi-touch gesture at the
                // board rather than at the screen and hope. The rule that a
                // pinch must never place an agent is not one that can be
                // checked by reading the gesture code.
                .testTag(BATTLEFIELD_TAG)
                // Clipped to its own bounds, and it has to be.
                //
                // The renderer opens every frame with `canvas.drawColor`, which
                // fills the whole *clip* rather than the composable's box --
                // and Compose does not clip a draw to its layout bounds unless
                // it is asked to. So the battlefield was painting over the top
                // status strip, which is drawn before it in the Column, every
                // frame: the HUD composed, laid out, reported correct bounds,
                // and was then wiped before anyone saw it.
                //
                // That is the "is the top HUD strip actually visible?" question
                // that has been open in DEVELOPMENT_STATUS since 1.5.2, and the
                // answer was no.
                //
                // It is now load-bearing for a second reason: zooming in draws
                // the board larger than this box, and without the clip it would
                // paint over the HUD strip and the control bar again.
                .clipToBounds()
                // One gesture loop, not two.
                //
                // `detectTapGestures` and `detectTransformGestures` in separate
                // `pointerInput` modifiers both consume from the same stream and
                // race: the tap detector sees the first finger go down, the
                // transform detector sees the second, and whichever resolves
                // first wins. That race is exactly the bug the brief is worried
                // about -- a pinch that drops an agent where the first finger
                // landed. Handling both here means a gesture is classified once,
                // and a gesture that ever had two fingers in it can never be a
                // tap.
                .pointerInput(widthPx, heightPx) {
                    awaitEachGesture {
                        val first = awaitFirstDown(requireUnconsumed = false)
                        var everMultiTouch = false
                        var travelled = 0f
                        var panned = false

                        do {
                            val event = awaitPointerEvent()
                            val active = event.changes.count { it.pressed }
                            if (active > 1) everMultiTouch = true

                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()
                            travelled += panChange.getDistance()

                            // The transform as it stands *right now*, rebuilt
                            // each event so the pinch anchors against the scale
                            // the fingers are actually looking at.
                            val live = WorldTransform(
                                widthPx, heightPx,
                                viewport.zoom, viewport.panX, viewport.panY
                            )

                            if (everMultiTouch) {
                                if (zoomChange != 1f) {
                                    viewport.pinch(live, event.calculateCentroid(), zoomChange)
                                }
                                if (panChange != Offset.Zero) {
                                    viewport.pan(panChange.x, panChange.y)
                                    panned = true
                                }
                            } else if (viewport.isZoomed && travelled > touchSlop) {
                                // One finger drags the board, but only once
                                // zoomed in and only past the slop -- otherwise
                                // every placement tap with a shaky thumb would
                                // scroll the board instead of deploying.
                                viewport.pan(panChange.x, panChange.y)
                                panned = true
                            }

                            if (everMultiTouch || panned) {
                                // Claim the events so nothing else interprets
                                // them, and keep the stored pan in step with
                                // what the clamp allowed.
                                event.changes.forEach { it.consume() }
                                viewport.settle(
                                    WorldTransform(
                                        widthPx, heightPx,
                                        viewport.zoom, viewport.panX, viewport.panY
                                    )
                                )
                            }
                        } while (event.changes.any { it.pressed })

                        // A tap is what is left: one finger, start to finish,
                        // that never travelled far enough to be a drag. Read
                        // against a transform rebuilt from the *current*
                        // viewport, so a tap is mapped at the zoom it was made
                        // at rather than the zoom the frame started with.
                        val wasTap = !everMultiTouch && !panned &&
                            travelled <= touchSlop && !first.isConsumed
                        if (wasTap) {
                            val at = WorldTransform(
                                widthPx, heightPx,
                                viewport.zoom, viewport.panX, viewport.panY
                            )
                            viewModel.onBattlefieldTap(at.toWorld(first.position))
                        }
                    }
                }
        ) {
            // Subscribes this draw scope to the simulation clock.
            @Suppress("UNUSED_EXPRESSION")
            viewModel.frameTick

            drawIntoCanvas { canvas ->
                renderer.draw(
                    canvas = canvas.nativeCanvas,
                    engine = viewModel.engine,
                    transform = transform,
                    options = options,
                    selection = viewModel.selection,
                    time = viewModel.renderTime
                )
            }
        }
    }
}

/** Bottom control bar: deploy, wave control, speed, pause. */
@Composable
private fun ControlBar(viewModel: GameViewModel) {
    val hud = viewModel.hud
    val canStart = hud.phase == RunPhase.PREPARING

    // Half the height it used to be. The bar is as wide as the screen and sits
    // directly under the battlefield, so every pixel of it is a pixel the board
    // does not get -- and the board is where the agent levels are printed.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Palette.Surface)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        CompactButton(
            text = "AGENTS",
            onClick = viewModel::toggleDeployPanel,
            selected = viewModel.showDeployPanel,
            accent = Palette.Cyan,
            dense = true
        )

        // Only while there is a boss to read about. A dead button that says
        // BOSS when there is no boss teaches the player to ignore it.
        if (hud.bossOnField) {
            CompactButton(
                text = "BOSS",
                onClick = viewModel::toggleBossPanel,
                selected = viewModel.showBossPanel,
                accent = Palette.Red,
                dense = true
            )
        }

        CompactButton(
            text = if (viewModel.paused) "RESUME" else "PAUSE",
            onClick = viewModel::togglePause,
            accent = Palette.Orange,
            dense = true
        )

        // Speed selector: discrete buttons rather than a cycling toggle, so the
        // current speed is always visible instead of having to be remembered.
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Balance.GAME_SPEEDS.forEachIndexed { index, speed ->
                // A speed nobody has bought is shown, not hidden: it is a thing
                // the store sells, and a row that silently loses a button when
                // you do not own it cannot tell you that. It is marked and it
                // is inert.
                val unlocked = index < Balance.speedCount(viewModel.fifthSpeedUnlocked)
                CompactButton(
                    text = if (unlocked) "${speed.toInt()}X" else "${speed.toInt()}X\u2022",
                    onClick = { viewModel.applySpeedIndex(index) },
                    selected = viewModel.speedIndex == index,
                    accent = if (unlocked) Palette.Purple else Palette.TextMuted,
                    dense = true
                )
            }
        }

        Spacer(Modifier.width(4.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = statusLineFor(viewModel),
                style = MaterialTheme.typography.labelSmall,
                color = Palette.TextSecondary,
                maxLines = 1
            )
            if (viewModel.selection.pendingAgent != null) {
                Text(
                    text = "PLACING ${viewModel.selection.pendingAgent?.displayName} — tap a node",
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.Green,
                    maxLines = 1
                )
            }
        }

        if (viewModel.selection.pendingAgent != null) {
            CompactButton(
                text = "CANCEL",
                onClick = viewModel::clearPendingAgent,
                accent = Palette.Red,
                dense = true
            )
        }

        CompactButton(
            // One line now: a two-line label is what set the bar's height in
            // the first place, and the boss warning is already shouted by the
            // banner, the HUD border and the colour of this button.
            text = if (hud.nextWaveIsBoss) "NEXT WAVE \u00B7 BREACH" else "NEXT WAVE",
            onClick = viewModel::startNextWave,
            enabled = canStart,
            accent = if (hud.nextWaveIsBoss) Palette.Red else Palette.Green,
            dense = true
        )
    }
}

private fun statusLineFor(viewModel: GameViewModel): String {
    val hud = viewModel.hud
    return when (hud.phase) {
        RunPhase.PREPARING ->
            if (hud.wave == 0) "> standing by — deploy agents and start wave 1"
            else "> wave ${hud.wave} cleared — prepare for wave ${hud.wave + 1}"
        RunPhase.BOSS_WARNING -> "> !! cyberattack incoming — major breach inbound !!"
        RunPhase.IN_WAVE -> "> wave ${hud.wave} active — ${hud.enemiesRemaining} threats remaining"
        RunPhase.GAME_OVER -> "> network compromised"
    }
}
