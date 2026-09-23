package com.cyopstd.game.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log
import com.cyopstd.game.engine.GameSound
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

/**
 * Plays the synthesized effect bank through a SoundPool, and owns the
 * background music.
 *
 * Sounds are generated once on a background thread at startup, written to the
 * app's cache directory as tiny WAV files, and loaded into the pool. Nothing is
 * ever downloaded and nothing is bundled, so there is no audio licensing
 * surface at all.
 *
 * The music takes a different path — see [MusicEngine] — because a long track
 * and a 70-millisecond blip want completely different playback machinery.
 *
 * Every entry point is null- and failure-tolerant: a device that cannot give us
 * a SoundPool simply plays a silent game.
 */
class AudioEngine(private val context: Context) {

    private var soundPool: SoundPool? = null
    private val soundIds = HashMap<GameSound, Int>()
    private val loaded = HashSet<Int>()

    @Volatile
    var sfxVolume: Float = 0.8f

    @Volatile
    var musicVolume: Float = 0.5f

    @Volatile
    private var ready: Boolean = false

    /**
     * The background track. Synthesized rather than shipped, like everything
     * else here, but long-form and streamed from disk instead of looped out of
     * a sample pool.
     */
    private val music = MusicEngine(context)

    fun initialize(scope: CoroutineScope) {
        music.prepare(scope)
        if (soundPool != null) return
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val pool = try {
            SoundPool.Builder()
                .setMaxStreams(MAX_STREAMS)
                .setAudioAttributes(attributes)
                .build()
        } catch (error: Exception) {
            Log.w(TAG, "SoundPool unavailable; running silent", error)
            return
        }

        pool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) loaded.add(sampleId)
        }
        soundPool = pool

        scope.launch(Dispatchers.IO) {
            try {
                buildBank(pool)
                ready = true
            } catch (error: Exception) {
                Log.w(TAG, "Could not build the sound bank; running silent", error)
            }
        }
    }

    private fun buildBank(pool: SoundPool) {
        val dir = File(context.cacheDir, "sfx").apply { mkdirs() }
        for ((sound, recipe) in SoundBank.recipes) {
            val file = File(dir, "${sound.name.lowercase()}.wav")
            try {
                if (!file.exists() || file.length() < 64L) {
                    file.writeBytes(ToneSynth.renderWav(recipe.duration, recipe.voices))
                }
                soundIds[sound] = pool.load(file.absolutePath, 1)
            } catch (error: Exception) {
                Log.w(TAG, "Failed to prepare ${sound.name}", error)
            }
        }

        // An earlier version looped a two-second drone out of the sound pool.
        // Clear it out so upgrading players do not keep the file forever.
        File(dir, "ambient_hum.wav").delete()
    }

    fun play(sound: GameSound) {
        if (!ready || sfxVolume <= 0.01f) return
        val pool = soundPool ?: return
        val id = soundIds[sound] ?: return
        if (id !in loaded) return
        val volume = (sfxVolume * SoundBank.recipes[sound]?.gain.orDefault()).coerceIn(0f, 1f)
        try {
            pool.play(id, volume, volume, 1, 0, 1f)
        } catch (error: Exception) {
            Log.w(TAG, "Playback failed for ${sound.name}", error)
        }
    }

    private fun Float?.orDefault(): Float = this ?: 1f

    /** Starts (or resumes) the background track, if music is turned up. */
    fun startMusic() {
        if (musicVolume <= 0.01f) return
        music.start()
    }

    fun stopMusic() {
        music.pause()
    }

    fun applyVolumes(music: Float, sfx: Float) {
        musicVolume = music
        sfxVolume = sfx
        this.music.setVolume(music)
    }

    fun release() {
        music.release()
        try {
            soundPool?.release()
        } catch (error: Exception) {
            Log.w(TAG, "SoundPool release failed", error)
        }
        soundPool = null
        soundIds.clear()
        loaded.clear()
        ready = false
    }

    private companion object {
        const val TAG = "CyOpsAudio"
        const val MAX_STREAMS = 12
    }
}
