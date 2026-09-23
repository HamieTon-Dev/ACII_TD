package com.cyopstd.game

import androidx.test.core.app.ApplicationProvider
import com.cyopstd.game.audio.ChiptuneComposer
import com.cyopstd.game.audio.MusicEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * The track costs a second or two to render, so it is cached. Everything that
 * can go wrong with a cache of a large generated file is what this covers: a
 * re-render on every launch (slow), or worse, a half-written file that is never
 * replaced and leaves the player with silence forever.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MusicCacheTest {

    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val engine = MusicEngine(context)
    private val expectedLength = 44L + ChiptuneComposer.totalFrames * 2L

    @Test
    fun `the track is rendered once and then reused`() {
        val first = engine.ensureTrackFile()
        assertTrue(first.exists())
        assertEquals(expectedLength, first.length())

        first.setLastModified(1_000_000L)
        val second = engine.ensureTrackFile()
        assertEquals(first.absolutePath, second.absolutePath)
        assertEquals("the track was re-rendered", 1_000_000L, second.lastModified())
    }

    @Test
    fun `a truncated render is replaced rather than played`() {
        val file = engine.ensureTrackFile()
        file.writeBytes(ByteArray(2048))
        val repaired = engine.ensureTrackFile()
        assertEquals(expectedLength, repaired.length())
    }

    @Test
    fun `renders from an older arrangement are cleaned up`() {
        val dir = File(context.cacheDir, "music").apply { mkdirs() }
        val stale = File(dir, "lofi_v0.wav").apply { writeBytes(ByteArray(64)) }
        engine.ensureTrackFile()
        assertFalse("the previous version's track was left behind", stale.exists())
    }

    @Test
    fun `a stale render is cleaned up even when the current one is already good`() {
        val current = engine.ensureTrackFile()
        val stale = File(current.parentFile, "lofi_v0.wav").apply { writeBytes(ByteArray(64)) }
        engine.ensureTrackFile()
        assertFalse("megabytes of dead cache would be kept forever", stale.exists())
        assertEquals(expectedLength, current.length())
    }

    @Test
    fun `no partial file is left behind after a render`() {
        engine.ensureTrackFile()
        val dir = File(context.cacheDir, "music")
        assertFalse(File(dir, "lofi.partial").exists())
        assertEquals(1, dir.listFiles()?.size)
    }
}
