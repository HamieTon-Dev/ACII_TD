package com.cyopstd.game.ads

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import java.lang.ref.WeakReference

/**
 * The rewarded ad, and nothing else.
 *
 * The single rule this is written around: **the game must never be stuck
 * waiting on an ad.** Every path through here ends in the continuation being
 * called exactly once — ad shown, ad dismissed, ad failed to show, no ad
 * loaded, no activity attached, or the SDK throwing. An ad that does not
 * arrive should cost the player nothing but the ad.
 *
 * Whether an ad is *allowed* is decided in two places and neither is here.
 * [AdPolicy] decides the game's own rules — who never sees one, how often —
 * and is tested. [ConsentGateway] decides whether Google will permit a request
 * at all. This class knows only how to load and show, and it will not do even
 * that until [start] has been called, which happens once consent has settled.
 *
 * **Logging.** Every transition is logged under the tag `CyOpsAds` with a
 * stable uppercase event name, so a release-build problem can be diagnosed
 * from a bug report's logcat: `AD_LOAD_STARTED`, `AD_LOADED`,
 * `AD_LOAD_FAILED`, `AD_SHOW_STARTED`, `AD_SHOW_FAILED`, `AD_DISMISSED`,
 * `REWARD_EARNED`. Nothing identifying a player is ever logged — the lines
 * carry a format, an ad unit id and an SDK error code, and that is all.
 */
class AdMobGateway(
    context: Context,
    /** Empty when this build has no rewarded unit; the revive is then never offered. */
    private val rewardedUnitId: String = ""
) : AdGateway {

    private val appContext = context.applicationContext

    private var rewarded: RewardedAd? = null
    private var loadingRewarded = false

    /**
     * False until consent has settled and [start] has been called.
     *
     * The SDK is not initialised in the constructor any more. Google's own
     * guidance is that `MobileAds.initialize` comes *after* the UMP flow, and
     * initialising in a field initialiser made that impossible to honour —
     * the gateway was constructed the moment the ViewModel was.
     */
    private var started = false

    /** Weak: this outlives any single Activity and must not pin one. */
    private var activity: WeakReference<Activity> = WeakReference(null)

    private val main = Handler(Looper.getMainLooper())

    /**
     * Run on the main thread, now if we are already on it.
     *
     * The Mobile Ads SDK delivers its callbacks on the main thread and has
     * done for years, but the game's continuations touch Compose state and a
     * background delivery would be a crash rather than a glitch. Posting
     * unconditionally would also change the ordering that
     * [showRewarded]'s reward-before-dismissal latch depends on, so this
     * executes inline when it can.
     */
    private inline fun onMain(crossinline block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post { block() }
    }

    /**
     * Initialise the SDK. Safe to call more than once.
     *
     * Called only once [ConsentGateway.canRequestAds] is true. Until then this
     * gateway reports no ads and requests none, which is the behaviour a
     * player who declined consent must get.
     */
    fun start() {
        if (started) return
        started = true
        try {
            // BEFORE initialize, not after. The configuration governs the SDK
            // itself, so it has to be in place before the SDK is allowed to do
            // anything -- including whatever it does on its own at startup.
            MobileAds.setRequestConfiguration(AdPrivacy.requestConfiguration())
            MobileAds.initialize(appContext) {
                Log.d(TAG, "SDK_INITIALISED childDirected=true personalization=DISABLED rating=G")
            }
        } catch (error: Throwable) {
            Log.w(TAG, "AdMob would not initialise; running without ads", error)
        }
    }

    override val isRewardedReady: Boolean
        get() = started && rewarded != null && activity.get() != null

    fun attach(current: Activity) {
        activity = WeakReference(current)
    }

    // ---------------------------------------------------------- rewarded

    override fun preloadRewarded() {
        if (!started) return
        if (rewardedUnitId.isEmpty()) return
        if (loadingRewarded || rewarded != null) return
        val host = activity.get() ?: return
        loadingRewarded = true
        Log.d(TAG, "AD_LOAD_STARTED format=rewarded unit=$rewardedUnitId")
        try {
            RewardedAd.load(
                host,
                rewardedUnitId,
                AdRequest.Builder().build(),
                object : RewardedAdLoadCallback() {
                    override fun onAdLoaded(ad: RewardedAd) {
                        Log.d(TAG, "AD_LOADED format=rewarded")
                        rewarded = ad
                        loadingRewarded = false
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        Log.i(TAG, "AD_LOAD_FAILED format=rewarded code=${error.code}")
                        rewarded = null
                        loadingRewarded = false
                    }
                }
            )
        } catch (error: Throwable) {
            Log.w(TAG, "AD_LOAD_FAILED format=rewarded threw", error)
            loadingRewarded = false
        }
    }

    /**
     * The reward is earned in `onUserEarnedReward` and nowhere else.
     *
     * The SDK delivers the reward callback *before* the dismissal callback, so
     * `earned` is latched when the reward arrives and read when the ad closes.
     * A player who skips out early gets a dismissal with no reward before it,
     * which is exactly the `false` the caller needs.
     */
    override fun showRewarded(onResult: (earned: Boolean) -> Unit) {
        val ad = rewarded
        val host = activity.get()
        if (ad == null || host == null) {
            Log.i(TAG, "AD_SHOW_FAILED format=rewarded reason=none-loaded")
            onResult(false)
            return
        }
        Log.d(TAG, "AD_SHOW_STARTED format=rewarded")

        var earned = false
        var finished = false
        fun finishOnce() {
            if (finished) return
            finished = true
            rewarded = null
            preloadRewarded()
            // The game's continuation moves Compose state and may restart a
            // run, so it is handed back on the main thread whatever thread the
            // SDK happened to use.
            onMain { onResult(earned) }
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "AD_DISMISSED format=rewarded earned=$earned")
                finishOnce()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                Log.w(TAG, "AD_SHOW_FAILED format=rewarded code=${error.code}")
                finishOnce()
            }
        }

        try {
            ad.show(host) {
                // The one place a reward is ever recognised. Not dismissal,
                // not "the ad was shown" -- this callback and nothing else.
                Log.d(TAG, "REWARD_EARNED format=rewarded")
                earned = true
            }
        } catch (error: Throwable) {
            Log.w(TAG, "AD_SHOW_FAILED format=rewarded threw", error)
            finishOnce()
        }
    }

    private companion object {
        const val TAG = "CyOpsAds"
    }
}
