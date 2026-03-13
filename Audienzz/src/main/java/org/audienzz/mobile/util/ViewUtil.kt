package org.audienzz.mobile.util

import android.content.res.Resources
import android.graphics.Rect
import android.util.Log
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
 * Returns true if the view is within [marginTopPx] pixels below the bottom of the screen.
 * Used to detect when a view is about to enter the viewport, enabling pre-fetch of Prebid bids
 * before the ad slot scrolls into view.
 */
private fun View.isInPrefetchZone(@Px marginTopPx: Int): Boolean {
    if (visibility != View.VISIBLE) return false
    val location = IntArray(2)
    getLocationOnScreen(location)
    val viewTop = location[1]
    val viewBottom = viewTop + height
    val screenHeight = resources.displayMetrics.heightPixels
    // Fire when view top is below screen but within margin, and view hasn't scrolled above screen
    return viewTop < screenHeight + marginTopPx && viewBottom > 0
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
 * Like [addOnBecameVisibleOnScreenListener] but also fires when the view enters a prefetch zone
 * [marginTopPx] pixels below the screen bottom, enabling early Prebid bid requests before the
 * slot scrolls fully into view.
 *
 * Falls back to standard [addOnBecameVisibleOnScreenListener] when [marginTopPx] is 0.
 */
fun View.addOnBecameVisibleOnScreenListenerWithMargin(
    @Px marginTopPx: Int = 0,
    listener: () -> Unit,
) {
    if (marginTopPx <= 0) {
        addOnBecameVisibleOnScreenListener(listener)
        return
    }
    Log.d("AudienzzPrefetch", "[PrefetchListener] Watching for view to enter zone (marginTopPx=$marginTopPx)")
    if (isVisibleOnScreen() || isInPrefetchZone(marginTopPx)) {
        Log.d("AudienzzPrefetch", "[PrefetchListener] View already in prefetch zone → firing immediately")
        listener()
    } else {
        viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (isVisibleOnScreen() || isInPrefetchZone(marginTopPx)) {
                    Log.d("AudienzzPrefetch", "[PrefetchListener] View entered prefetch zone → firing bid request")
                    viewTreeObserver.removeOnPreDrawListener(this)
                    listener()
                }
                return true
            }
        })
    }
}

/**
 * Registers a continuous scroll-based visibility listener that fires [onBecameVisible] and
 * [onBecameHidden] each time the view transitions between visible and hidden states.
 *
 * Unlike [addOnBecameVisibleOnScreenListener] this is NOT one-shot — it keeps tracking
 * until manually removed via the returned handle.
 *
 * @return The registered [ViewTreeObserver.OnScrollChangedListener]. Pass it to
 * [ViewTreeObserver.removeOnScrollChangedListener] to stop tracking (e.g. on view detach).
 */
fun View.addContinuousVisibilityListener(
    onBecameVisible: () -> Unit,
    onBecameHidden: () -> Unit,
): ViewTreeObserver.OnScrollChangedListener {
    var isCurrentlyVisible = isVisibleOnScreen()
    Log.d("AudienzzSmartRefresh", "[ContinuousListener] Attached. Initial state: ${if (isCurrentlyVisible) "VISIBLE" else "HIDDEN"}")
    val listener = ViewTreeObserver.OnScrollChangedListener {
        val nowVisible = isVisibleOnScreen()
        if (nowVisible != isCurrentlyVisible) {
            isCurrentlyVisible = nowVisible
            Log.d("AudienzzSmartRefresh", "[ContinuousListener] State changed → ${if (nowVisible) "VISIBLE" else "HIDDEN"}")
            if (nowVisible) onBecameVisible() else onBecameHidden()
        }
    }
    viewTreeObserver.addOnScrollChangedListener(listener)
    return listener
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

fun Resources.dpToPx(dp: Int): Int = (dp * displayMetrics.density).toInt()
