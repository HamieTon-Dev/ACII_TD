package com.packetbastion.asciidefense.engine

/** Where a run currently sits in the start-wave / fight / breathe loop. */
enum class RunPhase {
    /** Between waves: the player may deploy, upgrade and sell freely. */
    PREPARING,

    /** !!! INTRUSION ALERT !!! banner before a boss wave begins. */
    BOSS_WARNING,

    /** Packets are inbound. */
    IN_WAVE,

    /** CORE-SERVER integrity hit zero. */
    GAME_OVER
}
