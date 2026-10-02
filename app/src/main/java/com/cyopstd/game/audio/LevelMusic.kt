package com.cyopstd.game.audio

import com.cyopstd.game.R
import com.cyopstd.game.core.GameMap
import com.cyopstd.game.core.Maps

/**
 * The music a level plays, as supplied by the owner.
 *
 * Every other piece of audio in this game is synthesized at runtime by
 * [ChiptuneComposer] — a deliberate property, since nothing generated has a
 * licensing surface. These files are the exception: the owner composed
 * them, holds the licences for them, and asked for them specifically, so they
 * are the one thing in `res/raw` that is music.
 *
 * Each level is **two** renders of the same piece rather than one. The owner's
 * words: *"can add to the loops so that it's not repetitive."* A single track
 * on repeat is noticeable within about three waves; two alternating renders
 * double the period before anything repeats and cost nothing but the second
 * file.
 */
enum class LevelMusic(
    /** Raw resources, played in this order and then round again. */
    val variants: List<Int>
) {
    // Every track is the owner's own composition, titled "CyOps TD - Level X (Y)"
    // (owner, 2026-10-01). Levels 1-3 were replaced that day; 6-10 are new.
    LEVEL_ONE(listOf(R.raw.level1, R.raw.level1_2)),
    LEVEL_TWO(listOf(R.raw.level2, R.raw.level2_2)),
    LEVEL_THREE(listOf(R.raw.level3, R.raw.level3_2)),
    LEVEL_FOUR(listOf(R.raw.level4, R.raw.level4_2)),
    LEVEL_FIVE(listOf(R.raw.level5, R.raw.level5_2)),
    LEVEL_SIX(listOf(R.raw.level6, R.raw.level6_2)),
    LEVEL_SEVEN(listOf(R.raw.level7, R.raw.level7_2)),
    LEVEL_EIGHT(listOf(R.raw.level8, R.raw.level8_2)),
    LEVEL_NINE(listOf(R.raw.level9, R.raw.level9_2)),
    LEVEL_TEN(listOf(R.raw.level10, R.raw.level10_2));

    /** The variant that follows [index], wrapping. */
    fun variantAfter(index: Int): Int = (index + 1) % variants.size

    /**
     * What a match on this level actually plays, round and round: its own
     * two, Liminal Space, its own two, Liminal Haze (owner, 2026-10-02: two
     * tracks *"in rotation that can play on any map"*).
     */
    val rotation: List<Int>
        get() = variants + ANY_LEVEL[0] + variants + ANY_LEVEL[1]

    companion object {
        /** The owner's two tracks for every level (2026-10-02). */
        val ANY_LEVEL: List<Int> = listOf(R.raw.liminal_space, R.raw.liminal_haze)

        /** Their names, in the same order. */
        val ANY_LEVEL_NAMES: List<String> = listOf("Liminal Space", "Liminal Haze")
    }
}

/**
 * Which music a level plays.
 *
 * Keyed by the map rather than by the game mode, because that is what the
 * files are named for and what a player would expect: HACK:AI on the perimeter
 * is the same place with harder waves in it, not a different level.
 *
 * An exhaustive `when` on the id would not compile-check anything (ids are
 * strings), so the lookup is a table and `LevelMusicTest` asserts that every
 * map in the catalog is in it.
 */
fun musicForMap(map: GameMap): LevelMusic? = when (map.id) {
    Maps.PERIMETER.id -> LevelMusic.LEVEL_ONE
    Maps.HUGGING_FACE.id -> LevelMusic.LEVEL_TWO
    Maps.NEURAL_MESH.id -> LevelMusic.LEVEL_THREE
    Maps.DUCK_USB.id -> LevelMusic.LEVEL_FOUR
    Maps.DDOS.id -> LevelMusic.LEVEL_FIVE
    Maps.TRIDENT.id -> LevelMusic.LEVEL_SIX
    Maps.SPIRAL.id -> LevelMusic.LEVEL_SEVEN
    Maps.ZIGZAG.id -> LevelMusic.LEVEL_EIGHT
    Maps.HELIX.id -> LevelMusic.LEVEL_NINE
    Maps.BRAID.id -> LevelMusic.LEVEL_TEN
    // A level with no supplied track plays the synthesized mode music.
    else -> null
}

/**
 * One supplied music file, as the pause-menu music player lists it (owner,
 * 2026-10-01): every level's tracks, in level order.
 */
data class MusicTrack(
    val resId: Int,
    /** The level's `GameMap.id`. */
    val mapId: String,
    /** 1-based level number. */
    val level: Int,
    val levelName: String,
    /** 1 or 2: which of the level's two renders. */
    val part: Int,
    /** A track of its own name rather than a level's ("Liminal Space"). */
    val name: String? = null
) {
    /**
     * The owner's naming (2026-10-01): "CyOps TD - Level 4 (1)", or for the
     * menu's tracks ([level] 0) "CyOps TD - Main Menu (1)". Also the ID3 title.
     */
    val title: String
        get() = when {
            name != null -> "CyOps TD - $name"
            level == 0 -> "CyOps TD - Main Menu ($part)"
            else -> "CyOps TD - Level $level ($part)"
        }

    /** What the soundtrack is saved to the phone as. */
    val fileName: String get() = "$title.mp3"

    /** Stable across builds, unlike [resId]: what the saved pick is stored as. */
    val key: String get() = "$mapId|$part"

    /** "CyOps TD - Level 4 (1) · 🦆 DUCK-USB": the title, and which level it belongs to. */
    val label: String
        get() = when {
            name != null -> "$title \u00B7 ANY LEVEL"
            level == 0 -> title
            else -> "$title \u00B7 $levelName"
        }
}

/**
 * Every supplied track, built from the level catalogue, so a level that gets
 * music appears in the list without this changing.
 */
object MusicLibrary {
    /** The main menu's two tracks (owner, 2026-10-01), first in the album. */
    val menuTracks: List<MusicTrack> = listOf(R.raw.menu, R.raw.menu_2).mapIndexed { part, res ->
        MusicTrack(res, MENU_ID, 0, "MAIN MENU", part + 1)
    }

    const val MENU_ID = "menu"

    /** Liminal Space and Liminal Haze, which play on every level (2026-10-02), last in the album. */
    val anyLevelTracks: List<MusicTrack> = LevelMusic.ANY_LEVEL.mapIndexed { part, res ->
        MusicTrack(res, ANY_LEVEL_ID, -1, "ANY LEVEL", part + 1, name = LevelMusic.ANY_LEVEL_NAMES[part])
    }

    const val ANY_LEVEL_ID = "any"

    val tracks: List<MusicTrack> by lazy {
        menuTracks + Maps.all.withIndex().flatMap { (i, map) ->
            val music = musicForMap(map) ?: return@flatMap emptyList()
            music.variants.mapIndexed { part, res ->
                MusicTrack(res, map.id, i + 1, map.displayName, part + 1)
            }
        } + anyLevelTracks
    }

    /** The track saved as [key], or null if there is none (or it no longer exists). */
    fun indexOfKey(key: String?): Int? =
        key?.let { k -> tracks.indexOfFirst { it.key == k }.takeIf { it >= 0 } }

    /** Where [map]'s first track sits in [tracks], or null if it has none. */
    fun firstTrackOf(map: GameMap): Int? {
        val res = musicForMap(map)?.variants?.firstOrNull() ?: return null
        return tracks.indexOfFirst { it.resId == res }.takeIf { it >= 0 }
    }
}
