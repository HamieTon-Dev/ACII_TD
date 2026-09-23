package com.cyopstd.game.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import java.lang.ref.WeakReference

/**
 * AdMob interstitials.
 *
 * The single rule this is written around: **the game must never be stuck
 * waiting on an ad.** Every path through here ends in the continuation being
 * called exactly once — ad shown, ad dismissed, ad failed to show, no ad
 * loaded, no activity attached, or the SDK throwing. An ad that does not
 * arrive should cost the player nothing but the ad.
 *
 * Whether an ad is allowed at all is not decided here; that is
 * [AdPolicy]'s job, and it is tested. This only knows how to load and show.
 */
class AdMobGateway(
    context: Context,
    private val interstitialUnitId: String
) : AdGateway {

    private var loaded: InterstitialAd? = null
    private var loading = false

    /** Weak: this outlives any single Activity and must not pin one. */
    private var activity: WeakReference<Activity> = WeakReference(null)

    init {
        try {
            MobileAds.initialize(context.applicationContext) { }
        } catch (error: Throwable) {
            Log.w(TAG, "AdMob would not initialise; running without ads", error)
        }
    }

    override val isReady: Boolean get() = loaded != null && activity.get() != null

    fun attach(current: Activity) {
        activity = WeakReference(current)
    }

    override fun preload() {
        if (loading || loaded != null) return
        val host = activity.get() ?: return
        loading = true
        try {
            InterstitialAd.load(
                host,
                interstitialUnitId,
                AdRequest.Builder().build(),
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: InterstitialAd) {
                        loaded = ad
                        loading = false
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        // Not worth surfacing: no fill is normal, and the game
                        // simply carries on without one.
                        Log.i(TAG, "No interstitial available: ${error.message}")
                        loaded = null
                        loading = false
                    }
                }
            )
        } catch (error: Throwable) {
            Log.w(TAG, "Could not request an interstitial", error)
            loading = false
        }
    }

    override fun showInterstitial(onFinished: () -> Unit) {
        val ad = loaded
        val host = activity.get()
        if (ad == null || host == null) {
            onFinished()
            return
        }

        // Guarded so the continuation cannot run twice if the SDK delivers both
        // a dismissal and a failure, which it is entitled to do.
        var finished = false
        fun finishOnce() {
            if (finished) return
            finished = true
            loaded = null
            preload()
            onFinished()
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() = finishOnce()
            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                Log.w(TAG, "Interstitial failed to show: ${error.message}")
                finishOnce()
            }
        }

        try {
            ad.show(host)
        } catch (error: Throwable) {
            Log.w(TAG, "Interstitial threw on show", error)
            finishOnce()
        }
    }

    private companion object {
        const val TAG = "CyOpsAds"
    }
}
