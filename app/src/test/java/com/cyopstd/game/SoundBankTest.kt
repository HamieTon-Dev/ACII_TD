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
        assertTrue(boss.duration > kill.duration * 5)
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
    fun `the constant combat sounds are soft and calming`() {
        // Owner, 2026-09-27: harsh even at 11%, and "I seriously dont want
        // constant loud sounds ticking non stop. They need to be soft and
        // calming." So the hit is silent, and the kill is a quiet, round note.
        assertEquals("the hit must be silent", 0f, recipe(GameSound.PACKET_HIT).gain, 0f)

        val kill = recipe(GameSound.PACKET_DESTROYED)
        assertTrue("kill gain ${kill.gain}", kill.gain <= 0.25f)
        for (voice in kill.voices) {
            assertEquals("kill uses ${voice.wave}", ToneSynth.Wave.SINE, voice.wave)
            assertTrue("kill reaches ${voice.startFreq} Hz", maxOf(voice.startFreq, voice.endFreq) <= 700f)
            assertEquals("a note, not a sweep", voice.startFreq, voice.endFreq, 0f)
            assertTrue("kill strikes in ${voice.attack}s; it should swell in", voice.attack >= 0.01f)
        }
        // The main note, even at the top of the scale, stays below 600 Hz.
        val fundamental = kill.voices.maxBy { it.amplitude }.startFreq
        assertTrue(fundamental * SoundBank.pitchVariants(GameSound.PACKET_DESTROYED).max() < 600f)
    }

    @Test
    fun `kill notes vary within one scale`() {
        val rates = SoundBank.pitchVariants(GameSound.PACKET_DESTROYED)
        assertTrue(rates.size >= 4)
        assertTrue("SoundPool takes 0.5 to 2.0", rates.all { it in 0.5f..2f })
        assertEquals("everything else plays as rendered", listOf(1f), SoundBank.pitchVariants(GameSound.BOSS_DESTROYED))
    }

    @Test
    fun `the default attack renders exactly as before`() {
        // Every other effect, the boss kill included, must be untouched.
        val voice = ToneSynth.Voice(ToneSynth.Wave.SQUARE, 440f, decay = 5f)
        val explicit = voice.copy(attack = 0.004f)
        assertTrue(ToneSynth.renderWav(0.2f, listOf(voice)).contentEquals(ToneSynth.renderWav(0.2f, listOf(explicit))))
    }

    @Test
    fun `hit and kill sounds cannot stack into a buzz`() {
        assertTrue(SoundBank.minIntervalSeconds(GameSound.PACKET_HIT) >= 0.05f)
        assertTrue("at most about six kill notes a second",
            SoundBank.minIntervalSeconds(GameSound.PACKET_DESTROYED) >= 0.15f)
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
