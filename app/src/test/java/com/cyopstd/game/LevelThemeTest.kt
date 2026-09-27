package com.cyopstd.game

import com.cyopstd.game.core.Maps
import com.cyopstd.game.engine.GameEngine
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

/**
 * Per-level colour themes (owner, 2026-09-27): the first level keeps its
 * look; HUGGING-FACE is dark green with slightly brighter green lanes.
 */
class LevelThemeTest {

    private fun rgb(c: Int) = floatArrayOf((c shr 16 and 0xFF) / 255f, (c shr 8 and 0xFF) / 255f, (c and 0xFF) / 255f)
    private fun luminance(c: Int) = rgb(c).let { 0.2126f * it[0] + 0.7152f * it[1] + 0.0722f * it[2] }
    private fun distance(a: Int, b: Int): Float {
        val x = rgb(a); val y = rgb(b)
        return sqrt((x[0] - y[0]) * (x[0] - y[0]) + (x[1] - y[1]) * (x[1] - y[1]) + (x[2] - y[2]) * (x[2] - y[2]))
    }
    private val threats = mapOf(
        "hostile" to GameEngine.COLOR_HOSTILE,
        "warning" to GameEngine.COLOR_WARNING,
        "elite" to GameEngine.COLOR_ELITE
    )

    @Test
    fun `the first level keeps its original look`() {
        assertNull(Maps.PERIMETER.theme)
    }

    @Test
    fun `HUGGING-FACE is dark green with brighter green lanes`() {
        val theme = assertNotNull(Maps.HUGGING_FACE.theme).let { Maps.HUGGING_FACE.theme!! }
        for (c in listOf(theme.backdrop, theme.laneFill)) {
            val (r, g, b) = rgb(c).toList()
            assertTrue("#%06X is not green".format(c and 0xFFFFFF), g > r && g > b)
        }
        assertTrue("lanes should be brighter than the board", luminance(theme.laneFill) > luminance(theme.backdrop))
        // Owner, later: "a little darker green" for the board, lanes unchanged.
        assertTrue("board should be very dark: ${luminance(theme.backdrop)}", luminance(theme.backdrop) < 0.04f)
    }

    @Test
    fun `NEURAL-MESH is very dark red with slightly lighter red lanes`() {
        val theme = Maps.NEURAL_MESH.theme!!
        for (c in listOf(theme.backdrop, theme.laneFill)) {
            val (r, g, b) = rgb(c).toList()
            assertTrue("#%06X is not red".format(c and 0xFFFFFF), r > g && r > b)
        }
        assertTrue(luminance(theme.backdrop) < 0.04f)
        assertTrue(luminance(theme.laneFill) > luminance(theme.backdrop))
    }

    @Test
    fun `DUCK-USB is very very dark yellow with slightly lighter yellow lanes`() {
        val theme = Maps.DUCK_USB.theme!!
        for (c in listOf(theme.backdrop, theme.laneFill)) {
            val (r, g, b) = rgb(c).toList()
            assertTrue("#%06X is not yellow".format(c and 0xFFFFFF), r > b * 2 && g > b * 2 && kotlin.math.abs(r - g) < 0.03f)
        }
        assertTrue("backdrop too bright: ${luminance(theme.backdrop)}", luminance(theme.backdrop) < 0.05f)
        assertTrue(luminance(theme.laneFill) > luminance(theme.backdrop))
    }

    @Test
    fun `DDoS is very very dark orange with slightly lighter orange lanes`() {
        val theme = Maps.DDOS.theme!!
        for (c in listOf(theme.backdrop, theme.laneFill)) {
            val (r, g, b) = rgb(c).toList()
            assertTrue("#%06X is not orange".format(c and 0xFFFFFF), r > g && g > b && g > r * 0.35f)
        }
        assertTrue("backdrop too bright: ${luminance(theme.backdrop)}", luminance(theme.backdrop) < 0.05f)
        assertTrue(luminance(theme.laneFill) > luminance(theme.backdrop))
    }

    @Test
    fun `every theme stays dark and clear of the threat colours`() {
        for (map in Maps.all) {
            val theme = map.theme ?: continue
            assertTrue("${map.id} backdrop too bright", luminance(theme.backdrop) < 0.09f)
            assertTrue("${map.id} lanes too bright to read ASCII on", luminance(theme.laneFill) < 0.2f)
            for (c in listOf(theme.backdrop, theme.laneFill, theme.laneBorder)) {
                for ((name, threat) in threats) {
                    assertTrue("${map.id} #%06X too close to $name".format(c and 0xFFFFFF), distance(c, threat) > 0.6f)
                }
            }
        }
    }
}
