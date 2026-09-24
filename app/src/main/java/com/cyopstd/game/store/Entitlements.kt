package com.cyopstd.game.store

import kotlinx.serialization.Serializable

/**
 * What the player owns.
 *
 * Deliberately a set of product id strings rather than a field per product:
 * adding a skin must not require a save migration, and an id that is no longer
 * in the catalog has to survive a round trip rather than being silently
 * dropped, or a player who bought something before it was renamed would lose
 * it. Everything derived from the set is computed, never stored.
 */
@Serializable
data class Entitlements(
    val ownedIds: Set<String> = emptySet()
) {
    fun owns(sku: Sku): Boolean = sku.id in ownedIds

    fun owns(id: String): Boolean = id in ownedIds

    val adsRemoved: Boolean get() = Sku.NO_ADS.id in ownedIds

    /**
     * Revives per run this player is entitled to, or null for the default.
     *
     * Null rather than the default number so that [com.cyopstd.game.core.Balance]
     * stays the single place the free entitlement is written down, and a
     * product only ever says what it *changes*.
     */
    val revivesPerRun: Int? get() = Sku.revivesPerRun(ownedIds)

    /**
     * True when a revive costs no ad.
     *
     * Deliberately not implied by [adsRemoved]: the owner's rule is that
     * REMOVE ADS buys freedom from ads the player did not ask for, and the
     * revive is one they did. Only a product that says it covers revives
     * covers revives.
     */
    val reviveAdsRemoved: Boolean get() = Sku.reviveAdsRemoved(ownedIds)
    val fifthSpeedUnlocked: Boolean get() = Sku.SPEED_5X.id in ownedIds
    val spectrumAgents: Boolean get() = Sku.SKIN_AGENTS_SPECTRUM.id in ownedIds

    /** Core-server skins owned, in catalog order. */
    val coreSkins: List<Sku> get() = Sku.coreSkins.filter { it.id in ownedIds }

    /** Living backgrounds owned, in catalog order. */
    val backgrounds: List<Sku> get() = Sku.backgrounds.filter { it.id in ownedIds }

    fun plus(sku: Sku): Entitlements =
        copy(ownedIds = ownedIds + Sku.unlockedBy(sku))

    fun plusAll(skus: Iterable<Sku>): Entitlements =
        skus.fold(this) { acc, sku -> acc.plus(sku) }
}

/** Which cosmetic the player has *selected*, out of the ones they own. */
@Serializable
data class CosmeticChoice(
    /** Product id of the chosen CORE-SERVER skin, or null for the default. */
    val coreSkinId: String? = null,
    /** Product id of the chosen living background, or null for the default. */
    val backgroundId: String? = null,
    /** Spectrum agent colours on, when owned. */
    val spectrumAgents: Boolean = true
) {
    /**
     * Drops any selection the player does not (or no longer) owns.
     *
     * A refund, a restore onto a different account, or a catalog change can all
     * leave a choice pointing at something unowned. Resolving it here means the
     * renderer can trust the choice without checking entitlements itself.
     */
    fun resolvedAgainst(entitlements: Entitlements): CosmeticChoice = copy(
        coreSkinId = coreSkinId?.takeIf { entitlements.owns(it) },
        backgroundId = backgroundId?.takeIf { entitlements.owns(it) },
        spectrumAgents = spectrumAgents && entitlements.spectrumAgents
    )
}
