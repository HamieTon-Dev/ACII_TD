package com.cyopstd.game.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log
import com.cyopstd.game.core.GameMode
import com.cyopstd.game.engine.GameSound
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

/**
 * The track a mode plays under.
 *
 * An exhaustive `when` rather than a field on [GameMode] or a lookup by name:
 * adding a mode then has to answer this question, and the compiler is what
 * asks. It lives in the audio package because audio may know about the game,
 * and the game should not have to know about audio.
 *
 * The second map's track, [ChiptuneComposer.Track.BOTTLE_DRIVE], is composed
 * and rendering already but has nothing to select it yet — the map it belongs
 * to is not built. It becomes audible the moment there is a mode or a map to
 * name here.
 */
fun trackForMode(mode: GameMode): ChiptuneComposer.Track = when (mode) {
    GameMode.STANDARD -> ChiptuneComposer.Track.GAME
    GameMode.HACK_AI -> ChiptuneComposer.Track.BOTTLE
}

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
     * The menu's own track.
     *
     * A separate engine rather than one that reloads: switching screens is
     * frequent and a MediaPlayer that has to re-prepare a multi-megabyte file
     * each time would stutter the transition. A prepared player costs a few
     * megabytes of cache and swaps instantly.
     */
    private val menuMusic = MusicEngine(context, ChiptuneComposer.Track.MENU)

    /**
     * One engine per match track, built the first time that track is asked for.
     *
     * Lazily, and that is the point: HACK:AI is behind wave 100 and most
     * players will never hear its track, so rendering six megabytes of it on
     * every install would be work done for nobody. The standard track is
     * prepared up front because every player hears it.
     */
    private val matchMusic = HashMap<ChiptuneComposer.Track, MusicEngine>()

    /** Kept so a track asked for later can still be prepared off the main thread. */
    @Volatile
    private var audioScope: CoroutineScope? = null

    /** Which track should be playing. */
    private var inMatch = false
    private var matchTrack: ChiptuneComposer.Track = ChiptuneComposer.Track.GAME

    private fun engineFor(track: ChiptuneComposer.Track): MusicEngine =
        synchronized(matchMusic) {
            matchMusic.getOrPut(track) {
                MusicEngine(context, track).also { engine ->
                    engine.setVolume(musicVolume)
                    audioScope?.let(engine::prepare)
                }
            }
        }

    fun initialize(scope: CoroutineScope) {
        audioScope = scope
        engineFor(ChiptuneComposer.Track.GAME).prepare(scope)
        menuMusic.prepare(scope)
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

    /**
     * Chooses the track for where the player is.
     *
     * Only the chosen one plays; the other is paused rather than stopped, so
     * returning to the menu mid-track picks the menu music up where it was
     * instead of restarting it.
     */
    /**
     * Enter or leave a match, and say which track the match wants.
     *
     * The track is the caller's decision rather than something derived here:
     * today it comes from the game mode, and when the second map lands it may
     * come from the map instead. Either way this only has to switch players.
     */
    fun setInMatch(
        value: Boolean,
        track: ChiptuneComposer.Track = ChiptuneComposer.Track.GAME
    ) {
        val trackChanged = value && track != matchTrack
        if (inMatch == value && !trackChanged) return

        // Whatever was playing stops, including a *different* match track: a
        // player who finishes a HACK:AI run and starts a standard one must not
        // end up with two pieces of music at once.
        if (trackChanged || !value) pauseMatchTracks()

        inMatch = value
        if (value) matchTrack = track
        if (musicVolume <= 0.01f) return

        if (value) {
            menuMusic.pause()
            engineFor(matchTrack).start()
        } else {
            menuMusic.start()
        }
    }

    private fun pauseMatchTracks() {
        synchronized(matchMusic) { matchMusic.values.toList() }.forEach { it.pause() }
    }

    /** Starts (or resumes) whichever track belongs to the current screen. */
    fun startMusic() {
        if (musicVolume <= 0.01f) return
        if (inMatch) engineFor(matchTrack).start() else menuMusic.start()
    }

    fun stopMusic() {
        pauseMatchTracks()
        menuMusic.pause()
    }

    fun applyVolumes(music: Float, sfx: Float) {
        musicVolume = music
        sfxVolume = sfx
        synchronized(matchMusic) { matchMusic.values.toList() }.forEach { it.setVolume(music) }
        menuMusic.setVolume(music)
    }

    fun release() {
        synchronized(matchMusic) {
            matchMusic.values.forEach { it.release() }
            matchMusic.clear()
        }
        menuMusic.release()
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
