package com.packetbastion.asciidefense.engine

import com.packetbastion.asciidefense.core.Balance
import com.packetbastion.asciidefense.model.Effect
import com.packetbastion.asciidefense.model.EffectKind
import kotlin.random.Random

/**
 * Short-lived ASCII flourishes. Effects are pooled and capped, and in battery
 * saver mode the purely decorative ones are dropped before they are ever
 * created — gameplay-critical text (wave banners, crypto gains) always survives.
 */
class EffectSystem(private val engine: GameEngine, private val random: Random) {

    fun update(dt: Float) {
        for (effect in engine.effects.items) {
            if (!effect.active) continue
            effect.age += dt
            effect.y += effect.velocityY * dt
            if (effect.age >= effect.lifetime) effect.reset()
        }
    }

    fun spawnHit(x: Float, y: Float, critical: Boolean) {
        if (engine.batterySaver && !critical) return
        val effect = engine.effects.obtain() ?: return
        effect.reset()
        effect.active = true
        effect.kind = EffectKind.HIT
        effect.x = x + random.nextFloat() * 10f - 5f
        effect.y = y + random.nextFloat() * 10f - 5f
        effect.lifetime = 0.18f
        effect.colorArgb = if (critical) GameEngine.COLOR_WARNING else GameEngine.COLOR_NEUTRAL
        effect.scale = if (critical) 1.3f else 1f
        effect.text = if (critical) "**" else "*"
    }

    fun spawnDeath(x: Float, y: Float, boss: Boolean) {
        val effect = engine.effects.obtain() ?: return
        effect.reset()
        effect.active = true
        effect.kind = if (boss) EffectKind.BOSS_DEATH else EffectKind.DEATH
        effect.x = x
        effect.y = y
        effect.lifetime =
            if (boss) Balance.BOSS_DEATH_EFFECT_LIFETIME else Balance.DEATH_EFFECT_LIFETIME
        effect.colorArgb = if (boss) GameEngine.COLOR_HOSTILE else GameEngine.COLOR_WARNING
        effect.scale = if (boss) 2.2f else 1f
    }

    fun spawnDamageNumber(x: Float, y: Float, amount: Float, critical: Boolean) {
        if (!engine.showDamageNumbers) return
        if (engine.batterySaver && !critical) return
        val effect = engine.effects.obtain() ?: return
        effect.reset()
        effect.active = true
        effect.kind = EffectKind.DAMAGE_NUMBER
        effect.x = x + random.nextFloat() * 18f - 9f
        effect.y = y - 12f
        effect.velocityY = -46f
        effect.lifetime = Balance.DAMAGE_NUMBER_LIFETIME
        effect.colorArgb = if (critical) GameEngine.COLOR_WARNING else GameEngine.COLOR_NEUTRAL
        effect.scale = if (critical) 1.35f else 1f
        val rounded = amount.toInt().coerceAtLeast(1)
        effect.text = if (critical) "$rounded!" else "$rounded"
    }

    fun spawnCryptoGain(x: Float, y: Float, amount: Int) {
        if (engine.batterySaver) return
        val effect = engine.effects.obtain() ?: return
        effect.reset()
        effect.active = true
        effect.kind = EffectKind.CRYPTO_GAIN
        effect.x = x
        effect.y = y
        effect.velocityY = -34f
        effect.lifetime = 0.7f
        effect.colorArgb = GameEngine.COLOR_CRYPTO
        effect.text = "◇$amount"
    }

    fun spawnText(
        x: Float,
        y: Float,
        text: String,
        color: Int,
        lifetime: Float,
        scale: Float = 1f
    ) {
        val effect = engine.effects.obtain() ?: return
        effect.reset()
        effect.active = true
        effect.kind = EffectKind.TEXT
        effect.x = x
        effect.y = y
        effect.velocityY = -12f
        effect.lifetime = lifetime
        effect.colorArgb = color
        effect.text = text
        effect.scale = scale
    }

    fun spawnEffect(
        kind: EffectKind,
        x: Float,
        y: Float,
        text: String,
        color: Int,
        lifetime: Float
    ) {
        val effect = engine.effects.obtain() ?: return
        effect.reset()
        effect.active = true
        effect.kind = kind
        effect.x = x
        effect.y = y
        effect.velocityY = -26f
        effect.lifetime = lifetime
        effect.colorArgb = color
        effect.text = text
    }

    /**
     * Occasional terminal chatter. Fires rarely on purpose — it is flavour, and
     * flavour stops being flavour the moment it becomes constant.
     */
    fun maybeSpawnTerminalMessage(x: Float, y: Float) {
        if (engine.batterySaver) return
        if (random.nextFloat() > 0.035f) return
        val message = TERMINAL_MESSAGES[random.nextInt(TERMINAL_MESSAGES.size)]
        spawnText(x, y - 26f, message, GameEngine.COLOR_SUCCESS, 0.95f, scale = 0.85f)
    }

    fun activeEffects(): List<Effect> = engine.effects.items.filter { it.active }

    companion object {
        val TERMINAL_MESSAGES = arrayOf(
            "PACKET DROPPED",
            "THREAT NEUTRALIZED",
            "CONNECTION RESET",
            "ACCESS DENIED",
            "PORT SECURED",
            "FIREWALL ACTIVE",
            "INTRUSION BLOCKED",
            "SIGNATURE MATCHED",
            "QUARANTINED",
            "HANDSHAKE REFUSED"
        )
    }
}
