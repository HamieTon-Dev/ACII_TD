package com.cyopstd.game.audio

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.cyopstd.game.engine.HapticCue

/**
 * Subtle vibration for the handful of moments that genuinely deserve one: a boss
 * inbound, the server taking a hit, a milestone upgrade, and the run ending.
 * Nothing routine buzzes — over-used haptics are worse than none.
 */
class HapticEngine(context: Context) {

    private val vibrator: Vibrator? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE)
                as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    } catch (error: Exception) {
        Log.w(TAG, "No vibrator available", error)
        null
    }

    @Volatile
    var enabled: Boolean = true

    /** Rate-limit so a busy battlefield cannot turn the phone into a buzzer. */
    private var lastFiredAt: Long = 0L

    fun fire(cue: HapticCue) {
        if (!enabled) return
        val device = vibrator ?: return
        if (!device.hasVibrator()) return

        val now = System.currentTimeMillis()
        val minGap = if (cue == HapticCue.LIGHT) LIGHT_MIN_GAP_MS else STRONG_MIN_GAP_MS
        if (now - lastFiredAt < minGap) return
        lastFiredAt = now

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                device.vibrate(effectFor(cue))
            } else {
                @Suppress("DEPRECATION")
                device.vibrate(legacyPattern(cue), -1)
            }
        } catch (error: Exception) {
            Log.w(TAG, "Vibration failed", error)
        }
    }

    private fun effectFor(cue: HapticCue): VibrationEffect = when (cue) {
        HapticCue.LIGHT -> VibrationEffect.createOneShot(14, 70)
        HapticCue.MEDIUM -> VibrationEffect.createOneShot(30, 140)
        HapticCue.HEAVY -> VibrationEffect.createOneShot(60, 200)
        HapticCue.BOSS_ALERT -> VibrationEffect.createWaveform(
            longArrayOf(0, 55, 90, 55, 90, 110),
            intArrayOf(0, 180, 0, 180, 0, 230),
            -1
        )
        HapticCue.GAME_OVER -> VibrationEffect.createWaveform(
            longArrayOf(0, 180, 110, 320),
            intArrayOf(0, 200, 0, 255),
            -1
        )
    }

    private fun legacyPattern(cue: HapticCue): LongArray = when (cue) {
        HapticCue.LIGHT -> longArrayOf(0, 14)
        HapticCue.MEDIUM -> longArrayOf(0, 30)
        HapticCue.HEAVY -> longArrayOf(0, 60)
        HapticCue.BOSS_ALERT -> longArrayOf(0, 55, 90, 55, 90, 110)
        HapticCue.GAME_OVER -> longArrayOf(0, 180, 110, 320)
    }

    private companion object {
        const val TAG = "CyOpsHaptics"
        const val LIGHT_MIN_GAP_MS = 90L
        const val STRONG_MIN_GAP_MS = 140L
    }
}
