package com.cyopstd.game.audio

import com.cyopstd.game.engine.GameSound

/**
 * The synthesis recipes for every effect in the game.
 *
 * These are tuned to sit together as a family: UI is a clean short blip, combat
 * is noisy and percussive, and anything to do with the server is a low warning
 * sweep. Adjusting the game's whole audio character is a matter of editing this
 * one table.
 */
object SoundBank {

    data class Recipe(
        val duration: Float,
        val voices: List<ToneSynth.Voice>,
        /** Per-effect mix trim so a busy battlefield does not become noise. */
        val gain: Float = 1f
    )

    /**
     * The shortest gap allowed between two plays of the same effect, in
     * seconds; zero means no limit.
     */
    fun minIntervalSeconds(sound: GameSound): Float = minIntervals[sound] ?: 0f

    private val minIntervals: Map<GameSound, Float> = mapOf(
        // Every projectile landing used to play its own sound, so a few fast
        // agents stacked dozens a second into one continuous buzz. The hit is
        // now silent; the kill note is capped at about six a second, so even
        // a wave melting at 5x is a gentle patter, never a drone.
        GameSound.PACKET_HIT to 0.07f,
        GameSound.PACKET_DESTROYED to 0.16f
    )

    /**
     * Playback rates to pick from at random, so a run of kills is a loose
     * melody rather than the same note over and over. They are the steps of a
     * major pentatonic scale, and any two of those sound fine together, so
     * however kills overlap they never clash.
     */
    fun pitchVariants(sound: GameSound): List<Float> = pitchVariants[sound] ?: listOf(1f)

    private val pitchVariants: Map<GameSound, List<Float>> = mapOf(
        GameSound.PACKET_DESTROYED to listOf(1f, 9f / 8f, 5f / 4f, 3f / 2f, 5f / 3f)
    )

    val recipes: Map<GameSound, Recipe> = mapOf(
        GameSound.UI_CLICK to Recipe(
            duration = 0.07f,
            voices = listOf(
                ToneSynth.Voice(ToneSynth.Wave.SQUARE, 880f, 1180f, 0.35f, decay = 34f)
            ),
            gain = 0.7f
        ),

        GameSound.AGENT_PLACED to Recipe(
            duration = 0.20f,
            voices = listOf(
                ToneSynth.Voice(ToneSynth.Wave.TRIANGLE, 420f, 760f, 0.5f, decay = 12f),
                ToneSynth.Voice(ToneSynth.Wave.SINE, 840f, 1240f, 0.25f, decay = 16f)
            )
        ),

        GameSound.AGENT_UPGRADED to Recipe(
            duration = 0.32f,
            voices = listOf(
                ToneSynth.Voice(ToneSynth.Wave.TRIANGLE, 520f, 1040f, 0.45f, decay = 8f),
                ToneSynth.Voice(ToneSynth.Wave.SINE, 780f, 1560f, 0.3f, decay = 10f)
            )
        ),

        GameSound.AGENT_SOLD to Recipe(
            duration = 0.22f,
            voices = listOf(
                ToneSynth.Voice(ToneSynth.Wave.TRIANGLE, 700f, 300f, 0.42f, decay = 12f)
            )
        ),

        // Silent (gain 0, so it is never played). Owner, 2026-09-27: "I
        // seriously dont want constant loud sounds ticking non stop. They need
        // to be soft and calming." Every hit already flashes on the board;
        // a sound for each one was only ever ticking. It keeps a recipe so the
        // table stays complete and it can be brought back by raising the gain.
        GameSound.PACKET_HIT to Recipe(
            duration = 0.06f,
            voices = listOf(
                ToneSynth.Voice(ToneSynth.Wave.SINE, 330f, amplitude = 0.3f, decay = 40f, attack = 0.01f)
            ),
            gain = 0f
        ),

        // A soft, low, round note, like a felt mallet on a marimba: pure sine,
        // swelling in over 15 ms instead of striking, and fading out slowly.
        // Played quietly and at a random step of a pentatonic scale (see
        // [pitchVariants]), a wave of kills sounds like wind chimes.
        GameSound.PACKET_DESTROYED to Recipe(
            duration = 0.42f,
            voices = listOf(
                ToneSynth.Voice(ToneSynth.Wave.SINE, 330f, amplitude = 0.55f, decay = 8f, attack = 0.015f),
                // A faint octave on top gives it a little warmth; it fades first.
                ToneSynth.Voice(ToneSynth.Wave.SINE, 660f, amplitude = 0.07f, decay = 16f, attack = 0.015f)
            ),
            gain = 0.2f
        ),

        // A boss is the payoff for the whole wave, so it gets a sequence
        // rather than a hit (owner, 2026-09-26: "dramatic, not just like a
        // normal or elite enemy kill"). A sharp crack, a deep sub-bass boom
        // under it, a system "power-down" wail falling two octaves, a second
        // detonation a beat later, and a long low rumble that outlasts the
        // shards. Nothing else in the game is longer than a second and a half
        // except GAME OVER, and nothing else goes this low.
        GameSound.BOSS_DESTROYED to Recipe(
            duration = 2.4f,
            voices = listOf(
                // The crack.
                ToneSynth.Voice(ToneSynth.Wave.NOISE, 1f, 1f, 0.55f, decay = 7f),
                ToneSynth.Voice(ToneSynth.Wave.SQUARE, 900f, 220f, 0.22f, decay = 14f),
                // The boom.
                ToneSynth.Voice(ToneSynth.Wave.SINE, 95f, 28f, 0.6f, decay = 1.4f),
                // The power-down.
                ToneSynth.Voice(ToneSynth.Wave.SQUARE, 640f, 70f, 0.2f, decay = 1.6f),
                ToneSynth.Voice(ToneSynth.Wave.TRIANGLE, 320f, 35f, 0.3f, decay = 1.5f),
                // The second detonation.
                ToneSynth.Voice(ToneSynth.Wave.NOISE, 1f, 1f, 0.42f, decay = 4.5f, delay = 0.26f),
                ToneSynth.Voice(ToneSynth.Wave.SQUARE, 210f, 45f, 0.3f, decay = 3.2f, delay = 0.26f),
                // The rumble.
                ToneSynth.Voice(ToneSynth.Wave.SINE, 48f, 30f, 0.4f, decay = 0.9f, delay = 0.45f),
                ToneSynth.Voice(ToneSynth.Wave.NOISE, 1f, 1f, 0.12f, decay = 1.3f, delay = 0.45f)
            )
        ),

        GameSound.BOSS_WARNING to Recipe(
            duration = 0.70f,
            voices = listOf(
                ToneSynth.Voice(ToneSynth.Wave.SQUARE, 240f, 500f, 0.40f, decay = 2.6f),
                ToneSynth.Voice(ToneSynth.Wave.SINE, 120f, 250f, 0.35f, decay = 2.2f)
            )
        ),

        GameSound.SERVER_DAMAGE to Recipe(
            duration = 0.30f,
            voices = listOf(
                ToneSynth.Voice(ToneSynth.Wave.SQUARE, 320f, 90f, 0.5f, decay = 9f),
                ToneSynth.Voice(ToneSynth.Wave.NOISE, 1f, 1f, 0.22f, decay = 14f)
            )
        ),

        GameSound.WAVE_START to Recipe(
            duration = 0.30f,
            voices = listOf(
                ToneSynth.Voice(ToneSynth.Wave.TRIANGLE, 300f, 620f, 0.42f, decay = 7f),
                ToneSynth.Voice(ToneSynth.Wave.SINE, 600f, 900f, 0.2f, decay = 9f)
            )
        ),

        GameSound.WAVE_CLEARED to Recipe(
            duration = 0.45f,
            voices = listOf(
                ToneSynth.Voice(ToneSynth.Wave.TRIANGLE, 520f, 1040f, 0.42f, decay = 5.5f),
                ToneSynth.Voice(ToneSynth.Wave.SINE, 1040f, 1560f, 0.24f, decay = 6.5f)
            )
        ),

        GameSound.UNLOCK to Recipe(
            duration = 0.75f,
            voices = listOf(
                ToneSynth.Voice(ToneSynth.Wave.TRIANGLE, 440f, 1320f, 0.45f, decay = 3.2f),
                ToneSynth.Voice(ToneSynth.Wave.SINE, 880f, 1760f, 0.3f, decay = 3.6f)
            )
        ),

        GameSound.INSUFFICIENT to Recipe(
            duration = 0.20f,
            voices = listOf(
                ToneSynth.Voice(ToneSynth.Wave.SQUARE, 200f, 140f, 0.4f, decay = 13f)
            )
        ),

        GameSound.GAME_OVER to Recipe(
            duration = 1.30f,
            voices = listOf(
                ToneSynth.Voice(ToneSynth.Wave.SQUARE, 380f, 60f, 0.45f, decay = 2.0f),
                ToneSynth.Voice(ToneSynth.Wave.SINE, 190f, 40f, 0.45f, decay = 1.7f),
                ToneSynth.Voice(ToneSynth.Wave.NOISE, 1f, 1f, 0.16f, decay = 3.0f)
            )
        )
    )
}
