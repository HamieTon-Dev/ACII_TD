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
 * Plays the synthesized effect bank through a SoundPool.
 *
 * Sounds are generated once on a background thread at startup, written to the
 * app's cache directory as tiny WAV files, and loaded into the pool. Nothing is
 * ever downloaded and nothing is bundled, so there is no audio licensing
 * surface at all.
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
     * Ambient "network hum" loop, built from the same synth. It stands in for a
     * music track without shipping one: a slow, low drone that suits a SOC wall
     * display and costs a few kilobytes of cache.
     */
    private var ambientStreamId: Int = 0
    private var ambientSoundId: Int = -1

    fun initialize(scope: CoroutineScope) {
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

        val ambientFile = File(dir, "ambient_hum.wav")
        try {
            if (!ambientFile.exists() || ambientFile.length() < 64L) {
                ambientFile.writeBytes(
                    ToneSynth.renderWav(SoundBank.AMBIENT.duration, SoundBank.AMBIENT.voices)
                )
            }
            ambientSoundId = pool.load(ambientFile.absolutePath, 0)
        } catch (error: Exception) {
            Log.w(TAG, "Failed to prepare ambient loop", error)
        }
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

    fun startAmbient() {
        if (!ready || musicVolume <= 0.01f) return
        val pool = soundPool ?: return
        if (ambientSoundId < 0 || ambientSoundId !in loaded) return
        if (ambientStreamId != 0) return
        try {
            val volume = (musicVolume * 0.30f).coerceIn(0f, 1f)
            ambientStreamId = pool.play(ambientSoundId, volume, volume, 0, -1, 1f)
        } catch (error: Exception) {
            Log.w(TAG, "Could not start ambient loop", error)
        }
    }

    fun stopAmbient() {
        val pool = soundPool ?: return
        if (ambientStreamId == 0) return
        try {
            pool.stop(ambientStreamId)
        } catch (error: Exception) {
            Log.w(TAG, "Could not stop ambient loop", error)
        }
        ambientStreamId = 0
    }

    fun applyVolumes(music: Float, sfx: Float) {
        musicVolume = music
        sfxVolume = sfx
        val pool = soundPool
        if (pool != null && ambientStreamId != 0) {
            val volume = (music * 0.30f).coerceIn(0f, 1f)
            try {
                pool.setVolume(ambientStreamId, volume, volume)
            } catch (error: Exception) {
                Log.w(TAG, "Could not adjust ambient volume", error)
            }
            if (music <= 0.01f) stopAmbient()
        } else if (music > 0.01f) {
            startAmbient()
        }
    }

    fun release() {
        stopAmbient()
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
