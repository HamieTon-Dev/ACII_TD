package com.cyopstd.game.audio

import kotlinx.coroutines.CoroutineScope

/**
 * Something that plays underneath the game.
 *
 * There are two kinds now — a generated track streamed from the cache, and a
 * playlist of supplied files — and [AudioEngine] treats them identically:
 * everything it does is start, pause, set a level, and release. Naming that
 * contract means the volume rules, the startup fade and the backgrounding
 * behaviour are written once and cannot drift apart between the two.
 */
interface BackgroundTrack {

    /** What the game has asked for, whether or not the audio is ready yet. */
    val wantsToPlay: Boolean

    /** Do any loading off the main thread. Safe to call more than once. */
    fun prepare(scope: CoroutineScope)

    fun start()

    /** Pauses rather than stops, so playback resumes where it left off. */
    fun pause()

    /** The player's music setting, already multiplied by the startup ramp. */
    fun setVolume(value: Float)

    fun release()
}

/** Nothing below this is worth sending to a player. */
internal const val MIN_AUDIBLE = 0.01f

/**
 * Music sits under the effects: it is atmosphere, not the game.
 *
 * Shared by both kinds of track so that the music slider means one thing.
 */
internal const val MUSIC_TRIM = 0.55f
