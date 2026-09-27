package com.cyopstd.game

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.cyopstd.game.core.Balance
import com.cyopstd.game.core.GameMode
import com.cyopstd.game.core.Maps
import com.cyopstd.game.core.WorldGeometry
import com.cyopstd.game.engine.GameEngine
import com.cyopstd.game.engine.WaveGenerator
import com.cyopstd.game.model.AgentType
import com.cyopstd.game.save.PlayerStats
import com.cyopstd.game.ui.game.BattlefieldRenderOptions
import com.cyopstd.game.ui.game.BattlefieldRenderer
import com.cyopstd.game.ui.game.BattlefieldSelection
import com.cyopstd.game.ui.game.WorldTransform
import com.cyopstd.game.ui.menu.MainMenuScreen
import com.cyopstd.game.ui.theme.CyOpsTheme
import java.io.File
import kotlin.random.Random
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Not an assertion: before/after frames for the saturation pass (backlog S3).
 * The first run writes `before-*.png`; later runs write `after-*.png` and a
 * `compare-*.png` with before on top and after below.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w960dp-h440dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SaturationPreview {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val out = File("build/saturation").apply { mkdirs() }

    @Test
    fun `battlefield`() {
        val random = Random(4)
        val engine = GameEngine(random, WaveGenerator(random))
        engine.isAgentUnlocked = { true }
        engine.startNewRun()
        engine.addCrypto(10_000_000, countAsEarned = false)
        val nodes = Maps.PERIMETER.nodesByCoverage.map { it.id }
        AgentType.entries.forEachIndexed { i, type ->
            engine.placeAgent(type, nodes[i])
            engine.upgradeAgent(nodes[i], if (i % 2 == 0) 1 else 7)
        }
        engine.restore(
            wave = 11, serverHp = 60, crypto = 5000,
            placements = engine.snapshotPlacements(),
            attacksBlocked = 0, cryptoEarned = 0, bossesDefeated = 0,
            serverDamageTaken = 0, agentsDeployed = 0, agentUpgrades = 0
        )
        engine.startNextWave()
        repeat(60 * 7) { engine.update(1f / 60f, 1f) }
        engine.effectSystem().spawnShards(
            x = WorldGeometry.WIDTH * 0.42f, y = WorldGeometry.HEIGHT * 0.5f,
            colorArgb = GameEngine.COLOR_HOSTILE,
            radius = Balance.BOSS_SHARD_RADIUS, lifetime = Balance.BOSS_SHARD_LIFETIME
        )
        repeat(12) { engine.effectSystem().update(1f / 60f) }

        val bitmap = Bitmap.createBitmap(
            WorldGeometry.WIDTH.toInt(), WorldGeometry.HEIGHT.toInt(), Bitmap.Config.ARGB_8888
        )
        BattlefieldRenderer().draw(
            canvas = Canvas(bitmap),
            engine = engine,
            transform = WorldTransform(WorldGeometry.WIDTH, WorldGeometry.HEIGHT),
            options = BattlefieldRenderOptions(backgroundAnimation = false),
            selection = BattlefieldSelection(),
            time = 1f
        )
        save("battlefield", bitmap)
    }

    @Test
    fun `main menu`() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CyOpsTheme {
                Box(Modifier.fillMaxSize()) {
                    MainMenuScreen(
                        hasSavedRun = true,
                        stats = PlayerStats(highestWave = 64),
                        budget = 12_500L,
                        firmwareLevel = 12,
                        adsRemoved = false,
                        availableModes = listOf(GameMode.STANDARD, GameMode.HACK_AI),
                        selectedMode = GameMode.STANDARD,
                        availableMaps = listOf(Maps.PERIMETER),
                        selectedMap = Maps.PERIMETER,
                        backgroundAnimation = false,
                        onSelectMode = {}, onSelectMap = {}, onPlay = {},
                        onContinue = {}, onAgents = {}, onFirmware = {}, onCodex = {}, onStore = {},
                        onLoadout = {}, onPlayAccount = {}, onLeaderboard = {},
                        onStatistics = {}, onSettings = {}, onAbout = {}, onExit = {}
                    )
                }
            }
        }
        compose.mainClock.advanceTimeBy(700)
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        compose.runOnUiThread { view.draw(Canvas(bitmap)) }
        save("menu", bitmap)
    }

    private fun save(name: String, bitmap: Bitmap) {
        val before = File(out, "before-$name.png")
        if (!before.exists()) {
            before.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            return
        }
        File(out, "after-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val old = BitmapFactory.decodeFile(before.path)
        val gap = 12
        val both = Bitmap.createBitmap(bitmap.width, old.height + gap + bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(both)
        canvas.drawColor(0xFFFFFFFF.toInt())
        canvas.drawBitmap(old, 0f, 0f, null)
        canvas.drawBitmap(bitmap, 0f, (old.height + gap).toFloat(), null)
        File(out, "compare-$name.png").outputStream().use { both.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
