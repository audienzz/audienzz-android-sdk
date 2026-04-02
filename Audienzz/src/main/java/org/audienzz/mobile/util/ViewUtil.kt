package org.audienzz.mobile.util

import android.content.res.Resources
import android.graphics.Rect
import android.util.Log
import android.view.View
import android.view.ViewGroup
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
/**
 * Registers a persistent visibility listener that fires [onBecameVisible] and [onBecameHidden]
 * each time the view transitions between visible and hidden states.
 *
 * Unlike [addOnBecameVisibleOnScreenListener] this is NOT one-shot — it keeps tracking until
 * the returned listener is manually removed from the [ViewTreeObserver].
 */
fun View.addContinuousVisibilityListener(
    onBecameVisible: () -> Unit,
    onBecameHidden: () -> Unit,
): ViewTreeObserver.OnPreDrawListener {
    var wasVisible = isVisibleOnScreen()
    val listener = object : ViewTreeObserver.OnPreDrawListener {
        override fun onPreDraw(): Boolean {
            val isVisible = isVisibleOnScreen()
            if (isVisible && !wasVisible) {
                wasVisible = true
                onBecameVisible()
            } else if (!isVisible && wasVisible) {
                wasVisible = false
                onBecameHidden()
            }
            return true
        }
    }
    viewTreeObserver.addOnPreDrawListener(listener)
    return listener
}

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
 * Returns true if any part of this view is within [marginPx] pixels of the actually visible
 * screen area, walking the same parent-clip hierarchy as [isVisibleOnScreen].
 *
 * Expanding the view's rect by [marginPx] before calling [parent.getChildVisibleRect] means
 * "would this view be visible if it were marginPx taller?" — which is exactly "is the view
 * within marginPx of the visible clip rect."
 *
 * This avoids relying on [android.util.DisplayMetrics.heightPixels] which can include system
 * bar areas that are not part of the visible window content.
 */
fun View.isWithinPrefetchMargin(@Px marginPx: Int): Boolean {
    if (visibility != View.VISIBLE) return false
    val expandedRect = Rect(-marginPx, -marginPx, width + marginPx, height + marginPx)
    return parent == null || parent.getChildVisibleRect(this, expandedRect, null)
}

/**
 * One-shot listener that fires [onReady] as soon as this view is within [marginDp] dp of the
 * visible screen area, then removes itself.
 *
 * If the view is already within range the callback is invoked immediately (no listener added).
 *
 * @param marginDp distance in dp before the view enters the viewport that should trigger loading.
 *                 Pass 0 to fire only when the view is actually on screen.
 */
private fun View.findRecyclerViewAncestor(): ViewGroup? {
    var p = parent
    while (p != null) {
        if (p.javaClass.name == "androidx.recyclerview.widget.RecyclerView") return p as? ViewGroup
        p = (p as? ViewGroup)?.parent
    }
    return null
}

fun View.addPrefetchMarginListener(
    marginDp: Int,
    onReady: () -> Unit,
) {
    val marginPx = (marginDp * resources.displayMetrics.density).toInt()

    if (marginDp > 0 && findRecyclerViewAncestor() != null) {
        Log.w(TAG, "addPrefetchMarginListener — prefetchMarginDp=$marginDp has no effect inside a RecyclerView. " +
            "RecyclerView creates ViewHolders just before they appear, so the view is already within the margin when load() is called. " +
            "Use withLazyLoading=false in onBindViewHolder instead and rely on RecyclerView's own item prefetch (setInitialPrefetchItemCount).")
    }

    fun logPosition(event: String) {
        val location = IntArray(2)
        getLocationOnScreen(location)
        val windowRect = Rect()
        getWindowVisibleDisplayFrame(windowRect)
        val distanceFromVisibleBottom = location[1] - windowRect.bottom
        Log.d(TAG, "addPrefetchMarginListener [$event] — " +
            "marginDp=$marginDp (${marginPx}px), " +
            "viewTop=${location[1]}px, viewBottom=${location[1] + measuredHeight}px, " +
            "visibleWindowBottom=${windowRect.bottom}px, " +
            "distanceBelowScreen=${distanceFromVisibleBottom}px " +
            "(${(distanceFromVisibleBottom / resources.displayMetrics.density).toInt()}dp)")
    }

    if (isWithinPrefetchMargin(marginPx)) {
        logPosition("already in range → triggering immediately")
        onReady()
        return
    }
    logPosition("registered, waiting")
    viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
        override fun onPreDraw(): Boolean {
            if (isWithinPrefetchMargin(marginPx)) {
                logPosition("entered prefetch zone → triggering load")
                viewTreeObserver.removeOnPreDrawListener(this)
                onReady()
            }
            return true
        }
    })
}

private const val TAG = "AudienzzViewUtil"

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
