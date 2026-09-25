package com.cyopstd.game

import android.app.Application
import android.os.Looper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.cyopstd.game.core.GameMode
import com.cyopstd.game.core.Maps
import com.cyopstd.game.state.GameViewModel
import com.cyopstd.game.ui.game.GameScreen
import com.cyopstd.game.model.BossModifier
import com.cyopstd.game.model.BossVariant
import com.cyopstd.game.ui.game.BossDossier
import com.cyopstd.game.ui.game.BossDossierPanel
import com.cyopstd.game.save.GameSettings
import com.cyopstd.game.save.LeaderboardEntry
import com.cyopstd.game.save.PlayerIdentity
import com.cyopstd.game.save.PlayerStats
import com.cyopstd.game.state.GameOverSummary
import com.cyopstd.game.store.BillingStatus
import com.cyopstd.game.store.CosmeticChoice
import com.cyopstd.game.store.Entitlements
import com.cyopstd.game.ui.codex.CodexScreen
import com.cyopstd.game.ui.game.GameOverOverlay
import com.cyopstd.game.ui.game.PauseOverlay
import com.cyopstd.game.ui.menu.AboutScreen
import com.cyopstd.game.ui.menu.AgentsScreen
import com.cyopstd.game.ui.menu.FirmwareScreen
import com.cyopstd.game.ui.menu.LeaderboardScreen
import com.cyopstd.game.ui.menu.LoadoutScreen
import com.cyopstd.game.ui.menu.MainMenuScreen
import com.cyopstd.game.ui.menu.StoreScreen
import com.cyopstd.game.ui.settings.SettingsScreen
import com.cyopstd.game.ui.stats.StatisticsScreen
import com.cyopstd.game.ui.theme.CyOpsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Every screen, at every screen size the store will put it on.
 *
 * Marked a release blocker rather than polish. Google Play lists a device
 * catalogue in the thousands, and a layout that breaks on a narrow phone is a
 * one-star review from somebody who never got to play.
 *
 * **What this proves.** Every screen, at eight viewports from a 4-inch phone
 * held sideways to a tablet and including two accessibility font scales, is
 * laid out with nothing beyond the edge that cannot be scrolled to.
 *
 * **What it does not.** Three things, stated plainly because a green suite is
 * read as more than it is:
 *
 * 1. It does not prove anything *renders* correctly. This environment
 *    substitutes its own fonts; the studio wordmark looked perfect in a
 *    captured preview here and was unreadable on a real phone, twice.
 *    Text *widths* measured here are not the device's.
 * 2. It cannot see a fixed-size panel that is bigger than the screen, because
 *    Compose does not let one exist: `Modifier.height(330.dp)` inside a
 *    full-screen parent is coerced to the parent's height and the content
 *    inside degrades into a scroll. That degradation is the correct outcome,
 *    and it is also why this check stays quiet about it.
 * 3. It says nothing about whether a control is big enough to hit. That is a
 *    separate question with a separate answer in `WorldFitTest`, which is
 *    where the one real fault this audit found actually turned up.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [33])
class ScreenSizeTest(
    private val label: String,
    private val qualifiers: String,
    private val fontScale: Float
) {

    /**
     * The size has to be set here, in the constructor, and not through
     * `@Config(qualifiers = ...)`.
     *
     * `@Config` is resolved once per class, so a parameterised class gets one
     * size for every case — which is exactly what the first version of this did:
     * five differently-named cases all rendering at the same default size, all
     * reporting the same viewport height of 470dp. Five copies of one test is
     * not a spread of sizes, and the names made it look like one.
     *
     * The constructor is the last moment that works: JUnit builds the instance,
     * *then* applies the rules, and it is the compose rule's activity launch
     * that reads the configuration.
     */
    init {
        RuntimeEnvironment.setQualifiers(qualifiers)
        // A player with the system font at 1.3x is a screen-size problem
        // wearing a different hat: every `sp` in the game grows and the dp
        // boxes around them do not. It is not expressible as a qualifier.
        RuntimeEnvironment.setFontScale(fontScale)
    }

    @get:Rule
    val compose = createComposeRule()

    /**
     * What the qualifier asked for, so the check can confirm it got it.
     *
     * At mdpi one dp is one pixel, which is why these can be compared against
     * the root node's size directly.
     */
    private val expectedWidth = Regex("w(\\d+)dp").find(qualifiers)!!.groupValues[1].toInt()
    private val expectedHeight = Regex("h(\\d+)dp").find(qualifiers)!!.groupValues[1].toInt()

    companion object {
        /**
         * The spread, in landscape because the game is landscape-locked.
         *
         * The narrow end is not hypothetical: 568x320dp is a 4-inch phone
         * held sideways and 640x360dp is an ordinary budget one, while
         * several of this game's panels are hard-coded at 420-430dp wide.
         */
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun sizes(): List<Array<Any>> = listOf(
            arrayOf("tiny phone 568x320", "w568dp-h320dp-land-mdpi", 1f),
            arrayOf("small phone 640x360", "w640dp-h360dp-land-mdpi", 1f),
            arrayOf("common phone 800x400", "w800dp-h400dp-land-mdpi", 1f),
            arrayOf("tall phone 900x380", "w900dp-h380dp-land-mdpi", 1f),
            arrayOf("large phone 1000x460", "w1000dp-h460dp-land-mdpi", 1f),
            arrayOf("tablet 1280x800", "w1280dp-h800dp-land-mdpi", 1f),
            // The accessibility end. 1.3x is a common "large text" setting and
            // 2f is the top of Android's slider; both are one tap away in
            // system settings and neither had ever been rendered here.
            arrayOf("small phone, large text", "w640dp-h360dp-land-mdpi", 1.3f),
            arrayOf("small phone, largest text", "w640dp-h360dp-land-mdpi", 2f)
        )
    }

    /**
     * Fails naming anything laid out past the edge of the screen and with no
     * way to reach it.
     *
     * Three things had to be right before this measured anything real.
     *
     * *Position* is `positionInRoot + size`, **not** `boundsInRoot`.
     * `boundsInRoot` is *clipped* to what is visible, so a control laid out
     * far off the side reports bounds neatly inside the viewport and looks
     * fine. The first version used it and passed everything, including a
     * deliberately oversized canary — a harness measuring nothing at all,
     * which is worse than no harness because it reads as proof. (Same lesson
     * as the 1.22.0 dossier bug from the other direction: there a laid-out
     * node had zero *size*; here an off-screen node has innocent *bounds*.
     * Size is the layout truth; bounds are what survived clipping.)
     *
     * *Scrolling* is subtracted. Content below the fold of a `verticalScroll`
     * column, or past the end of a `LazyRow`, is off screen **and reachable**,
     * which is the entire purpose of putting it in one. Measuring without
     * this reported 107 off-screen elements in the store — every card below
     * the first, in a column explicitly built to scroll. A check that flags
     * correct layout that loudly gets muted, and then it is not a check.
     *
     * What is left is the thing worth failing on: a fixed-size panel wider or
     * taller than the phone, with no scroll to rescue it.
     */
    private fun ComposeContentTestRule.assertNothingOverflows(screen: String) {
        val root = onRoot().fetchSemanticsNode()
        val width = root.size.width
        val height = root.size.height
        assertTrue("$screen has no size at all at $label", width > 0 && height > 0)
        assertTrue(
            "$screen rendered at ${width}x$height, which is not $label — the " +
                "size qualifier did not take effect",
            width == expectedWidth && height == expectedHeight
        )

        val overflowing = ArrayList<String>()

        fun walk(
            node: androidx.compose.ui.semantics.SemanticsNode,
            scrollsSideways: Boolean,
            scrollsDown: Boolean
        ) {
            val at = node.positionInRoot
            val left = at.x
            val top = at.y
            val right = left + node.size.width
            val bottom = top + node.size.height
            val label = node.config
                .getOrElse(androidx.compose.ui.semantics.SemanticsProperties.Text) {
                    emptyList()
                }
                .joinToString(" ").take(28).ifBlank { "node ${node.id}" }
            if (node.size.width > 0 && node.size.height > 0) {
                // A pixel of slack: rounding at a layout boundary is not an
                // overflow, and chasing it would make this test noise.
                if (!scrollsSideways && (right > width + 1f || left < -1f)) {
                    overflowing += "$label runs off the side " +
                        "(${left.toInt()}..${right.toInt()} of $width)"
                }
                if (!scrollsDown && (bottom > height + 1f || top < -1f)) {
                    overflowing += "$label runs off the bottom " +
                        "(${top.toInt()}..${bottom.toInt()} of $height)"
                }
            }
            // A scroll container's own box is still measured above -- it is
            // the viewport, and a viewport hanging off the screen is a real
            // fault. Only what is *inside* it gets the exemption.
            val sideways = scrollsSideways || node.config.contains(
                androidx.compose.ui.semantics.SemanticsProperties.HorizontalScrollAxisRange
            )
            val down = scrollsDown || node.config.contains(
                androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange
            )
            node.children.forEach { walk(it, sideways, down) }
        }
        walk(root, scrollsSideways = false, scrollsDown = false)

        assertTrue(
            "$screen at $label: ${overflowing.size} element(s) are off screen — " +
                overflowing.take(4).joinToString("; "),
            overflowing.isEmpty()
        )
    }

    @Test
    fun `the main menu fits`() {
        compose.setContent {
            CyOpsTheme {
                Box(Modifier.fillMaxSize()) {
                    MainMenuScreen(
                        hasSavedRun = true,
                        stats = PlayerStats(highestWave = 120),
                        budget = 99_000,
                        firmwareLevel = 240,
                        adsRemoved = false,
                        availableModes = GameMode.entries,
                        selectedMode = GameMode.STANDARD,
                        availableMaps = Maps.all,
                        selectedMap = Maps.PERIMETER,
                        backgroundAnimation = false,
                        onSelectMode = {}, onSelectMap = {},
                        onPlay = {}, onContinue = {}, onAgents = {},
                        onFirmware = {}, onCodex = {}, onStore = {}, onLoadout = {},
                        onPlayAccount = {}, onLeaderboard = {}, onStatistics = {},
                        onSettings = {}, onAbout = {}, onExit = {}
                    )
                }
            }
        }
        compose.assertNothingOverflows("the main menu")
    }

    @Test
    fun `the boss dossier fits over the battlefield`() {
        // The one most likely to be wrong. It is a fixed 430x330dp panel, and
        // a 360dp-tall phone has roughly 280dp of battlefield once the HUD
        // strip and the control bar have taken their share.
        compose.setContent {
            CyOpsTheme {
                Box(Modifier.fillMaxSize()) {
                    BossDossierPanel(
                        dossier = BossDossier(
                            variant = BossVariant.GOOD_GAME,
                            health = 420f,
                            maxHealth = 1_000f,
                            armor = 14f,
                            speed = 38f,
                            modifiers = BossModifier.entries,
                            revived = true,
                            distanceToCore = 640f
                        ),
                        onClose = {}
                    )
                }
            }
        }
        compose.assertNothingOverflows("the boss dossier")
    }

    @Test
    fun `the match screen fits, with a panel open over it`() {
        // The panel on its own fits anywhere; the question is whether it fits
        // in what is *left* of the screen once the HUD strip and the control
        // bar have taken theirs. That is the real constraint and the only way
        // to measure it is to render the actual screen.
        val application = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = GameViewModel(application, TestStores.isolatedRepository())
        shadowOf(Looper.getMainLooper()).idle()
        viewModel.startNewGame()
        shadowOf(Looper.getMainLooper()).idle()
        viewModel.toggleDeployPanel()

        compose.mainClock.autoAdvance = false
        compose.setContent {
            CyOpsTheme {
                GameScreen(viewModel = viewModel, onExitToMenu = {}, onOpenSettings = {})
            }
        }
        compose.mainClock.advanceTimeBy(64)
        compose.assertNothingOverflows("the match screen with the roster open")
    }

    /**
     * Proves the check can fail.
     *
     * A screen-size test that never reports anything is indistinguishable
     * from one that is not looking, and this one reported nothing on its
     * first run at every size including an absurd 320x200 -- which is either
     * good news or a broken measurement. This settles which.
     *
     * `requiredWidth`, not `width`: `width` is *coerced into the incoming
     * constraints*, so a 4000dp control inside a full-screen Box measures at
     * the screen width and genuinely does not overflow. The first version of
     * this canary used it, failed, and looked for a while like evidence the
     * walker was blind — when in fact the thing it was pointing at fitted.
     * A canary that cannot misfire is worth as much as the check it guards.
     */
    @Test
    fun `the check catches something that genuinely does not fit`() {
        compose.setContent {
            CyOpsTheme {
                Box(Modifier.fillMaxSize()) {
                    androidx.compose.material3.Text(
                        text = "THIS IS DELIBERATELY FAR TOO WIDE TO FIT ON ANY SCREEN",
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.requiredWidth(4000.dp)
                    )
                }
            }
        }
        var reported = false
        try {
            compose.assertNothingOverflows("the deliberately oversized control")
        } catch (expected: AssertionError) {
            reported = true
        }
        assertTrue("the overflow check did not notice a 4000dp control", reported)
    }

    /**
     * Proves the font-scale parameter is doing something.
     *
     * The same lesson as the viewport-size assertion above, which caught five
     * differently-named cases all rendering at one size: a parameter that is
     * silently ignored turns extra cases into extra copies, and the names make
     * the suite look broader than it is. This one measures a line of text and
     * checks it grew.
     */
    @Test
    fun `the font scale parameter reaches the composition`() {
        var seen = 0f
        compose.setContent {
            CyOpsTheme {
                seen = androidx.compose.ui.platform.LocalDensity.current.fontScale
                Box(Modifier.fillMaxSize())
            }
        }
        assertEquals("the font scale never reached the composition", fontScale, seen, 0.001f)
    }

    /**
     * Zoom has to earn its keep on every size, not just the small one.
     *
     * The feature exists for the 568x320dp phone, but a maximum zoom that is
     * too weak on a large phone or absurd on a tablet is still a bug. This
     * checks the property that matters at each size: the fitted board shows
     * the whole battlefield, and the fully zoomed board makes adjacent
     * deployment nodes at least a 48dp touch target.
     */
    @Test
    fun `zoom makes nodes tappable at this screen size`() {
        val fitted = com.cyopstd.game.ui.game.WorldTransform(
            expectedWidth.toFloat(),
            expectedHeight.toFloat()
        )
        val zoomed = com.cyopstd.game.ui.game.WorldTransform(
            expectedWidth.toFloat(),
            expectedHeight.toFloat(),
            com.cyopstd.game.ui.game.WorldTransform.MAX_ZOOM
        )

        // The whole board is visible when fitted.
        assertTrue(
            "the fitted board does not fit at $label",
            fitted.toScreenX(com.cyopstd.game.core.WorldGeometry.WIDTH) <= expectedWidth + 1f &&
                fitted.toScreenY(com.cyopstd.game.core.WorldGeometry.HEIGHT) <= expectedHeight + 1f
        )

        var closest = Float.MAX_VALUE
        val nodes = com.cyopstd.game.core.Maps.PERIMETER.nodes
        for (i in nodes.indices) {
            for (j in i + 1 until nodes.size) {
                val dx = nodes[i].x - nodes[j].x
                val dy = nodes[i].y - nodes[j].y
                closest = minOf(closest, kotlin.math.sqrt(dx * dx + dy * dy))
            }
        }
        val spacingDp = closest * zoomed.scale
        assertTrue(
            "at maximum zoom adjacent nodes are ${spacingDp.toInt()}dp apart at " +
                "$label, which is under the 48dp touch-target guidance this " +
                "feature exists to satisfy",
            spacingDp >= 48f
        )
    }

    @Test
    fun `the store fits`() {
        compose.setContent {
            CyOpsTheme {
                Box(Modifier.fillMaxSize()) {
                    StoreScreen(
                        entitlements = Entitlements(),
                        budget = 99_000,
                        prices = emptyMap(),
                        status = BillingStatus.READY,
                        backgroundAnimation = false,
                        onBuy = {}, onRestore = {}, onBack = {}
                    )
                }
            }
        }
        compose.assertNothingOverflows("the store")
    }

    // ------------------------------------------------------- the rest of it
    //
    // Every screen a player can reach from the menu, plus the two overlays
    // that arrive uninvited over a match. Cheap to add and the whole point:
    // the screen that breaks on a small phone will be one nobody thought to
    // check by hand.

    private fun show(content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent {
            CyOpsTheme {
                Box(Modifier.fillMaxSize()) { content() }
            }
        }
    }

    @Test
    fun `the agent roster fits`() {
        show {
            AgentsScreen(
                unlockedAgents = emptySet(),
                highestWave = 40,
                backgroundAnimation = false,
                onBack = {}
            )
        }
        compose.assertNothingOverflows("the agent roster")
    }

    @Test
    fun `the firmware screen fits`() {
        show {
            FirmwareScreen(
                budget = 99_000,
                firmwareLevel = 240,
                lifetimeBudgetEarned = 500_000,
                backgroundAnimation = false,
                onBuy = {}, onBack = {}
            )
        }
        compose.assertNothingOverflows("the firmware screen")
    }

    @Test
    fun `the loadout screen fits`() {
        show {
            LoadoutScreen(
                entitlements = Entitlements(),
                cosmetics = CosmeticChoice(),
                backgroundAnimation = false,
                onChooseCoreSkin = {}, onChooseBackground = {},
                onSpectrumAgents = {}, onOpenStore = {}, onBack = {}
            )
        }
        compose.assertNothingOverflows("the loadout screen")
    }

    @Test
    fun `the settings screen fits`() {
        show {
            SettingsScreen(
                settings = GameSettings(),
                backgroundAnimation = false,
                onUpdate = {}, onResetProgress = {}, onBack = {}
            )
        }
        compose.assertNothingOverflows("the settings screen")
    }

    @Test
    fun `the codex fits`() {
        show { CodexScreen(backgroundAnimation = false, onBack = {}) }
        compose.assertNothingOverflows("the codex")
    }

    @Test
    fun `the statistics screen fits`() {
        show {
            StatisticsScreen(
                stats = PlayerStats(
                    highestWave = 120,
                    totalAttacksBlocked = 48_200,
                    totalBossesDefeated = 310,
                    totalCryptoEarned = 964_000,
                    totalGamesPlayed = 240,
                    deploymentsByAgent = mapOf("FIREWALL" to 900, "ANALYST" to 410)
                ),
                budget = 99_000,
                firmwareLevel = 240,
                lifetimeBudgetEarned = 500_000,
                backgroundAnimation = false,
                onBack = {}
            )
        }
        compose.assertNothingOverflows("the statistics screen")
    }

    @Test
    fun `the about screen fits`() {
        show { AboutScreen(backgroundAnimation = false, onBack = {}) }
        compose.assertNothingOverflows("the about screen")
    }

    @Test
    fun `the leaderboard fits`() {
        show {
            LeaderboardScreen(
                identity = PlayerIdentity(username = "OPERATOR", highestWave = 120),
                entries = List(12) {
                    LeaderboardEntry(
                        username = "OPERATOR_$it",
                        wave = 120 - it,
                        damage = 900_000L - it
                    )
                },
                backgroundAnimation = false,
                onRegister = {}, onBack = {}
            )
        }
        compose.assertNothingOverflows("the leaderboard")
    }

    @Test
    fun `the pause overlay fits`() {
        show {
            PauseOverlay(wave = 37, onResume = {}, onRestart = {}, onSettings = {}, onMainMenu = {})
        }
        compose.assertNothingOverflows("the pause overlay")
    }

    @Test
    fun `the game over summary fits, revive offer and all`() {
        // The tallest variant on purpose: a new record plus a revive button
        // is the most content this overlay ever carries.
        compose.mainClock.autoAdvance = false
        show {
            GameOverOverlay(
                summary = GameOverSummary(
                    waveReached = 121,
                    attacksBlocked = 4_820,
                    cryptoEarned = 96_400,
                    bossesDefeated = 30,
                    bestWave = 121,
                    isNewRecord = true
                ),
                onRetry = {}, onMainMenu = {},
                onWatchAdToRevive = {},
                revivesLeft = 1
            )
        }
        compose.mainClock.advanceTimeBy(64)
        compose.assertNothingOverflows("the game over summary")
    }
}
