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
    LEVEL_ONE(listOf(R.raw.level1, R.raw.level1_2)),
    LEVEL_TWO(listOf(R.raw.level2, R.raw.level2_2)),

    /** NEURAL-MESH, the third level. */
    LEVEL_THREE(listOf(R.raw.level3, R.raw.level3_2)),

    /** 🦆 DUCK-USB, the fourth level: "Neon Static", supplied by the owner. */
    LEVEL_FOUR(listOf(R.raw.level4, R.raw.level4_2));

    /** The variant that follows [index], wrapping. */
    fun variantAfter(index: Int): Int = (index + 1) % variants.size
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
    // No supplied track yet: the synthesized mode music plays instead.
    else -> null
}
