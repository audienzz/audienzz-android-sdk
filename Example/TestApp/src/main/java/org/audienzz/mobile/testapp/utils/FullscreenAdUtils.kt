package org.audienzz.mobile.testapp.utils

import android.util.Log
import com.google.android.gms.ads.AdError
import org.audienzz.mobile.original.callbacks.AudienzzFullScreenContentCallback

object FullscreenAdUtils {
    fun createFullScreenCallback(
        logTag: String,
        onAdDismissedCallback: (() -> Unit)? = null,
    ): AudienzzFullScreenContentCallback {
        return object : AudienzzFullScreenContentCallback() {
            override fun onAdClicked() {
                super.onAdClicked()
                Log.d(logTag, "Ad was clicked")
            }

            override fun onAdShowedFullScreenContent() {
                super.onAdShowedFullScreenContent()
                Log.d(logTag, "Ad was shown")
            }

            override fun onAdImpression() {
                super.onAdImpression()
                Log.d(logTag, "Ad impression")
            }

            override fun onAdDismissedFullScreenContent() {
                super.onAdDismissedFullScreenContent()
                onAdDismissedCallback?.invoke()
                Log.d(logTag, "Ad was dismissed")
            }

            override fun onAdFailedToShowFullScreenContent(p0: AdError) {
                super.onAdFailedToShowFullScreenContent(p0)
                Log.d(logTag, "Ad failed to show")
            }
        }
    }
}