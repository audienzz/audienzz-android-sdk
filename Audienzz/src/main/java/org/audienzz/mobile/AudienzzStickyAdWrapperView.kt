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
import java.util.WeakHashMap
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
    /**
     * Reserved height in dp. Pass `null` (or omit) to use the backend-configured value
     * or the SDK default (600 dp). A non-null value always wins over the backend setting.
     */
    maxHeightDp: Int? = null,
) : FrameLayout(context, attrs, defStyleAttr) {

    // Explicit publisher override in pixels. null = use remote config or SDK default.
    private var maxHeightOverridePx: Int? = maxHeightDp?.let { dpToPx(it) }

    // Backend-derived values populated by applyRemoteConfig().
    private var remoteMaxHeightDp: Int? = null
    private var remoteStickyTopOffsetDp: Int? = null

    /** Resolved reserved height in pixels: publisher override → remote config → SDK default. */
    private val effectiveMaxHeight: Int
        get() = maxHeightOverridePx ?: dpToPx(remoteMaxHeightDp ?: DEFAULT_MAX_HEIGHT_DP)

    /**
     * Height reserved in the layout in pixels.
     *
     * Getter returns the resolved effective height (publisher override → remote config → 600 dp).
     * Setter stores an explicit publisher override, superseding remote-config values.
     * Pass `null` via the constructor's `maxHeightDp` parameter to opt in to remote-config control.
     */
    public var maxHeight: Int
        get() = effectiveMaxHeight
        set(value) {
            maxHeightOverridePx = value
            requestLayout()
        }

    /**
     * Y offset (pixels) from the top of the visible scroll viewport where the ad sticks.
     * `null` → use the backend-configured value; if absent there too, resolves to 0.
     */
    public var stickyTopOffset: Int? = null

    /**
     * Remote ad-unit config ID. When set the SDK reads `stickyMaxHeight` and
     * `stickyTopOffset` from the cached remote config and applies them as fallback
     * values (publisher overrides in the constructor or via setters still win).
     */
    public var adConfigId: String? = null
        set(value) {
            field = value
            applyRemoteConfig()
        }

    /** Whether sticky behaviour is active. When `false` the child stays at position 0. */
    public var isStickyEnabled: Boolean = true
        set(value) {
            field = value
            updatePosition()
        }

    /**
     * Optional performance gate: when enabled, skips sticky calculations while the wrapper
     * is far outside the visible viewport. Disabled by default.
     */
    var isVisibilityGateEnabled: Boolean = false

    private var adView: View? = null
    private var adViewLayoutListener: View.OnLayoutChangeListener? = null
    private var scrollViewRef: ViewGroup? = null
    private var attachedNestedScrollView: NestedScrollView? = null
    private var scrollChangedListener: ViewTreeObserver.OnScrollChangedListener? = null
    private var globalLayoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null
    private var cachedTopInContent: Int = Int.MIN_VALUE
    private var isSettleTickerRunning = false
    private var lastTickerScrollY = Int.MIN_VALUE
    private var stableTickerFrames = 0
    private val tempRect = Rect()
    private val settleTicker = object : Runnable {
        override fun run() {
            val scrollView = scrollViewRef ?: run {
                stopSettleTicker()
                return
            }

            updatePosition()

            // Read scrollY AFTER updatePosition() so the stability check uses
            // the same value that was just consumed, rather than the value from
            // the previous Choreographer frame (the ticker runs in the Animation
            // phase, before computeScroll() updates scrollY in the Traversal
            // phase, so reading before updatePosition() would always be 1 frame
            // stale and cause premature stable-frame counts during fling).
            val currentY = scrollView.scrollY
            if (currentY != lastTickerScrollY) {
                lastTickerScrollY = currentY
                stableTickerFrames = 0
            } else {
                stableTickerFrames += 1
            }

            if (stableTickerFrames >= 8) {
                stopSettleTicker()
            } else {
                postOnAnimation(this)
            }
        }
    }

    // MARK: - Public API

    /** Sets the ad view to be made sticky. Replaces any previously set ad view. */
    public fun setAdView(view: View) {
        adViewLayoutListener?.let { listener ->
            adView?.removeOnLayoutChangeListener(listener)
        }
        removeAllViews()
        adView = view
        val layoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            refreshTopCache()
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
        attachedNestedScrollView = scrollView
        addGlobalLayoutListener()
        refreshTopCache()
        registerNestedWrapper(scrollView, this)
        updatePosition()
    }

    /** Attaches sticky scroll tracking to a [android.widget.ScrollView]. */
    public fun attachToScrollView(scrollView: android.widget.ScrollView) {
        detachFromScrollView()
        scrollViewRef = scrollView
        addGlobalLayoutListener()
        refreshTopCache()

        val listener = ViewTreeObserver.OnScrollChangedListener {
            updatePosition()
            startSettleTicker()
        }
        scrollChangedListener = listener
        scrollView.viewTreeObserver.addOnScrollChangedListener(listener)
        updatePosition()
    }

    /** Detaches from the current scroll view, stopping scroll-driven updates. */
    public fun detachFromScrollView() {
        attachedNestedScrollView?.let { nested ->
            unregisterNestedWrapper(nested, this)
        }
        attachedNestedScrollView = null

        scrollChangedListener?.let {
            scrollViewRef?.viewTreeObserver?.removeOnScrollChangedListener(it)
        }
        scrollChangedListener = null
        removeGlobalLayoutListener()
        scrollViewRef = null
        cachedTopInContent = Int.MIN_VALUE
        stopSettleTicker()
    }

    // MARK: - Overrides

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Always measure to exactly effectiveMaxHeight so the space is reserved in the layout.
        super.onMeasure(
            widthMeasureSpec,
            MeasureSpec.makeMeasureSpec(effectiveMaxHeight, MeasureSpec.EXACTLY),
        )
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (changed) {
            refreshTopCache()
        }
        updatePosition()
    }

    override fun onDetachedFromWindow() {
        adViewLayoutListener?.let { listener ->
            adView?.removeOnLayoutChangeListener(listener)
        }
        adViewLayoutListener = null
        super.onDetachedFromWindow()
        // Stop active animations — the window is gone so we can't post frames.
        stopSettleTicker()
        // Remove the global layout listener — its VTO dies with the window.
        // Keep scrollViewRef / attachedNestedScrollView intact so that
        // onAttachedToWindow can re-subscribe when the view returns on screen
        // (e.g. bottom-nav tab switch with retained views).
        removeGlobalLayoutListener()
        // Regular ScrollView scroll listener is VTO-based — remove and re-add on reattach.
        scrollChangedListener?.let {
            scrollViewRef?.viewTreeObserver?.removeOnScrollChangedListener(it)
        }
        scrollChangedListener = null
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // Re-wire the adView layout listener (was removed in onDetachedFromWindow).
        adView?.let { view ->
            val layoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                refreshTopCache()
                updatePosition()
            }
            adViewLayoutListener = layoutListener
            view.addOnLayoutChangeListener(layoutListener)
        }
        // Re-subscribe to NestedScrollView scroll events.
        attachedNestedScrollView?.let { scrollView ->
            addGlobalLayoutListener()
            registerNestedWrapper(scrollView, this)
            refreshTopCache()
            updatePosition()
        }
        // Re-subscribe to regular ScrollView scroll events.
        (scrollViewRef as? android.widget.ScrollView)?.let { scrollView ->
            val listener = ViewTreeObserver.OnScrollChangedListener {
                updatePosition()
                startSettleTicker()
            }
            scrollChangedListener = listener
            scrollView.viewTreeObserver.addOnScrollChangedListener(listener)
        }
    }

    // MARK: - Private

    private fun applyRemoteConfig() {
        val configId = adConfigId ?: run {
            remoteMaxHeightDp = null
            remoteStickyTopOffsetDp = null
            return
        }
        // getAdUnitConfig reads from the local cache (populated during SDK init)
        // and calls back on the main thread — no network request at this point.
        AudienzzPrebidMobile.getAdUnitConfig(configId) { config ->
            remoteMaxHeightDp = config?.config?.stickyMaxHeight
            remoteStickyTopOffsetDp = config?.config?.stickyTopOffset
            requestLayout()
            updatePosition()
        }
    }

    private fun updatePosition() {
        if (!isStickyEnabled) {
            adView?.translationY = 0f
            return
        }
        val scrollView = scrollViewRef ?: return
        val child = adView ?: return

        val maxH = effectiveMaxHeight
        val topOffset = stickyTopOffset ?: dpToPx(remoteStickyTopOffsetDp ?: 0)
        val childHeight = child.height.takeIf { it > 0 } ?: maxH
        val maxTop = max(0, maxH - childHeight).toFloat()

        // Use one consistent coordinate system for both drag and fling.
        val wrapperTop = resolveTopInContent(scrollView) - scrollView.scrollY
        val wrapperBottom = wrapperTop + height

        if (isVisibilityGateEnabled) {
            val viewportHeight = scrollView.height
            if (wrapperBottom < -viewportHeight || wrapperTop > viewportHeight * 2) {
                return
            }
        }

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

    private fun resolveTopInContent(scrollView: ViewGroup): Int {
        if (cachedTopInContent == Int.MIN_VALUE) {
            cachedTopInContent = computeTopInContent(scrollView)
        }
        return cachedTopInContent
    }

    private fun refreshTopCache() {
        val scrollView = scrollViewRef ?: return
        cachedTopInContent = computeTopInContent(scrollView)
    }

    private fun addGlobalLayoutListener() {
        if (globalLayoutListener != null) return
        val listener = ViewTreeObserver.OnGlobalLayoutListener {
            refreshTopCache()
            updatePosition()
        }
        globalLayoutListener = listener
        viewTreeObserver.addOnGlobalLayoutListener(listener)
    }

    private fun removeGlobalLayoutListener() {
        val listener = globalLayoutListener ?: return
        if (viewTreeObserver.isAlive) {
            viewTreeObserver.removeOnGlobalLayoutListener(listener)
        }
        globalLayoutListener = null
    }

    private fun startSettleTicker() {
        // Always reset the stable-frame counter on each scroll event so that
        // a briefly-unchanged integer scrollY during fling deceleration does
        // not cause the ticker to stop prematurely (Bug: ticker ran in the
        // Animation phase before computeScroll() updated scrollY, causing
        // stableTickerFrames to increment even while the fling was active).
        stableTickerFrames = 0
        lastTickerScrollY = Int.MIN_VALUE
        if (isSettleTickerRunning) return
        isSettleTickerRunning = true
        postOnAnimation(settleTicker)
    }

    private fun stopSettleTicker() {
        if (!isSettleTickerRunning) return
        isSettleTickerRunning = false
        removeCallbacks(settleTicker)
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

        private val nestedWrappers:
            WeakHashMap<NestedScrollView, MutableSet<AudienzzStickyAdWrapperView>> = WeakHashMap()

        private fun registerNestedWrapper(
            scrollView: NestedScrollView,
            wrapper: AudienzzStickyAdWrapperView,
        ) {
            val isFirstWrapper = !nestedWrappers.containsKey(scrollView)
            val wrappers = nestedWrappers.getOrPut(scrollView) { mutableSetOf() }
            wrappers.add(wrapper)

            // Only set the scroll listener once per scroll view — it reads the wrapper
            // set dynamically from nestedWrappers, so all wrappers registered later are
            // automatically included without replacing the listener.
            if (isFirstWrapper) {
                scrollView.setOnScrollChangeListener { _: NestedScrollView, _: Int, _: Int, _: Int, _: Int ->
                    val current = nestedWrappers[scrollView] ?: return@setOnScrollChangeListener
                    if (current.isEmpty()) return@setOnScrollChangeListener
                    current.forEach {
                        it.updatePosition()
                        it.startSettleTicker()
                    }
                }
            }
        }

        private fun unregisterNestedWrapper(
            scrollView: NestedScrollView,
            wrapper: AudienzzStickyAdWrapperView,
        ) {
            val wrappers = nestedWrappers[scrollView] ?: return
            wrappers.remove(wrapper)
            if (wrappers.isEmpty()) {
                scrollView.setOnScrollChangeListener(null as NestedScrollView.OnScrollChangeListener?)
                nestedWrappers.remove(scrollView)
            }
        }
    }
}
