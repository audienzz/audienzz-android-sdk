package org.audienzz.mobile

import android.content.Context
import android.util.AttributeSet
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.view.ViewParent
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
    private var adViewLayoutListener: View.OnLayoutChangeListener? = null
    private var scrollViewRef: ViewGroup? = null
    private var nestedScrollViewRef: NestedScrollView? = null
    private var platformScrollViewRef: android.widget.ScrollView? = null
    private var scrollChangedListener: ViewTreeObserver.OnScrollChangedListener? = null
    private val tempRect = Rect()

    // MARK: - Public API

    /** Sets the ad view to be made sticky. Replaces any previously set ad view. */
    public fun setAdView(view: View) {
        adViewLayoutListener?.let { listener ->
            adView?.removeOnLayoutChangeListener(listener)
        }
        removeAllViews()
        adView = view
        val layoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updatePosition()
        }
        adViewLayoutListener = layoutListener
        view.addOnLayoutChangeListener(layoutListener)
        addView(view, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    /** Attaches sticky scroll tracking to a [NestedScrollView]. */
    public fun attachToScrollView(scrollView: NestedScrollView) {
        detachFromScrollView()
        scrollViewRef = scrollView
        nestedScrollViewRef = scrollView
        scrollView.setOnScrollChangeListener { _: NestedScrollView, _: Int, _: Int, _: Int, _: Int ->
            updatePosition()
        }
        updatePosition()
    }

    /** Attaches sticky scroll tracking to a [android.widget.ScrollView]. */
    public fun attachToScrollView(scrollView: android.widget.ScrollView) {
        detachFromScrollView()
        scrollViewRef = scrollView
        platformScrollViewRef = scrollView

        val listener = ViewTreeObserver.OnScrollChangedListener {
            updatePosition()
        }
        scrollChangedListener = listener
        scrollView.viewTreeObserver.addOnScrollChangedListener(listener)
        updatePosition()
    }

    /** Detaches from the current scroll view, stopping scroll-driven updates. */
    public fun detachFromScrollView() {
        nestedScrollViewRef?.setOnScrollChangeListener(null as NestedScrollView.OnScrollChangeListener?)
        nestedScrollViewRef = null

        scrollChangedListener?.let {
            platformScrollViewRef?.viewTreeObserver?.removeOnScrollChangedListener(it)
        }
        scrollChangedListener = null
        platformScrollViewRef = null
        scrollViewRef = null

        adViewLayoutListener?.let { listener ->
            adView?.removeOnLayoutChangeListener(listener)
        }
        adViewLayoutListener = null
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
        updatePosition()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        detachFromScrollView()
    }

    // MARK: - Private

    private fun updatePosition() {
        if (!isStickyEnabled) {
            adView?.translationY = 0f
            return
        }
        val scrollView = scrollViewRef ?: return
        val child = adView ?: return

        val topOffset = stickyTopOffset ?: 0
        val childHeight = child.height.takeIf { it > 0 } ?: maxHeight
        val maxTop = max(0, maxHeight - childHeight).toFloat()

        // Flutter-equivalent formula: wrapperTop = contentTop - scrollY.
        val wrapperTop = computeTopInContent(scrollView) - scrollView.scrollY
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

    private fun computeTopInContent(scrollView: ViewGroup): Int {
        if (!isDescendantOf(scrollView)) {
            return top + scrollView.scrollY
        }
        tempRect.set(0, 0, width, height)
        scrollView.offsetDescendantRectToMyCoords(this, tempRect)
        return tempRect.top
    }

    private fun isDescendantOf(ancestor: ViewGroup): Boolean {
        var current: ViewParent? = parent
        while (current != null) {
            if (current === ancestor) return true
            current = current.parent
        }
        return false
    }

    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density).toInt()

    public companion object {
        private const val DEFAULT_MAX_HEIGHT_DP = 600
    }
}
