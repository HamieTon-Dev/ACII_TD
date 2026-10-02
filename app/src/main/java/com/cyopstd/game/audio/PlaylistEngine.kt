package com.cyopstd.game.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Plays a level's supplied music, one variant after another, forever.
 *
 * The generated tracks use [MusicEngine], which hands a single file to a
 * MediaPlayer with `isLooping` set and never thinks about it again. That is
 * not enough here: a level's music is *two* files that must alternate, and
 * `isLooping` can only repeat the one it was given.
 *
 * So each variant plays to its end and the completion handler starts the next
 * one. There is a fraction of a second between them while the next player
 * prepares. That is deliberate rather than overlooked — `setNextMediaPlayer`
 * would close the gap, but it also means holding two decoders open for the
 * whole of every track, and these are two separate pieces of music rather than
 * a seamless loop, so the join is a bar line and not a splice.
 *
 * Everything tolerates failure. A device that will not decode the files plays
 * the level silently and tells [onUnavailable], which is how [AudioEngine]
 * knows to fall back to the track it can always render itself.
 */
class PlaylistEngine(
    private val context: Context,
    /** Raw resources, played in this order and then round again. */
    private val variants: List<Int>,
    /** For the log. */
    private val name: String,
    /** Which of [variants] to begin with. */
    startIndex: Int = 0,
    /** Called once if this device cannot play the supplied files at all. */
    private val onUnavailable: () -> Unit = {},
    /** Told the index of each track as it loads (the music player shows it). */
    private val onTrackChanged: (Int) -> Unit = {}
) : BackgroundTrack {

    /** A level's rotation: its own two renders and the two any-level tracks. */
    constructor(context: Context, music: LevelMusic, onUnavailable: () -> Unit = {}) :
        this(context, music.rotation, music.name, 0, onUnavailable)

    private val lock = Any()
    private var player: MediaPlayer? = null

    /** Which of [LevelMusic.variants] is loaded, or about to be. */
    private var index = startIndex.coerceIn(0, (variants.size - 1).coerceAtLeast(0))

    /** Which of the variants is loaded, or about to be. */
    val currentIndex: Int get() = synchronized(lock) { index }

    @Volatile
    private var wantPlaying = false

    override val wantsToPlay: Boolean get() = wantPlaying

    @Volatile
    private var volume = 0.5f

    @Volatile
    private var preparing = false

    /** Told once, not once per track: a broken decoder stays broken. */
    @Volatile
    private var reportedUnavailable = false

    /** The variant currently loaded. Exposed for tests; nothing else needs it. */
    val currentVariant: Int get() = synchronized(lock) { variants[index] }

    override fun prepare(scope: CoroutineScope) {
        synchronized(lock) {
            if (player != null || preparing) return
            preparing = true
        }
        scope.launch(Dispatchers.IO) { load(scope, variants[index]) }
    }

    private fun load(scope: CoroutineScope, resId: Int) {
        val prepared = open(resId)
        if (prepared == null) {
            synchronized(lock) { preparing = false }
            if (!reportedUnavailable) {
                reportedUnavailable = true
                onUnavailable()
            }
            return
        }

        prepared.setOnCompletionListener { finished ->
            // Advance on the thread the callback arrives on, then load the
            // next one off it: opening a file is disk work and this callback
            // runs on the player's looper.
            synchronized(lock) {
                if (player !== finished) return@setOnCompletionListener
                player = null
                index = (index + 1) % variants.size
            }
            safely("release") {
                finished.setOnCompletionListener(null)
                finished.release()
            }
            val next = synchronized(lock) {
                preparing = true
                variants[index]
            }
            scope.launch(Dispatchers.IO) { load(scope, next) }
        }

        onTrackChanged(synchronized(lock) { index })
        synchronized(lock) {
            player = prepared
            preparing = false
            applyVolume(prepared)
            if (wantPlaying && volume > MIN_AUDIBLE) safely("start") { prepared.start() }
        }
    }

    private fun open(resId: Int): MediaPlayer? = try {
        val descriptor = context.resources.openRawResourceFd(resId)
            ?: throw IllegalStateException("no descriptor for $resId")
        descriptor.use { fd ->
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
                isLooping = false
                prepare()
            }
        }
    } catch (error: Exception) {
        Log.w(TAG, "Could not open $name variant $resId", error)
        null
    }

    override fun start() {
        wantPlaying = true
        synchronized(lock) {
            val active = player ?: return
            if (volume <= MIN_AUDIBLE || active.isPlaying) return
            safely("start") { active.start() }
        }
    }

    override fun pause() {
        wantPlaying = false
        synchronized(lock) {
            val active = player ?: return
            if (!active.isPlaying) return
            safely("pause") { active.pause() }
        }
    }

    override fun setVolume(value: Float) {
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
        // The same trim the generated tracks get, so turning the music up does
        // not mean something different on one level than on another.
        val level = (volume * MUSIC_TRIM).coerceIn(0f, 1f)
        safely("volume") { target.setVolume(level, level) }
    }

    override fun release() {
        wantPlaying = false
        synchronized(lock) {
            val active = player ?: return
            safely("release") {
                active.setOnCompletionListener(null)
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
            Log.w(TAG, "Level music $what failed", error)
        }
    }

    private companion object {
        const val TAG = "CyOpsLevelMusic"
    }
}
