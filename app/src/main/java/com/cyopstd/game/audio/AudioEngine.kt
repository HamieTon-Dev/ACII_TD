package com.cyopstd.game.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.SystemClock
import android.util.Log
import com.cyopstd.game.R
import com.cyopstd.game.core.GameMode
import com.cyopstd.game.engine.GameSound
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

/**
 * The generated track a mode falls back to.
 *
 * Since the owner supplied the level music, a match normally plays the files
 * in [LevelMusic]. This is what plays when it cannot: a level with no music of
 * its own yet, or a device whose decoder will not take the bundled files.
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
     * One playlist per level, built the first time that level is played.
     *
     * Lazily, and that is the point: a player who never reaches the second map
     * never pays to open its music.
     */
    private val levelMusic = HashMap<LevelMusic, PlaylistEngine>()

    /**
     * The generated tracks, kept as what plays when the supplied files cannot.
     *
     * Not dead weight and not a second music system: these render from code,
     * so they work on a device whose decoder refuses the bundled MP3s, and on
     * a level that has no music of its own yet. Built lazily for the same
     * reason the playlists are — HACK:AI is behind wave 100 and most players
     * will never hear its track.
     */
    private val matchMusic = HashMap<ChiptuneComposer.Track, MusicEngine>()

    /** Kept so a track asked for later can still be prepared off the main thread. */
    @Volatile
    private var audioScope: CoroutineScope? = null

    /** Which track should be playing. */
    private var inMatch = false
    private var matchTrack: ChiptuneComposer.Track = ChiptuneComposer.Track.GAME

    /** The level whose music the current match wants, if it has one. */
    private var matchLevel: LevelMusic? = null

    /** Set when this device will not play the supplied files at all. */
    @Volatile
    private var levelMusicUnavailable = false

    private fun playlistFor(level: LevelMusic): PlaylistEngine =
        synchronized(levelMusic) {
            levelMusic.getOrPut(level) {
                PlaylistEngine(context, level, onUnavailable = ::onLevelMusicUnavailable).also {
                    it.setVolume(musicLevel)
                    audioScope?.let(it::prepare)
                }
            }
        }

    /**
     * The supplied files would not open. Fall back to something we can render.
     *
     * Reached only on a device whose decoder refuses the bundled MP3s. It is
     * not a hypothetical — OEM decoders vary — and the alternative is a level
     * that is silently, permanently quiet.
     */
    private fun onLevelMusicUnavailable() {
        levelMusicUnavailable = true
        Log.w(TAG, "Level music will not play here; falling back to the generated track")
        if (inMatch && musicLevel > 0.01f) engineFor(matchTrack).start()
    }

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
        beginStartupFade(scope)
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
    /**
     * Show the menu, and play its music.
     *
     * Separate from [setInMatch] because that early-returns when the state has
     * not changed, and at launch it has not: `inMatch` starts false, so the
     * very first menu never asked anyone to start playing. Menu music only
     * ever began after a player had been into a match and come back out,
     * which is why it was silent on a fresh launch for every version of this
     * game so far.
     */
    fun enterMenu() {
        inMatch = false
        pauseMatchTracks()
        if (musicVolume <= 0.01f) return
        menuMusic.start()
    }

    /**
     * The machine coming up: played once, over the studio ident.
     *
     * The only audio file in the game. Everything else is synthesized, which
     * is a deliberate property -- no licensing surface, nothing to ship -- and
     * this is the one exception because the owner supplied it and owns it.
     * Played through its own MediaPlayer rather than the effect pool: two
     * seconds at 48kHz is far too long for a SoundPool, which is built for
     * 70-millisecond blips.
     */
    fun playBootChime() {
        if (bootChimePlayed || musicVolume <= 0.01f) return
        bootChimePlayed = true
        try {
            val player = MediaPlayer.create(context, R.raw.boot_chime) ?: return
            bootChime = player
            player.setVolume(musicVolume, musicVolume)
            player.setOnCompletionListener {
                it.release()
                if (bootChime === it) bootChime = null
            }
            player.start()
        } catch (error: Exception) {
            Log.w(TAG, "Boot chime would not play", error)
        }
    }

    /** Held so backgrounding the app silences it like everything else. */
    private var bootChime: MediaPlayer? = null

    /** Once per launch. It is an ident, not a UI sound. */
    private var bootChimePlayed = false

    fun setInMatch(
        value: Boolean,
        level: LevelMusic? = null,
        track: ChiptuneComposer.Track = ChiptuneComposer.Track.GAME
    ) {
        val changed = value && (track != matchTrack || level != matchLevel)
        if (inMatch == value && !changed) return

        // Whatever was playing stops, including a *different* match track: a
        // player who finishes a run on one level and starts another must not
        // end up with two pieces of music at once.
        if (changed || !value) pauseMatchTracks()

        inMatch = value
        if (value) {
            matchTrack = track
            matchLevel = level
        }
        if (musicVolume <= 0.01f) return

        if (value) {
            menuMusic.pause()
            startMatchTrack()
        } else {
            menuMusic.start()
        }
    }

    /**
     * The level's own music if there is any and this device will play it,
     * otherwise the generated track for the mode.
     */
    private fun startMatchTrack() {
        val level = matchLevel
        if (level != null && !levelMusicUnavailable) {
            playlistFor(level).start()
        } else {
            engineFor(matchTrack).start()
        }
    }

    private fun pauseMatchTracks() {
        matchEngines().forEach { it.pause() }
    }

    /** Every background track that belongs to a match, of either kind. */
    private fun matchEngines(): List<BackgroundTrack> =
        synchronized(levelMusic) { levelMusic.values.toList<BackgroundTrack>() } +
            synchronized(matchMusic) { matchMusic.values.toList<BackgroundTrack>() }

    /** Starts (or resumes) whichever track belongs to the current screen. */
    fun startMusic() {
        if (musicVolume <= 0.01f) return
        if (inMatch) startMatchTrack() else menuMusic.start()
    }

    /**
     * Silence everything, without forgetting which track belongs where.
     *
     * Called when the app goes to the background. It deliberately does *not*
     * touch `inMatch`: the player is coming back to the same screen they left,
     * and [startMusic] needs to know which one that was.
     */
    fun stopMusic() {
        pauseMatchTracks()
        menuMusic.pause()
        try {
            bootChime?.takeIf { it.isPlaying }?.pause()
        } catch (error: Exception) {
            Log.w(TAG, "Boot chime would not pause", error)
        }
    }

    /**
     * Whether anything has been asked to play.
     *
     * Exposed because "is the game making noise right now" is the whole
     * subject of a bug that shipped: backgrounding the app used to *start*
     * the menu music rather than stop it, and nothing in the suite could see
     * that.
     */
    val musicWanted: Boolean
        get() = menuMusic.wantsToPlay || matchEngines().any { it.wantsToPlay }

    /**
     * How many match tracks are currently asking to play. Should never be two.
     *
     * Exposed because "one piece of music at a time" is not something the
     * player can be asked to verify and is exactly the kind of thing that
     * breaks quietly when a second kind of track is added — which is what the
     * level playlists are.
     */
    val matchTracksWanting: Int get() = matchEngines().count { it.wantsToPlay }

    fun applyVolumes(music: Float, sfx: Float) {
        musicVolume = music
        sfxVolume = sfx
        applyMusicLevel()
    }

    // ------------------------------------------------------- the startup fade

    /**
     * How far through the launch fade the music is, 0 to 1.
     *
     * A **multiplier** on the player's setting rather than a level of its own.
     * That distinction is the whole feature: ramping an absolute volume would
     * quietly override somebody who turned music down to 20%, taking them to
     * full and back, which is the same bug as ignoring the setting outright.
     */
    @Volatile
    private var startupRamp = 0f

    /** The level actually being sent to the players, setting and ramp combined. */
    val musicLevel: Float get() = musicVolume * startupRamp

    private fun applyMusicLevel() {
        val level = musicLevel
        matchEngines().forEach { it.setVolume(level) }
        menuMusic.setVolume(level)
    }

    /**
     * Silence, then a slow rise, once per launch.
     *
     * The music used to arrive at full volume a fraction of a second after the
     * boot chime finished — the ident and the boot screen take about 3.9s
     * between them — and two sounds back to back with no gap read as one
     * interrupting the other.
     *
     * Driven off `elapsedRealtime`, so backgrounding the app mid-fade and
     * coming back does not restart the wait. The fade belongs to the launch,
     * and the launch already happened.
     */
    private fun beginStartupFade(scope: CoroutineScope) {
        if (startupFading) return
        startupFading = true
        val startedAt = SystemClock.elapsedRealtime()
        scope.launch {
            while (true) {
                val elapsed = (SystemClock.elapsedRealtime() - startedAt) / 1000f
                startupRamp = startupRampAt(elapsed)
                applyMusicLevel()
                if (startupRamp >= 1f) return@launch
                delay(FADE_TICK_MS)
            }
        }
    }

    private var startupFading = false

    fun release() {
        synchronized(levelMusic) {
            levelMusic.values.forEach { it.release() }
            levelMusic.clear()
        }
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

    companion object {
        private const val TAG = "CyOpsAudio"
        private const val MAX_STREAMS = 12
        private const val FADE_TICK_MS = 40L

        /** Nothing at all for this long after launch. */
        const val STARTUP_SILENCE_SECONDS = 5f

        /** Then a rise to the player's chosen level over this long. */
        const val STARTUP_FADE_SECONDS = 4f

        /**
         * The launch ramp at [seconds] after start, 0 to 1.
         *
         * A pure function so the shape can be asserted rather than watched:
         * silent for the whole delay, a straight rise, and pinned at 1
         * afterwards so nothing keeps recomputing it for the rest of the
         * session.
         */
        fun startupRampAt(seconds: Float): Float = when {
            seconds <= STARTUP_SILENCE_SECONDS -> 0f
            seconds >= STARTUP_SILENCE_SECONDS + STARTUP_FADE_SECONDS -> 1f
            else -> (seconds - STARTUP_SILENCE_SECONDS) / STARTUP_FADE_SECONDS
        }
    }
}
