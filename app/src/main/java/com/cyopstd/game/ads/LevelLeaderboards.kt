package com.cyopstd.game.ads

import com.cyopstd.game.core.GameMap
import com.cyopstd.game.core.GameMode
import com.cyopstd.game.core.Maps
import com.cyopstd.game.save.BoardKey

/**
 * The worldwide board for every level in every difficulty (owner, 2026-10-01:
 * "all per level and difficulty"): 10 levels × 3 difficulties = 30 Play
 * Console leaderboards.
 *
 * Kept here rather than in `secrets.properties` on purpose. A leaderboard id
 * is not a secret, since every copy of the app carries it, and thirty of them
 * as build secrets (and thirty GitHub Actions secrets) would be thirty chances
 * to mistype one where nobody can see it. An empty id means that board does
 * not exist yet: the level keeps its device board and nothing is posted.
 * `LEADERBOARDS.md` lists the names to create them under.
 */
object LevelLeaderboards {

    /** "mapId|modeId" to the Play Console id ("CgkI…"). */
    val ids: Map<String, String> = mapOf(
        "perimeter|standard" to "", // L1 NETWORK PERIMETER · NETWORK DEFENCE
        "perimeter|hack_ai" to "", // L1 NETWORK PERIMETER · HACK:AI
        "perimeter|kernel_mode" to "", // L1 NETWORK PERIMETER · KERNEL MODE
        "hugging_face|standard" to "", // L2 HUGGING-FACE · NETWORK DEFENCE
        "hugging_face|hack_ai" to "", // L2 HUGGING-FACE · HACK:AI
        "hugging_face|kernel_mode" to "", // L2 HUGGING-FACE · KERNEL MODE
        "neural_mesh|standard" to "", // L3 NEURAL-MESH · NETWORK DEFENCE
        "neural_mesh|hack_ai" to "", // L3 NEURAL-MESH · HACK:AI
        "neural_mesh|kernel_mode" to "", // L3 NEURAL-MESH · KERNEL MODE
        "duck_usb|standard" to "", // L4 DUCK-USB · NETWORK DEFENCE
        "duck_usb|hack_ai" to "", // L4 DUCK-USB · HACK:AI
        "duck_usb|kernel_mode" to "", // L4 DUCK-USB · KERNEL MODE
        "ddos|standard" to "", // L5 DDoS · NETWORK DEFENCE
        "ddos|hack_ai" to "", // L5 DDoS · HACK:AI
        "ddos|kernel_mode" to "", // L5 DDoS · KERNEL MODE
        "trident|standard" to "", // L6 MIRAI · NETWORK DEFENCE
        "trident|hack_ai" to "", // L6 MIRAI · HACK:AI
        "trident|kernel_mode" to "", // L6 MIRAI · KERNEL MODE
        "spiral|standard" to "", // L7 RING-ZERO · NETWORK DEFENCE
        "spiral|hack_ai" to "", // L7 RING-ZERO · HACK:AI
        "spiral|kernel_mode" to "", // L7 RING-ZERO · KERNEL MODE
        "zigzag|standard" to "", // L8 WANNACRY · NETWORK DEFENCE
        "zigzag|hack_ai" to "", // L8 WANNACRY · HACK:AI
        "zigzag|kernel_mode" to "", // L8 WANNACRY · KERNEL MODE
        "helix|standard" to "", // L9 HONEYPOT · NETWORK DEFENCE
        "helix|hack_ai" to "", // L9 HONEYPOT · HACK:AI
        "helix|kernel_mode" to "", // L9 HONEYPOT · KERNEL MODE
        "braid|standard" to "", // L10 HEARTBLEED · NETWORK DEFENCE
        "braid|hack_ai" to "", // L10 HEARTBLEED · HACK:AI
        "braid|kernel_mode" to "", // L10 HEARTBLEED · KERNEL MODE
    )

    /** What to call the board in Play Console, e.g. "MIRAI · HACK:AI". */
    fun boardName(map: GameMap, mode: GameMode): String =
        map.displayName.removePrefix("\uD83E\uDD86 ") + " \u00B7 " + mode.runName

    /** Every configured per-level board, or none when the build has no games project. */
    fun boards(gamesConfigured: Boolean, raw: Map<String, String> = ids): Map<BoardKey, String> {
        if (!gamesConfigured) return emptyMap()
        val out = HashMap<BoardKey, String>()
        for (map in Maps.all) for (mode in GameMode.entries) {
            val id = raw["${map.id}|${mode.id}"]?.trim().orEmpty()
            if (id.isNotEmpty()) out[BoardKey(mode, map.id)] = id
        }
        return out
    }
}
