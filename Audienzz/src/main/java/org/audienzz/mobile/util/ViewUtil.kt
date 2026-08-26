package org.audienzz.mobile.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
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
 * Used for initial lazy-load triggering — any part of the view being visible is enough.
 */
fun View.isVisibleOnScreen() =
    visibility == View.VISIBLE && getGlobalVisibleRectIgnoringSize(Rect())

private fun View.getGlobalVisibleRectIgnoringSize(outRect: Rect): Boolean {
    val extraPadding = 1
    outRect.set(-extraPadding, -extraPadding, width + extraPadding, height + extraPadding)
    // H3: a detached view (parent == null) is NOT on screen. Treating it as visible made a
    // recycled/detached slot report "visible" and trigger fetchDemand storms.
    return parent != null && parent.getChildVisibleRect(this, outRect, null)
}

/**
 * Returns the fraction (0.0–1.0) of this view's height that is currently visible on screen.
 *
 * Uses [getGlobalVisibleRect] which clips the view's rect to the window's visible area,
 * so the result naturally excludes system bars, keyboard, and off-screen portions.
 *
 * Returns 0 if the view is GONE/INVISIBLE, not yet measured, or fully off-screen.
 */
internal fun View.visibleHeightFraction(): Float {
    if (visibility != View.VISIBLE) return 0f
    val totalHeight = measuredHeight
    if (totalHeight <= 0) return 0f
    val visibleRect = Rect()
    if (!getGlobalVisibleRect(visibleRect)) return 0f
    return visibleRect.height().toFloat() / totalHeight.toFloat()
}

/**
 * Smart refresh fires only when at least this fraction of the ad height is visible, avoiding
 * triggering on a pixel-sliver as the ad enters or leaves the viewport.
 */
private const val SMART_REFRESH_VISIBILITY_THRESHOLD = 0.2f

/**
 * True when at least [SMART_REFRESH_VISIBILITY_THRESHOLD] of the view's height is on screen — the
 * same threshold [addContinuousVisibilityListener] uses to decide visible vs hidden. Exposed so
 * callers can do a one-off *level* check (e.g. stop refresh for a prefetched-but-not-yet-visible
 * view) rather than waiting for an edge transition that may never come.
 */
fun View.isVisibleForSmartRefresh(): Boolean =
    visibleHeightFraction() >= SMART_REFRESH_VISIBILITY_THRESHOLD

/**
 * Fraction of the ad's height that may hang off the BOTTOM of the viewport while the ad still
 * qualifies for smart refresh. More than this off the bottom → pause (see [isRefreshEligible]).
 */
private const val SMART_REFRESH_MAX_BOTTOM_OFFSCREEN_FRACTION = 0.5f

/**
 * Directional smart-**refresh** eligibility (stricter than [isVisibleForSmartRefresh], which
 * gates the initial load). The ad is eligible only when BOTH hold:
 * - its **top edge is fully on screen** — if 1px or more of the top is clipped above the
 *   viewport, it is ineligible (pause), and
 * - **no more than 50%** of its height is off the **bottom** of the viewport — if more than
 *   half is below the fold, it is ineligible (pause on start).
 *
 * A fully-visible ad, or one entering from the bottom with ≥50% on screen, is eligible.
 *
 * Geometry: [getGlobalVisibleRect] gives the visible portion clipped by every ancestor
 * (so nested scroll containers are honoured) and [getLocationInWindow] gives the view's full
 * top — both in window coordinates, so their tops/bottoms are directly comparable. Kept
 * separate from [visibleHeightFraction] so the viewability tracker's ≥50% math is untouched.
 */
internal fun View.isRefreshEligible(): Boolean {
    if (visibility != View.VISIBLE) return false
    val totalHeight = measuredHeight
    if (totalHeight <= 0) return false
    val visibleRect = Rect()
    if (!getGlobalVisibleRect(visibleRect)) return false // fully off screen

    val location = IntArray(2)
    getLocationInWindow(location)
    val viewTop = location[1]
    val viewBottom = viewTop + totalHeight

    // getGlobalVisibleRect clamps to the visible area, so both are >= 0.
    val topOffscreenPx = visibleRect.top - viewTop
    val bottomOffscreenPx = viewBottom - visibleRect.bottom

    val topFullyOnScreen = topOffscreenPx < 1
    val bottomWithinHalf = bottomOffscreenPx <= totalHeight * SMART_REFRESH_MAX_BOTTOM_OFFSCREEN_FRACTION
    return topFullyOnScreen && bottomWithinHalf
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
    useDirectionalGate: Boolean,
    onBecameVisible: () -> Unit,
    onBecameHidden: () -> Unit,
): ViewTreeObserver.OnPreDrawListener {
    // Smart-refresh v2 pauses/resumes on the directional eligibility rule (top edge fully on screen
    // AND ≤50% off the bottom); the legacy model uses the ≥20% visible-height threshold.
    fun eligible(): Boolean = if (useDirectionalGate) isRefreshEligible() else isVisibleForSmartRefresh()
    var wasVisible = eligible()
    val listener = object : ViewTreeObserver.OnPreDrawListener {
        override fun onPreDraw(): Boolean {
            val isVisible = eligible()
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
        // H3: capture the observer we register on. After an attach/detach the view's current
        // viewTreeObserver can be a different instance, so removing via the property would be a
        // silent no-op and the listener would leak and fire every frame.
        val registrationObserver = viewTreeObserver
        registrationObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (isVisibleOnScreen()) {
                    val observer = if (registrationObserver.isAlive) registrationObserver else viewTreeObserver
                    observer.removeOnPreDrawListener(this)
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
    // H3: a detached view (parent == null) is not within any viewport — do not treat it as in range.
    return parent != null && parent.getChildVisibleRect(this, expandedRect, null)
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
    // H3: remove from the observer we registered on (see addOnBecameVisibleOnScreenListener).
    val registrationObserver = viewTreeObserver
    registrationObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
        override fun onPreDraw(): Boolean {
            if (isWithinPrefetchMargin(marginPx)) {
                logPosition("entered prefetch zone → triggering load")
                val observer = if (registrationObserver.isAlive) registrationObserver else viewTreeObserver
                observer.removeOnPreDrawListener(this)
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

/**
 * Unwraps this [Context] to its host [Activity] by walking the [ContextWrapper] chain, or returns
 * null for a non-Activity context (e.g. application context). Used to match an ad to its screen
 * for screen-aware smart refresh.
 */
internal fun Context.unwrapActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
