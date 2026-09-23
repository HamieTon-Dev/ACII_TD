package com.cyopstd.game

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.cyopstd.game.ui.theme.Palette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

/**
 * Guards the two promises the backdrop shift makes: that it is subtle, and
 * that it can never be confused with an enemy.
 */
class PaletteTest {

    private val enemyColours = listOf(
        "red" to Palette.Red,
        "magenta" to Palette.Magenta,
        "orange" to Palette.Orange,
        "purple" to Palette.Purple,
        "deep red" to Palette.RedDeep
    )

    @Test
    fun `the backdrop holds for five waves then moves on`() {
        for (wave in 1..5) {
            assertEquals(Palette.backdropBand(1), Palette.backdropBand(wave))
        }
        assertNotEquals(Palette.backdropBand(5), Palette.backdropBand(6))
        for (wave in 6..10) {
            assertEquals(Palette.backdropBand(6), Palette.backdropBand(wave))
        }
        assertNotEquals(Palette.backdropBand(10), Palette.backdropBand(11))
    }

    @Test
    fun `wave zero is the starting colour and the bands cycle forever`() {
        // The menu and the pre-wave lobby both sit at wave 0.
        assertEquals(Palette.backdropBands[0], Palette.backdropBand(0))
        assertEquals(Palette.backdropBands[0], Palette.backdropBand(1))

        val cycle = Palette.backdropBands.size * Palette.BACKDROP_BAND_WAVES
        assertEquals(Palette.backdropBand(3), Palette.backdropBand(3 + cycle))
        // Deep into a run it must still return a real colour, not fall off.
        assertTrue(Palette.backdropBand(10_000) in Palette.backdropBands)
    }

    @Test
    fun `no backdrop colour resembles an enemy colour`() {
        for (band in Palette.backdropBands) {
            for ((name, enemy) in enemyColours) {
                val distance = distance(band, enemy)
                assertTrue(
                    "backdrop ${hex(band)} is only $distance from $name",
                    distance > 0.6f
                )
            }
            // Enemies are the warm half of the palette; the backdrop is never
            // allowed in there, which is a stronger guarantee than distance.
            assertTrue(
                "backdrop ${hex(band)} is a warm colour",
                band.red <= band.blue + 0.01f
            )
        }
    }

    @Test
    fun `every backdrop colour stays dark enough to read ASCII on`() {
        for (band in Palette.backdropBands) {
            val luminance = 0.2126f * band.red + 0.7152f * band.green + 0.0722f * band.blue
            assertTrue("backdrop ${hex(band)} luminance $luminance", luminance < 0.09f)
        }
    }

    @Test
    fun `consecutive shifts are a nudge rather than a jolt`() {
        for (i in Palette.backdropBands.indices) {
            val from = Palette.backdropBands[i]
            val to = Palette.backdropBands[(i + 1) % Palette.backdropBands.size]
            val distance = distance(from, to)
            // Far enough apart to notice over a couple of waves, close enough
            // that the transition never draws attention to itself.
            assertTrue("${hex(from)} -> ${hex(to)} moves $distance", distance in 0.015f..0.14f)
        }
    }

    private fun distance(a: Color, b: Color): Float = sqrt(
        (a.red - b.red) * (a.red - b.red) +
            (a.green - b.green) * (a.green - b.green) +
            (a.blue - b.blue) * (a.blue - b.blue)
    )

    private fun hex(color: Color): String = "#%06X".format(color.toArgb() and 0xFFFFFF)
}
