package org.audienzz.mobile.util

import android.content.res.Resources
import android.graphics.Rect
import android.view.View
import android.view.ViewTreeObserver
import androidx.annotation.Px
import com.google.android.gms.ads.admanager.AdManagerAdRequest
import org.audienzz.mobile.AudienzzResultCode
import org.audienzz.mobile.original.AudienzzInterstitialAdHandler
import org.audienzz.mobile.original.AudienzzRewardedVideoAdHandler
import org.audienzz.mobile.original.callbacks.AudienzzFullScreenContentCallback
import org.audienzz.mobile.original.callbacks.AudienzzInterstitialAdLoadCallback
import org.audienzz.mobile.original.callbacks.AudienzzRewardedAdLoadCallback

/**
 * Returns true if the view's visibility is VISIBLE and it's located in screen rect.
 */
fun View.isVisibleOnScreen() =
    visibility == View.VISIBLE && getGlobalVisibleRectIgnoringSize(Rect())

private fun View.getGlobalVisibleRectIgnoringSize(outRect: Rect): Boolean {
    val extraPadding = 1
    outRect.set(-extraPadding, -extraPadding, width + extraPadding, height + extraPadding)
    return parent == null || parent.getChildVisibleRect(this, outRect, null)
}

/**
 * Triggers [listener] if view is already visible on screen or subscribes to
 * [ViewTreeObserver.OnPreDrawListener]
 *
 * @see [isVisibleOnScreen]
 */
fun View.addOnBecameVisibleOnScreenListener(listener: () -> Unit) {
    if (isVisibleOnScreen()) {
        listener()
    } else {
        viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (isVisibleOnScreen()) {
                    viewTreeObserver.removeOnPreDrawListener(this)
                    listener()
                }
                return true
            }
        })
    }
}

/**
 * Lazy loads [AudienzzInterstitialAdHandler] with specified params
 *
 * @see [addOnBecameVisibleOnScreenListener]
 *
 * @param fullScreenContentCallback use for work with callbacks from Interstitial ad
 *
 * @param resultCallback return result code, request and listener for Interstitial ad
 * Then it is required to load GAM ad.
 */
fun View.lazyAdLoader(
    adHandler: AudienzzInterstitialAdHandler,
    gamRequestBuilder: AdManagerAdRequest.Builder = AdManagerAdRequest.Builder(),
    adLoadCallback: AudienzzInterstitialAdLoadCallback,
    fullScreenContentCallback: AudienzzFullScreenContentCallback? = null,
    resultCallback: (
    (
        AudienzzResultCode?,
        AdManagerAdRequest,
        AudienzzInterstitialAdLoadCallback,
    ) -> Unit
    ),
) {
    addOnBecameVisibleOnScreenListener {
        adHandler.load(
            gamRequestBuilder = gamRequestBuilder,
            adLoadCallback = adLoadCallback,
            fullScreenContentCallback = fullScreenContentCallback,
            resultCallback = resultCallback,
        )
    }
}

/**
 * Lazy loads [AudienzzRewardedVideoAdHandler] with specified params
 *
 * @see [addOnBecameVisibleOnScreenListener]
 *
 * @param fullScreenContentCallback use for work with callbacks from Rewarded ad
 *
 * @param resultCallback return result code, request and listener for Rewarded ad
 * Then it is required to load GAM ad.
 */
fun View.lazyAdLoader(
    adHandler: AudienzzRewardedVideoAdHandler,
    gamRequestBuilder: AdManagerAdRequest.Builder = AdManagerAdRequest.Builder(),
    adLoadCallback: AudienzzRewardedAdLoadCallback,
    fullScreenContentCallback: AudienzzFullScreenContentCallback? = null,
    resultCallback: (
    (
        AudienzzResultCode?,
        AdManagerAdRequest,
        AudienzzRewardedAdLoadCallback,
    ) -> Unit
    ),
) {
    addOnBecameVisibleOnScreenListener {
        adHandler.load(
            gamRequestBuilder = gamRequestBuilder,
            adLoadCallback = adLoadCallback,
            fullScreenContentCallback = fullScreenContentCallback,
            resultCallback = resultCallback,
        )
    }
}

fun Resources.pxToDp(@Px px: Int): Int = (px / displayMetrics.density).toInt()
