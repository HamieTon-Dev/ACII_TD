package com.cyopstd.game.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * Plays the background track written by [ChiptuneComposer].
 *
 * This deliberately does *not* use the SoundPool that carries the sound
 * effects. SoundPool decodes a clip fully into memory and is meant for short
 * one-shots; a three-and-a-half minute track belongs on a MediaPlayer, which
 * streams it from disk and loops it natively without a gap.
 *
 * The track is rendered once into the cache directory and reused forever after,
 * so the cost is a second or two on first launch and nothing on every launch
 * since. Every entry point tolerates failure: a device that cannot give us a
 * MediaPlayer simply plays without music.
 */
class MusicEngine(private val context: Context) {

    private val lock = Any()
    private var player: MediaPlayer? = null

    /** What the game has asked for, which may arrive before the track is ready. */
    @Volatile
    private var wantPlaying = false

    @Volatile
    private var volume = 0.5f

    @Volatile
    private var preparing = false

    /**
     * Render (if needed) and prepare the track on a background thread. Safe to
     * call repeatedly; only the first call does any work.
     */
    fun prepare(scope: CoroutineScope) {
        synchronized(lock) {
            if (player != null || preparing) return
            preparing = true
        }
        scope.launch(Dispatchers.IO) {
            try {
                val file = ensureTrackFile()
                val prepared = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    setDataSource(file.absolutePath)
                    isLooping = true
                    prepare()
                }
                synchronized(lock) {
                    player = prepared
                    preparing = false
                    applyVolume(prepared)
                    if (wantPlaying && volume > MIN_AUDIBLE) safely("start") { prepared.start() }
                }
            } catch (error: Exception) {
                Log.w(TAG, "Could not prepare the music track; running without music", error)
                synchronized(lock) { preparing = false }
            }
        }
    }

    /**
     * Write the WAV if the cache does not already hold this version of it.
     * Renders to a temporary file first so a kill mid-render cannot leave a
     * truncated track that would fail to prepare on the next launch.
     */
    internal fun ensureTrackFile(): File {
        val dir = File(context.cacheDir, "music").apply { mkdirs() }
        val file = File(dir, "lofi_v${ChiptuneComposer.TRACK_VERSION}.wav")
        val expected = 44L + ChiptuneComposer.totalFrames * 2L

        // Drop renders from earlier versions of the arrangement, and any
        // partial file. Done before the early return below, so a cache left
        // untidy by an interrupted render is cleaned up on the next launch
        // rather than holding megabytes forever.
        dir.listFiles()?.forEach { if (it != file) it.delete() }

        if (file.exists() && file.length() == expected) return file

        val temp = File(dir, "lofi.partial")
        BufferedOutputStream(FileOutputStream(temp), 1 shl 16).use {
            ChiptuneComposer.writeWav(it)
        }
        if (!temp.renameTo(file)) {
            temp.copyTo(file, overwrite = true)
            temp.delete()
        }
        return file
    }

    fun start() {
        wantPlaying = true
        synchronized(lock) {
            val active = player ?: return
            if (volume <= MIN_AUDIBLE || active.isPlaying) return
            safely("start") { active.start() }
        }
    }

    /** Pauses rather than stops, so the track resumes where the player left it. */
    fun pause() {
        wantPlaying = false
        synchronized(lock) {
            val active = player ?: return
            if (!active.isPlaying) return
            safely("pause") { active.pause() }
        }
    }

    fun setVolume(value: Float) {
        volume = value.coerceIn(0f, 1f)
        synchronized(lock) {
            val active = player ?: return
            applyVolume(active)
            if (volume <= MIN_AUDIBLE) {
                if (active.isPlaying) safely("pause") { active.pause() }
            } else if (wantPlaying && !active.isPlaying) {
                safely("start") { active.start() }
            }
        }
    }

    private fun applyVolume(target: MediaPlayer) {
        // Music sits well under the effects: it is atmosphere, not the game.
        val level = (volume * MUSIC_TRIM).coerceIn(0f, 1f)
        safely("volume") { target.setVolume(level, level) }
    }

    fun release() {
        wantPlaying = false
        synchronized(lock) {
            val active = player ?: return
            safely("release") {
                active.stop()
                active.release()
            }
            player = null
        }
    }

    private inline fun safely(what: String, block: () -> Unit) {
        try {
            block()
        } catch (error: Exception) {
            Log.w(TAG, "Music $what failed", error)
        }
    }

    private companion object {
        const val TAG = "CyOpsMusic"
        const val MIN_AUDIBLE = 0.01f
        const val MUSIC_TRIM = 0.55f
    }
}
