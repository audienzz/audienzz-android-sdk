package org.audienzz.mobile

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import androidx.core.widget.NestedScrollView
import kotlin.math.abs
import kotlin.math.max

/**
 * Wraps an ad view and keeps it sticky within a reserved area as the user scrolls.
 *
 * Reserve [maxHeight] pixels in your layout. As the user scrolls past the wrapper,
 * the child ad view slides within the reserved area, staying visible for as long
 * as possible before scrolling off-screen.
 *
 * The sticky behaviour mirrors the Flutter `AudienzzStickyAdWrapper` widget.
 * Use [isStickyEnabled] to toggle the sticky behaviour at runtime.
 *
 * **View-based usage**
 * ```kotlin
 * val stickyWrapper = AudienzzStickyAdWrapperView(context, maxHeightDp = 450)
 * stickyWrapper.setAdView(myBannerView)
 * stickyWrapper.attachToScrollView(nestedScrollView)
 * parentLayout.addView(stickyWrapper, LayoutParams(MATCH_PARENT, WRAP_CONTENT))
 * ```
 *
 * **Jetpack Compose usage**
 * ```kotlin
 * AndroidView(
 *     factory = { ctx ->
 *         AudienzzStickyAdWrapperView(ctx, maxHeightDp = 450).also { wrapper ->
 *             wrapper.setAdView(AudienzzBannerView(ctx, configId, adSize))
 *             wrapper.attachToScrollView(nestedScrollView)
 *         }
 *     },
 *     modifier = Modifier.fillMaxWidth()
 * )
 * ```
 */
public class AudienzzStickyAdWrapperView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    maxHeightDp: Int = DEFAULT_MAX_HEIGHT_DP,
) : FrameLayout(context, attrs, defStyleAttr) {

    /** Height reserved in the layout in pixels. Set in dp via the constructor. */
    public var maxHeight: Int = dpToPx(maxHeightDp)
        set(value) {
            field = value
            requestLayout()
        }

    /**
     * Y offset (pixels) from the top of the visible scroll viewport where the ad sticks.
     * Defaults to `null` which resolves to 0.
     */
    public var stickyTopOffset: Int? = null

    /** Whether sticky behaviour is active. When `false` the child stays at position 0. */
    public var isStickyEnabled: Boolean = true
        set(value) {
            field = value
            updatePosition()
        }

    private var adView: View? = null
    private var scrollViewRef: ViewGroup? = null
    private var scrollChangedListener: ViewTreeObserver.OnScrollChangedListener? = null

    // Cached Y position of this wrapper within the scroll view's content space.
    // Recomputed after each layout pass so it stays correct as the page reflows.
    // Using sv.scrollY + this offset avoids calling getLocationInWindow() on every
    // scroll frame — getLocationInWindow traverses the whole hierarchy and can lag
    // behind the logical scroll state during flings, producing a visible "shift"
    // when the finger is released.
    private var wrapperScrollTop = 0

    // MARK: - Public API

    /** Sets the ad view to be made sticky. Replaces any previously set ad view. */
    public fun setAdView(view: View) {
        removeAllViews()
        adView = view
        addView(view, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    /** Attaches sticky scroll tracking to a [NestedScrollView]. */
    public fun attachToScrollView(scrollView: NestedScrollView) {
        attachScrollListener(scrollView)
    }

    /** Attaches sticky scroll tracking to a [android.widget.ScrollView]. */
    public fun attachToScrollView(scrollView: android.widget.ScrollView) {
        attachScrollListener(scrollView)
    }

    /** Detaches from the current scroll view, stopping scroll-driven updates. */
    public fun detachFromScrollView() {
        scrollChangedListener?.let {
            scrollViewRef?.viewTreeObserver?.removeOnScrollChangedListener(it)
        }
        scrollChangedListener = null
        scrollViewRef = null
    }

    // MARK: - Overrides

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Always measure to exactly maxHeight so the space is reserved in the layout.
        super.onMeasure(
            widthMeasureSpec,
            MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.EXACTLY),
        )
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (changed) {
            wrapperScrollTop = computeTopInScrollContent()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        detachFromScrollView()
    }

    // MARK: - Private

    private fun attachScrollListener(scrollView: ViewGroup) {
        detachFromScrollView()
        scrollViewRef = scrollView
        val listener = ViewTreeObserver.OnScrollChangedListener { updatePosition() }
        scrollChangedListener = listener
        scrollView.viewTreeObserver.addOnScrollChangedListener(listener)
    }

    /**
     * Computes this wrapper's Y position within the scroll view's content coordinate space.
     * Called in [onLayout] so it reflects the latest layout state.
     *
     * Uses [getLocationInWindow] for accuracy across arbitrary nesting depths, then
     * adds the current [scrollY] to convert from viewport-relative to content-relative.
     * This snapshot is stable between layout changes and lets [updatePosition] read only
     * `sv.scrollY` — a single integer — instead of calling [getLocationInWindow] on
     * every scroll frame.
     */
    private fun computeTopInScrollContent(): Int {
        val sv = scrollViewRef ?: return 0
        val wrapperLoc = IntArray(2)
        val svLoc = IntArray(2)
        getLocationInWindow(wrapperLoc)
        sv.getLocationInWindow(svLoc)
        // (wrapperLoc[1] - svLoc[1]) is the wrapper's current Y relative to the visible
        // top of the scroll view.  Adding scrollY converts that to content-space Y.
        return (wrapperLoc[1] - svLoc[1]) + sv.scrollY
    }

    private fun updatePosition() {
        if (!isStickyEnabled) {
            adView?.translationY = 0f
            return
        }
        val sv = scrollViewRef ?: return
        val child = adView ?: return

        val topOffset = stickyTopOffset ?: 0
        val childHeight = child.height.takeIf { it > 0 } ?: height
        val maxTop = max(0, maxHeight - childHeight).toFloat()

        // Derive wrapper-top-in-viewport purely from the cached content offset and
        // the current scroll value — no per-frame window traversal needed.
        val wrapperTop = wrapperScrollTop - sv.scrollY
        val wrapperBottom = wrapperTop + height

        val newTop = when {
            wrapperTop >= topOffset -> 0f
            wrapperBottom <= topOffset + childHeight -> maxTop
            else -> (topOffset - wrapperTop).toFloat()
        }.coerceIn(0f, maxTop)

        if (abs(newTop - child.translationY) > 0.5f) {
            child.translationY = newTop
        }
    }

    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density).toInt()

    public companion object {
        private const val DEFAULT_MAX_HEIGHT_DP = 600
    }
}
