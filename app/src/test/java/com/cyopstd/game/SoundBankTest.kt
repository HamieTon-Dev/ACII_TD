package com.cyopstd.game

import com.cyopstd.game.audio.SoundBank
import com.cyopstd.game.audio.ToneSynth
import com.cyopstd.game.engine.GameSound
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The effect table.
 *
 * The boss kill has to sound like an event, not like a louder packet: the
 * owner asked for it to be dramatic and unlike a normal or elite kill.
 */
class SoundBankTest {

    private fun recipe(sound: GameSound) = SoundBank.recipes.getValue(sound)

    @Test
    fun `every sound has a recipe`() {
        for (sound in GameSound.entries) assertTrue("$sound has no recipe", sound in SoundBank.recipes)
    }

    @Test
    fun `a boss kill is a long, layered sequence rather than a hit`() {
        val boss = recipe(GameSound.BOSS_DESTROYED)
        val kill = recipe(GameSound.PACKET_DESTROYED)
        assertTrue("boss kill is ${boss.duration}s", boss.duration >= 2f)
        assertTrue(boss.duration > kill.duration * 10)
        assertTrue("a boss kill needs more than one stage", boss.voices.any { it.delay > 0f })
        // The lowest thing in the game: a sub-bass boom nothing else reaches.
        val lowest = boss.voices.filter { it.wave != ToneSynth.Wave.NOISE }.minOf { minOf(it.startFreq, it.endFreq) }
        val othersLowest = SoundBank.recipes
            .filterKeys { it != GameSound.BOSS_DESTROYED }
            .values.flatMap { it.voices }
            .filter { it.wave != ToneSynth.Wave.NOISE }
            .minOf { minOf(it.startFreq, it.endFreq) }
        assertTrue("boss $lowest Hz vs others $othersLowest Hz", lowest < othersLowest)
    }

    @Test
    fun `a delayed voice is silent until its delay`() {
        val wav = ToneSynth.renderWav(
            0.5f, listOf(ToneSynth.Voice(ToneSynth.Wave.SINE, 440f, delay = 0.25f))
        )
        val silentBytes = (ToneSynth.SAMPLE_RATE * 0.24f).toInt() * 2
        for (b in 44 until 44 + silentBytes) assertEquals("sound before the delay", 0, wav[b].toInt())
        var loud = false
        for (b in 44 + silentBytes + 4000 until wav.size) if (wav[b].toInt() != 0) loud = true
        assertTrue("nothing after the delay", loud)
    }

    @Test
    fun `the constant combat sounds are soft, not harsh`() {
        // Owner: harsh even at 11% volume. Noise and square waves are what
        // made it harsh, so the sounds heard dozens of times a minute use
        // neither, and stay low in pitch.
        for (sound in listOf(GameSound.PACKET_HIT, GameSound.PACKET_DESTROYED)) {
            for (voice in recipe(sound).voices) {
                assertTrue("$sound uses ${voice.wave}",
                    voice.wave == ToneSynth.Wave.SINE || voice.wave == ToneSynth.Wave.TRIANGLE)
                assertTrue("$sound reaches ${voice.startFreq} Hz", maxOf(voice.startFreq, voice.endFreq) <= 700f)
            }
        }
    }

    @Test
    fun `hit and kill sounds cannot stack into a buzz`() {
        assertTrue(SoundBank.minIntervalSeconds(GameSound.PACKET_HIT) >= 0.05f)
        assertTrue(SoundBank.minIntervalSeconds(GameSound.PACKET_DESTROYED) >= 0.04f)
        // Nothing the player must never miss is rate limited.
        for (sound in listOf(GameSound.BOSS_DESTROYED, GameSound.BOSS_WARNING, GameSound.SERVER_DAMAGE,
            GameSound.GAME_OVER, GameSound.UI_CLICK)) {
            assertEquals(0f, SoundBank.minIntervalSeconds(sound), 0f)
        }
    }

    @Test
    fun `preview`() {
        // Not an assertion: the file a human listens to.
        File("build/previews").mkdirs()
        val boss = recipe(GameSound.BOSS_DESTROYED)
        File("build/previews/boss_destroyed.wav").writeBytes(ToneSynth.renderWav(boss.duration, boss.voices))
        val kill = recipe(GameSound.PACKET_DESTROYED)
        File("build/previews/packet_destroyed.wav").writeBytes(ToneSynth.renderWav(kill.duration, kill.voices))
    }
}
