package com.cyopstd.game.store

import kotlinx.coroutines.flow.Flow

/**
 * Where entitlements live locally, and how a purchase becomes a grant.
 *
 * Kept separate from the gateway on purpose. The gateway knows about Play; this
 * knows about *this player's* save. A purchase that Play confirms has to end up
 * here to survive the app being killed, and a € pack has to be credited exactly
 * once no matter how many times Play re-reports it.
 */
interface StoreRepository {
    val entitlements: Flow<Entitlements>
    val cosmetics: Flow<CosmeticChoice>

    /**
     * Record a confirmed purchase and apply what it grants.
     *
     * @return the € credited, which is zero when this purchase was already
     *         applied. Play re-reports owned products on every connect and on
     *         every restore, so a consumable that credited € on each report
     *         would hand out free currency for the life of the install.
     */
    suspend fun applyPurchase(sku: Sku, orderId: String): Int

    suspend fun chooseCoreSkin(id: String?)
    suspend fun chooseBackground(id: String?)
    suspend fun setSpectrumAgents(enabled: Boolean)
}
