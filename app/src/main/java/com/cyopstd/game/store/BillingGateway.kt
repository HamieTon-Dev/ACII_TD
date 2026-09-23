package com.cyopstd.game.store

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** What the store screen is allowed to know about the outside world. */
interface BillingGateway {

    /** Localized prices keyed by product id, empty until Play answers. */
    val prices: Flow<Map<String, String>>

    /** Connection state, so the store can say why a button is dead. */
    val status: Flow<BillingStatus>

    /**
     * Start a purchase. The result arrives through [purchases], not as a return
     * value, because a purchase can also complete later, on another device, or
     * be restored — there is no single moment to return.
     */
    fun purchase(sku: Sku)

    /** Products confirmed owned. Emits on connect, purchase and restore. */
    val purchases: Flow<Set<Sku>>

    /** Re-query Play for everything this account owns. */
    fun restore()

    fun release()
}

enum class BillingStatus {
    /** No Play integration configured in this build. */
    UNAVAILABLE,
    CONNECTING,
    READY,
    /** Connected but the last operation failed. */
    ERROR
}

/**
 * The gateway used when there is no Play billing to talk to.
 *
 * This is what ships until the app is registered in the Play Console, and it
 * is also what a unit test gets. It owns nothing, sells nothing and fails
 * quietly: the store screen renders, every product reads as unavailable, and
 * **the game is completely playable**, which is the property that matters. The
 * game has to work when billing does not — on a device with no Play Services,
 * with no network, or for a player who never opens the store.
 */
class NoBillingGateway : BillingGateway {
    override val prices = MutableStateFlow(emptyMap<String, String>())
    override val status = MutableStateFlow(BillingStatus.UNAVAILABLE)
    override val purchases = MutableStateFlow(emptySet<Sku>())
    override fun purchase(sku: Sku) = Unit
    override fun restore() = Unit
    override fun release() = Unit
}
