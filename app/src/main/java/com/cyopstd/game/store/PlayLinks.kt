package com.cyopstd.game.store

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

/**
 * Deep links into the Google Play client.
 *
 * The app deliberately has **no account system of its own**. There is nothing
 * to log into here: purchases belong to whichever Google account is signed into
 * the Play Store on the device, and the only honest thing the account screen
 * can do is hand the player over to Play itself. These are the links that do
 * that.
 *
 * The URIs are built as a pure list so they can be tested without a device,
 * and they come in preference order: the `market://` scheme opens the Play app
 * directly, and the `https://` form is the fallback for a device where the Play
 * app is absent or disabled — which is exactly the device where the first
 * intent would have thrown.
 */
object PlayLinks {

    /** Where the player manages purchases, refunds and payment methods. */
    fun orderHistoryUris(): List<Uri> = listOf(
        Uri.parse("https://play.google.com/store/account/orderhistory")
    )

    /** This app's own listing, for rating it or sharing it. */
    fun listingUris(packageName: String): List<Uri> = listOf(
        Uri.parse("market://details?id=$packageName"),
        Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
    )

    /**
     * Opens the first URI that something on this device can handle.
     *
     * Returns false when nothing could, so the caller can say so instead of a
     * button that looks like it worked. A device with no browser and no Play
     * app is unusual but entirely possible, and it must not be a crash.
     */
    fun open(context: Context, uris: List<Uri>): Boolean {
        for (uri in uris) {
            val intent = Intent(Intent.ACTION_VIEW, uri)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
                return true
            } catch (e: ActivityNotFoundException) {
                Log.i("CyOpsStore", "nothing handles $uri", e)
            }
        }
        return false
    }
}
