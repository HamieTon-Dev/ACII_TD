package com.cyopstd.game.store

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.consumePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

/**
 * Google Play Billing.
 *
 * Three rules shape everything here, and they are the ones that cost real
 * money to get wrong:
 *
 * 1. **A purchase is granted when Play says it is owned**, not when the buy
 *    flow returns. A player can buy on another device, restore, or complete a
 *    pending payment hours later — all of which arrive as "you own this" with
 *    no purchase flow attached.
 * 2. **Everything owned must be acknowledged within three days** or Play
 *    refunds it automatically. Consumables are consumed instead, which is what
 *    lets a € pack be bought again.
 * 3. **Failure is silent to the player and total for the game.** If Play
 *    cannot be reached, the store says so and the game plays on. Nothing here
 *    may throw into the game.
 *
 * The purchase *grant* is not done here — this reports what Play says is
 * owned, and [com.cyopstd.game.save.GameRepository.applyPurchase] decides what
 * that means, guarded by order id so a re-report cannot pay out twice.
 */
class PlayBillingGateway(
    context: Context,
    private val scope: CoroutineScope,
    /** Called with the confirmed purchase so its order id can guard the grant. */
    private val onPurchaseConfirmed: (Sku, String) -> Unit
) : BillingGateway {

    override val prices = MutableStateFlow(emptyMap<String, String>())
    override val status = MutableStateFlow(BillingStatus.CONNECTING)
    override val purchases = MutableStateFlow(emptySet<Sku>())

    private var details: Map<String, ProductDetails> = emptyMap()

    /**
     * Weak, because launching the buy flow needs the current Activity and this
     * gateway outlives any one of them. A strong reference here would leak an
     * Activity for the life of the process.
     */
    private var activity: WeakReference<Activity> = WeakReference(null)

    private val listener = PurchasesUpdatedListener { result, list ->
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                scope.launch { list?.forEach { handle(it) } }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> Unit
            else -> {
                Log.w(TAG, "Purchase flow failed: ${result.debugMessage}")
                status.value = BillingStatus.ERROR
            }
        }
    }

    private val client: BillingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener(listener)
        .enablePendingPurchases(
            com.android.billingclient.api.PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build()
        )
        .build()

    fun attach(current: Activity) {
        activity = WeakReference(current)
    }

    fun connect() {
        status.value = BillingStatus.CONNECTING
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                    Log.w(TAG, "Billing setup failed: ${result.debugMessage}")
                    status.value = BillingStatus.ERROR
                    return
                }
                status.value = BillingStatus.READY
                scope.launch {
                    loadProducts()
                    // Asked for on every connect, not only on RESTORE: this is
                    // how a purchase made on another device, or one that
                    // completed while the app was closed, actually arrives.
                    queryOwned()
                }
            }

            override fun onBillingServiceDisconnected() {
                status.value = BillingStatus.ERROR
            }
        })
    }

    private suspend fun loadProducts() {
        val products = Sku.entries.map { sku ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(sku.id)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        }
        val result = client.queryProductDetails(
            QueryProductDetailsParams.newBuilder().setProductList(products).build()
        )
        val list = result.productDetailsList.orEmpty()
        details = list.associateBy { it.productId }
        // Play's price, in the player's currency. The catalog's own strings are
        // only ever a placeholder for before this arrives.
        prices.value = list.mapNotNull { product ->
            product.oneTimePurchaseOfferDetails?.formattedPrice?.let { product.productId to it }
        }.toMap()
    }

    private suspend fun queryOwned() {
        val result = client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        )
        result.purchasesList.forEach { handle(it) }
    }

    private suspend fun handle(purchase: Purchase) {
        // PENDING is a real state -- cash and carrier billing can sit here for
        // hours. Granting on pending would hand out unpaid product.
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return

        val owned = purchase.products.mapNotNull { Sku.byId(it) }
        if (owned.isEmpty()) return

        owned.forEach { sku -> onPurchaseConfirmed(sku, purchase.orderId.orEmpty()) }
        purchases.value = purchases.value + owned

        // Consumables are consumed so they can be bought again; everything else
        // is acknowledged. Either way it must happen within three days or Play
        // refunds the purchase automatically.
        val consumable = owned.any { it.kind == SkuKind.CONSUMABLE }
        try {
            if (consumable) {
                client.consumePurchase(
                    ConsumeParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
                )
            } else if (!purchase.isAcknowledged) {
                client.acknowledgePurchase(
                    AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(purchase.purchaseToken)
                        .build()
                )
            }
        } catch (error: Exception) {
            Log.w(TAG, "Could not settle purchase ${purchase.orderId}", error)
        }
    }

    override fun purchase(sku: Sku) {
        val host = activity.get()
        val product = details[sku.id]
        if (host == null || product == null) {
            Log.w(TAG, "Cannot start purchase for ${sku.id}: no activity or no details")
            status.value = BillingStatus.ERROR
            return
        }
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(product)
                        .build()
                )
            )
            .build()
        try {
            client.launchBillingFlow(host, params)
        } catch (error: Exception) {
            Log.w(TAG, "Could not launch the purchase flow", error)
            status.value = BillingStatus.ERROR
        }
    }

    override fun restore() {
        if (status.value != BillingStatus.READY) {
            connect()
            return
        }
        scope.launch(Dispatchers.Main) { queryOwned() }
    }

    override fun release() {
        activity = WeakReference(null)
        try {
            client.endConnection()
        } catch (error: Exception) {
            Log.w(TAG, "Billing client would not close cleanly", error)
        }
    }

    private companion object {
        const val TAG = "CyOpsBilling"
    }
}
