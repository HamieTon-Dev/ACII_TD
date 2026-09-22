package com.packetbastion.asciidefense.engine

/**
 * Engine-emitted cues. The engine never touches SoundPool or the Vibrator
 * directly — it raises one of these and whoever is listening decides whether the
 * player's settings allow it to be heard or felt.
 */
enum class GameSound {
    UI_CLICK,
    AGENT_PLACED,
    AGENT_UPGRADED,
    AGENT_SOLD,
    PACKET_HIT,
    PACKET_DESTROYED,
    BOSS_DESTROYED,
    BOSS_WARNING,
    SERVER_DAMAGE,
    WAVE_START,
    WAVE_CLEARED,
    UNLOCK,
    INSUFFICIENT,
    GAME_OVER
}

enum class HapticCue {
    LIGHT,
    MEDIUM,
    HEAVY,
    BOSS_ALERT,
    GAME_OVER
}

/** Reported back to the UI so it can show INSUFFICIENT CRYPTO etc. */
enum class PlacementResult {
    SUCCESS,
    INSUFFICIENT_CRYPTO,
    NODE_OCCUPIED,
    NODE_INVALID,
    AGENT_LOCKED,
    NO_CAPACITY
}
