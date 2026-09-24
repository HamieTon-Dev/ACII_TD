package com.cyopstd.game.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

/**
 * The real consent layer, on Google's User Messaging Platform.
 *
 * Written around one rule, the same one that governs [AdMobGateway]: **the
 * game must never be stuck waiting on Google.** Consent runs at startup, in
 * front of the menu, on a network that may not be there. Every path through
 * here — form shown, form not required, request failed, SDK threw — ends in
 * `onSettled` being called exactly once, and the game starts either way.
 *
 * What happens on failure is deliberately *not* "assume consent". If consent
 * cannot be established, [canRequestAds] reports whatever the SDK has stored,
 * which for a first run with no network is false. The player gets the game
 * with no ads, which is the harmless outcome; the alternative is an
 * unconsented ad request, which is the one that gets a listing taken down.
 *
 * All UMP callbacks are delivered on the main thread by the SDK, so the
 * continuations handed to it here are safe to touch UI state from directly.
 */
class UmpConsentGateway(
    context: Context,
    /**
     * A test device's hashed id, to force the form to appear during
     * development.
     *
     * Empty in every normal build. It is read from the debug build config
     * rather than hard-coded so that a real id never reaches a release, and
     * `ConsentDebugSettings` is only attached when it is non-empty.
     */
    private val debugDeviceHash: String = ""
) : ConsentGateway {

    private val consentInformation: ConsentInformation =
        UserMessagingPlatform.getConsentInformation(context.applicationContext)

    override val canRequestAds: Boolean
        get() = try {
            consentInformation.canRequestAds()
        } catch (error: Throwable) {
            Log.w(TAG, "Could not read consent state; assuming no ads", error)
            false
        }

    override val privacyOptionsRequired: Boolean
        get() = try {
            consentInformation.privacyOptionsRequirementStatus ==
                ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
        } catch (error: Throwable) {
            Log.w(TAG, "Could not read the privacy options requirement", error)
            false
        }

    override fun refresh(activity: Activity, onSettled: () -> Unit) {
        // Guarded because the SDK is entitled to deliver both a success and a
        // failure, and a game that starts twice is as broken as one that
        // never starts.
        var settled = false
        fun settleOnce() {
            if (settled) return
            settled = true
            Log.d(TAG, "CONSENT_SETTLED canRequestAds=$canRequestAds")
            onSettled()
        }

        val parameters = ConsentRequestParameters.Builder()
            // The game is not directed at children and carries no age gate, so
            // this is a truthful `false` rather than a default.
            .setTagForUnderAgeOfConsent(false)
            .apply {
                if (debugDeviceHash.isNotEmpty()) {
                    setConsentDebugSettings(
                        ConsentDebugSettings.Builder(activity)
                            .setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
                            .addTestDeviceHashedId(debugDeviceHash)
                            .build()
                    )
                }
            }
            .build()

        try {
            consentInformation.requestConsentInfoUpdate(
                activity,
                parameters,
                {
                    // Up to date. Show the form only if one is actually
                    // required; this is a no-op everywhere it is not.
                    try {
                        UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                            if (formError != null) {
                                Log.w(TAG, "CONSENT_FORM_FAILED ${formError.errorCode}")
                            }
                            settleOnce()
                        }
                    } catch (error: Throwable) {
                        Log.w(TAG, "Consent form threw", error)
                        settleOnce()
                    }
                },
                { requestError ->
                    // No network, no fill, a misconfigured AdMob app id. None
                    // of them are the player's problem.
                    Log.w(TAG, "CONSENT_UPDATE_FAILED ${requestError.errorCode}")
                    settleOnce()
                }
            )
        } catch (error: Throwable) {
            Log.w(TAG, "Consent update threw", error)
            settleOnce()
        }
    }

    override fun showPrivacyOptions(activity: Activity, onError: (String?) -> Unit) {
        try {
            UserMessagingPlatform.showPrivacyOptionsForm(activity) { formError ->
                if (formError == null) {
                    onError(null)
                } else {
                    Log.w(TAG, "PRIVACY_FORM_FAILED ${formError.errorCode}")
                    onError("Privacy options are unavailable right now.")
                }
            }
        } catch (error: Throwable) {
            Log.w(TAG, "Privacy options threw", error)
            onError("Privacy options are unavailable right now.")
        }
    }

    private companion object {
        const val TAG = "CyOpsConsent"
    }
}
