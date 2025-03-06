package org.audienzz.mobile.util

import android.content.res.Resources
import android.graphics.Rect
import android.view.View
import android.view.ViewTreeObserver
import androidx.annotation.Px
import com.google.android.gms.ads.admanager.AdManagerAdRequest
import com.google.android.gms.ads.admanager.AdManagerInterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import org.audienzz.mobile.AudienzzResultCode
import org.audienzz.mobile.original.AudienzzInterstitialAdHandler
import org.audienzz.mobile.original.AudienzzRewardedVideoAdHandler

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
 * @param manager use for work with listeners from Interstitial ad
 *
 * @param onLoadRequest return request and listener for Interstitial load ad
 */
fun View.lazyLoadAd(
    adHandler: AudienzzInterstitialAdHandler,
    listener: AdManagerInterstitialAdLoadCallback,
    request: AdManagerAdRequest = AdManagerAdRequest.Builder().build(),
    resultCallback: ((AudienzzResultCode?) -> Unit),
    manager: AudienzzFullScreenContentCallback?,
    onLoadRequest: ((AdManagerAdRequest, AdManagerInterstitialAdLoadCallback) -> Unit),
) {
    addOnBecameVisibleOnScreenListener {
        adHandler.load(
            context = context,
            listener = listener,
            request = request,
            resultCallback = resultCallback,
            manager = manager,
            onLoadRequest = onLoadRequest,
        )
    }
}

/**
 * Lazy loads [AudienzzRewardedVideoAdHandler] with specified params
 *
 * @see [addOnBecameVisibleOnScreenListener]
 */
fun View.lazyLoadAd(
    adHandler: AudienzzRewardedVideoAdHandler,
    listener: RewardedAdLoadCallback,
    request: AdManagerAdRequest = AdManagerAdRequest.Builder().build(),
    resultCallback: ((AudienzzResultCode?) -> Unit),
    manager: AudienzzFullScreenContentCallback?,
    requestCallback: ((AdManagerAdRequest, RewardedAdLoadCallback) -> Unit),
) {
    addOnBecameVisibleOnScreenListener {
        adHandler.load(
            context = context,
            listener = listener,
            request = request,
            resultCallback = resultCallback,
            manager = manager,
            requestCallback = requestCallback,
        )
    }
}

fun Resources.pxToDp(@Px px: Int): Int = (px / displayMetrics.density).toInt()
