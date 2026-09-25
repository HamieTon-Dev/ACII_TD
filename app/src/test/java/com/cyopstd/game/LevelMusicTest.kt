package com.cyopstd.game

import com.cyopstd.game.audio.ChiptuneComposer
import com.cyopstd.game.audio.LevelMusic
import com.cyopstd.game.audio.musicForMap
import com.cyopstd.game.audio.trackForMode
import com.cyopstd.game.core.GameMode
import com.cyopstd.game.core.Maps
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The owner's music, and where each piece plays.
 *
 * Everything else in this game makes its own noise; these six files are the
 * one exception, supplied and licensed by the owner. The brief was three
 * sentences — *"keep the music we had as main menu music"*, *"level music will
 * loop"*, *"music names match same levels and can add to the loops so that
 * it's not repetitive"* — and this is each of them, checked.
 */
class LevelMusicTest {

    private val rawDir = File("src/main/res/raw")

    @Test
    fun `every level has two variants and no file is used twice`() {
        // Two, because one on repeat is noticeable inside three waves. The
        // second variant is the entire reason this is a playlist rather than a
        // looping player.
        for (level in LevelMusic.entries) {
            assertEquals("${level.name} variant count", 2, level.variants.size)
            assertEquals(
                "${level.name} lists the same file twice",
                level.variants.size,
                level.variants.toSet().size
            )
        }
        val all = LevelMusic.entries.flatMap { it.variants }
        assertEquals("two levels share a file: $all", all.size, all.toSet().size)
    }

    @Test
    fun `the playlist alternates rather than repeating`() {
        for (level in LevelMusic.entries) {
            assertEquals("${level.name} should go to its second variant", 1, level.variantAfter(0))
            assertEquals("${level.name} should come back round", 0, level.variantAfter(1))
        }
    }

    @Test
    fun `every supplied file is actually shipped and actually used`() {
        val shipped = rawDir.listFiles()
            .orEmpty()
            .filter { it.extension.lowercase() == "mp3" }
            .map { it.nameWithoutExtension }
            .sorted()
        assertEquals(
            "the six supplied tracks should be the mp3s in res/raw",
            listOf("level1", "level1_2", "level2", "level2_2", "level3", "level3_2"),
            shipped
        )
        // A file in res/raw that no level names is dead weight in the download,
        // and every megabyte is a megabyte of install size.
        assertEquals(
            "six files shipped, ${LevelMusic.entries.sumOf { it.variants.size }} referenced",
            shipped.size,
            LevelMusic.entries.sumOf { it.variants.size }
        )
    }

    @Test
    fun `the files are large enough to be the real tracks`() {
        // A truncated or placeholder file still compiles, still ships, and
        // plays half a second of nothing.
        for (file in rawDir.listFiles().orEmpty().filter { it.extension == "mp3" }) {
            assertTrue(
                "${file.name} is only ${file.length()} bytes",
                file.length() > 500_000L
            )
        }
    }

    // ------------------------------------------------------- which map, which

    @Test
    fun `every level in the game has its own music`() {
        val chosen = Maps.all.associateWith { musicForMap(it) }
        for ((map, music) in chosen) {
            assertNotNull("${map.displayName} has no music", music)
        }
        assertEquals(
            "two levels ended up on the same music: $chosen",
            Maps.all.size,
            chosen.values.distinct().size
        )
    }

    @Test
    fun `the third level's music is composed and waiting, not misfiled`() {
        // Supplied before the map it belongs to exists. It must not be quietly
        // pointed at one of the two levels that do.
        val used = Maps.all.mapNotNull { musicForMap(it) }.toSet()
        assertTrue(
            "LEVEL_THREE should not be playing on an existing level",
            LevelMusic.LEVEL_THREE !in used
        )
    }

    @Test
    fun `the music follows the level, not the difficulty`() {
        // HACK:AI on the perimeter is the same place with harder waves in it.
        assertEquals(LevelMusic.LEVEL_ONE, musicForMap(Maps.PERIMETER))
        assertEquals(LevelMusic.LEVEL_TWO, musicForMap(Maps.HUGGING_FACE))
    }

    // ------------------------------------------------------------ the menu

    @Test
    fun `the menu keeps the music it already had`() {
        // The owner asked for exactly this, and it is worth a test because the
        // obvious way to wire up level music is to route everything through it
        // and leave the menu with whatever falls out.
        val menuSource = File("src/main/java/com/cyopstd/game/audio/AudioEngine.kt").readText()
        assertTrue(
            "the menu should still play the generated MENU track",
            menuSource.contains("MusicEngine(context, ChiptuneComposer.Track.MENU)")
        )
        assertTrue(
            "the menu must not be routed through the level playlists",
            menuSource.lines()
                .filter { it.contains("menuMusic") && it.contains("PlaylistEngine") }
                .isEmpty()
        )
    }

    @Test
    fun `the generated tracks are still there to fall back on`() {
        // Not decoration: a device whose decoder refuses the supplied files
        // plays a level silently unless something can be rendered instead.
        assertEquals(ChiptuneComposer.Track.GAME, trackForMode(GameMode.STANDARD))
        assertEquals(ChiptuneComposer.Track.BOTTLE, trackForMode(GameMode.HACK_AI))
        assertNotEquals(
            "the menu track must not double as a match track",
            ChiptuneComposer.Track.MENU,
            trackForMode(GameMode.STANDARD)
        )
    }
}
