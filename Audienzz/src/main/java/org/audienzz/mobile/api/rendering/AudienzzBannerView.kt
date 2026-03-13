package org.audienzz.mobile.api.rendering

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.view.ViewTreeObserver
import android.webkit.WebView
import android.widget.FrameLayout
import org.audienzz.mobile.AudienzzAdSize
import org.audienzz.mobile.api.data.AudienzzAdPosition
import org.audienzz.mobile.api.data.AudienzzVideoPlacementType
import org.audienzz.mobile.api.exceptions.AudienzzAdException
import org.audienzz.mobile.api.rendering.listeners.AudienzzBannerVideoListener
import org.audienzz.mobile.api.rendering.listeners.AudienzzBannerViewListener
import org.audienzz.mobile.api.rendering.pluginrenderer.AudienzzPluginEventListener
import org.audienzz.mobile.rendering.bidding.data.bid.AudienzzBid
import org.audienzz.mobile.rendering.bidding.data.bid.AudienzzBidResponse
import org.audienzz.mobile.rendering.bidding.listeners.AudienzzBannerEventHandler
import org.audienzz.mobile.rendering.bidding.listeners.AudienzzBannerEventListener
import org.audienzz.mobile.util.addContinuousVisibilityListener
import org.audienzz.mobile.util.addOnBecameVisibleOnScreenListenerWithMargin
import org.audienzz.mobile.util.dpToPx
import org.prebid.mobile.AdSize
import org.prebid.mobile.api.exceptions.AdException
import org.prebid.mobile.api.rendering.BannerView
import org.prebid.mobile.api.rendering.listeners.BannerVideoListener
import org.prebid.mobile.api.rendering.listeners.BannerViewListener
import org.prebid.mobile.rendering.bidding.data.bid.Bid
import org.prebid.mobile.rendering.bidding.interfaces.BannerEventHandler
import org.prebid.mobile.rendering.bidding.listeners.BannerEventListener

@Suppress("TooManyFunctions")
class AudienzzBannerView internal constructor(
    internal val prebidBannerView: BannerView,
) {

    val view: FrameLayout = prebidBannerView

    val autoRefreshDelayInMs: Int = prebidBannerView.autoRefreshDelayInMs

    val additionalSizes: Set<AudienzzAdSize> =
        prebidBannerView.additionalSizes.map { AudienzzAdSize(it) }.toSet()

    var videoPlacementType: AudienzzVideoPlacementType?
        get() = prebidBannerView.videoPlacementType?.let {
            AudienzzVideoPlacementType.fromPrebidVideoPlacementType(it)
        }
        set(value) {
            prebidBannerView.videoPlacementType = value?.prebidVideoPlacementType
        }

    var adPosition: AudienzzAdPosition
        get() = AudienzzAdPosition.fromPrebidAdPosition(prebidBannerView.adPosition)
        set(value) {
            prebidBannerView.adPosition = value.prebidAdPosition
        }

    var pbAdSlot: String?
        get() = prebidBannerView.pbAdSlot
        set(value) {
            prebidBannerView.pbAdSlot = value
        }

    var impOrtbConfig: String?
        get() = prebidBannerView.impOrtbConfig
        set(value) {
            prebidBannerView.impOrtbConfig = value
        }

    val bidResponse: AudienzzBidResponse? =
        prebidBannerView.bidResponse?.let { AudienzzBidResponse(it) }

    /**
     * When true, pauses Prebid autorefresh and ad playback while the banner is off-screen,
     * and resumes both when it returns to the viewport.
     *
     * Default: false (opt-in). Publishers should enable once smart refresh behaviour is verified.
     */
    var smartRefresh: Boolean = false
        set(value) {
            field = value
            if (value && prebidBannerView.isAttachedToWindow) {
                attachSmartRefreshListener()
            } else if (!value) {
                detachSmartRefreshListener()
            }
        }

    /**
     * Distance in dp below the screen bottom at which the Prebid bid request is triggered
     * before the view scrolls fully into the viewport. 0 = disabled (fire at viewport edge).
     *
     * Example: 150 starts the auction ~150dp before the ad enters view.
     */
    var prefetchMarginTopDp: Int = 0

    private var scrollListener: ViewTreeObserver.OnScrollChangedListener? = null

    /**
     * Saved autorefresh delay (in seconds) when smart refresh pauses the timer.
     * Null means refresh is not currently paused by us.
     */
    private var pausedRefreshDelaySec: Int? = null

    init {
        prebidBannerView.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                if (smartRefresh) attachSmartRefreshListener()
            }

            override fun onViewDetachedFromWindow(v: View) {
                detachSmartRefreshListener()
            }
        })
    }

    private fun attachSmartRefreshListener() {
        if (scrollListener != null) return
        Log.d(TAG, "[SmartRefresh] Listener attached to BannerView")
        scrollListener = prebidBannerView.addContinuousVisibilityListener(
            onBecameVisible = ::onBecameVisible,
            onBecameHidden = ::onBecameHidden,
        )
    }

    private fun detachSmartRefreshListener() {
        scrollListener?.let {
            if (prebidBannerView.isAttachedToWindow) {
                prebidBannerView.viewTreeObserver.removeOnScrollChangedListener(it)
            }
            scrollListener = null
            Log.d(TAG, "[SmartRefresh] Listener detached from BannerView")
        }
    }

    private fun onBecameVisible() {
        Log.d(TAG, "[SmartRefresh] → VISIBLE: resumeAutoRefresh + resumeAdPlayback")
        val delay = pausedRefreshDelaySec
        if (delay != null) {
            pausedRefreshDelaySec = null
            prebidBannerView.setAutoRefreshDelay(delay)
            Log.d(TAG, "[SmartRefresh] Restored autoRefreshDelay=$delay sec")
        }
        resumeAdPlayback()
    }

    private fun onBecameHidden() {
        Log.d(TAG, "[SmartRefresh] → HIDDEN: stopAutoRefresh + pauseAdPlayback")
        val currentDelayMs = prebidBannerView.autoRefreshDelayInMs
        if (currentDelayMs > 0 && pausedRefreshDelaySec == null) {
            pausedRefreshDelaySec = currentDelayMs / 1000
            prebidBannerView.stopRefresh()
            Log.d(TAG, "[SmartRefresh] Saved autoRefreshDelay=${pausedRefreshDelaySec}sec and stopped refresh")
        }
        pauseAdPlayback()
    }

    private fun pauseAdPlayback() {
        var webViewCount = 0
        for (i in 0 until prebidBannerView.childCount) {
            (prebidBannerView.getChildAt(i) as? WebView)?.let {
                it.onPause()
                webViewCount++
            }
        }
        Log.d(TAG, "[SmartRefresh] pauseAdPlayback: paused $webViewCount WebView(s)")
    }

    private fun resumeAdPlayback() {
        var webViewCount = 0
        for (i in 0 until prebidBannerView.childCount) {
            (prebidBannerView.getChildAt(i) as? WebView)?.let {
                it.onResume()
                webViewCount++
            }
        }
        Log.d(TAG, "[SmartRefresh] resumeAdPlayback: resumed $webViewCount WebView(s)")
    }

    /**
     * Instantiates an BannerView with the ad details as an attribute.
     *
     * @param attrs includes:
     * <p>
     * adUnitID
     * refreshIntervalInSec
     */
    constructor(context: Context, attrs: AttributeSet?) : this(BannerView(context, attrs))

    /**
     * Instantiates an BannerView for the given configId and adSize.
     */
    constructor(
        context: Context,
        configId: String,
        adSize: AudienzzAdSize,
    ) : this(
        BannerView(
            context,
            configId,
            adSize.adSize,
        ),
    )

    /**
     * Instantiates an BannerView for GAM prebid integration.
     */
    constructor(
        context: Context,
        configId: String,
        eventHandler: AudienzzBannerEventHandler,
    ) : this(
        BannerView(
            context,
            configId,
            getBannerEventHandler(eventHandler),
        ),
    )

    /**
     * Executes ad loading if no request is running.
     *
     * @param lazyLoad allows to postpone loadAd call until view is visible on screen.
     * When combined with [prefetchMarginTopDp] > 0, the bid request fires before the view
     * fully enters the viewport, reducing latency for the user.
     */
    fun loadAd(lazyLoad: Boolean = false) {
        if (lazyLoad) {
            val marginPx = prebidBannerView.resources.dpToPx(prefetchMarginTopDp)
            prebidBannerView.addOnBecameVisibleOnScreenListenerWithMargin(marginPx) {
                prebidBannerView.loadAd()
            }
        } else {
            prebidBannerView.loadAd()
        }
    }

    /**
     * Cancels BidLoader refresh timer.
     */
    fun stopRefresh() {
        prebidBannerView.stopRefresh()
    }

    /**
     * Cleans up resources when destroyed.
     */
    fun destroy() {
        prebidBannerView.destroy()
    }

    fun setAutoRefreshDelay(seconds: Int) {
        prebidBannerView.setAutoRefreshDelay(seconds)
    }

    @Suppress("SpreadOperator")
    fun addAdditionalSizes(vararg sizes: AudienzzAdSize) {
        prebidBannerView.addAdditionalSizes(*sizes.map { it.adSize }.toTypedArray())
    }

    @Suppress("SpreadOperator")
    fun addAdditionalSizes(sizes: List<AudienzzAdSize>) {
        prebidBannerView.addAdditionalSizes(*sizes.map { it.adSize }.toTypedArray())
    }

    fun setBannerListener(bannerListener: AudienzzBannerViewListener) {
        prebidBannerView.setBannerListener(getBannerViewListener(bannerListener))
    }

    fun setBannerVideoListener(bannerVideoListener: AudienzzBannerVideoListener) {
        prebidBannerView.setBannerVideoListener(getBannerVideoListener(bannerVideoListener))
    }

    fun setPluginEventListener(pluginEventListener: AudienzzPluginEventListener) {
        prebidBannerView.setPluginEventListener { pluginEventListener.getPluginRendererName() }
    }

    /**
     * Sets BannerEventHandler for GAM prebid integration
     *
     * @param eventHandler instance of GamBannerEventHandler
     */
    fun setEventHandler(eventHandler: AudienzzBannerEventHandler) {
        prebidBannerView.setEventHandler(
            getBannerEventHandler(eventHandler),
        )
    }

    companion object {

        private const val TAG = "AudienzzBannerView"

        private fun getBannerEventHandler(eventHandler: AudienzzBannerEventHandler) =
            object : BannerEventHandler {
                override fun getAdSizeArray(): Array<AdSize> =
                    eventHandler.getAdSizeArray().map { it.adSize }.toTypedArray()

                override fun setBannerEventListener(bannerViewListener: BannerEventListener) {
                    eventHandler.setBannerEventListener(
                        getBannerEventListener(bannerViewListener),
                    )
                }

                override fun requestAdWithBid(bid: Bid?) {
                    eventHandler.requestAdWithBid(bid?.let { AudienzzBid(it) })
                }

                override fun trackImpression() {
                    eventHandler.trackImpression()
                }

                override fun destroy() {
                    eventHandler.destroy()
                }
            }

        private fun getBannerEventListener(listener: BannerEventListener) =
            object : AudienzzBannerEventListener {
                override fun onPrebidSdkWin() {
                    listener.onPrebidSdkWin()
                }

                override fun onAdServerWin(view: View?) {
                    listener.onAdServerWin(view)
                }

                override fun onAdFailed(exception: AudienzzAdException?) {
                    listener.onAdFailed(exception?.prebidAdException)
                }

                override fun onAdClicked() {
                    listener.onAdClicked()
                }

                override fun onAdClosed() {
                    listener.onAdClosed()
                }
            }

        private fun getBannerViewListener(listener: AudienzzBannerViewListener) =
            object : BannerViewListener {
                override fun onAdLoaded(bannerView: BannerView?) {
                    listener.onAdLoaded(bannerView?.let { AudienzzBannerView(it) })
                }

                override fun onAdDisplayed(bannerView: BannerView?) {
                    listener.onAdDisplayed(bannerView?.let { AudienzzBannerView(it) })
                }

                override fun onAdFailed(bannerView: BannerView?, exception: AdException?) {
                    listener.onAdFailed(
                        bannerView?.let { AudienzzBannerView(it) },
                        exception?.let { AudienzzAdException(it) },
                    )
                }

                override fun onAdClicked(bannerView: BannerView?) {
                    listener.onAdClicked(bannerView?.let { AudienzzBannerView(it) })
                }

                override fun onAdClosed(bannerView: BannerView?) {
                    listener.onAdClosed(bannerView?.let { AudienzzBannerView(it) })
                }
            }

        private fun getBannerVideoListener(listener: AudienzzBannerVideoListener) =
            object : BannerVideoListener {
                override fun onVideoCompleted(bannerView: BannerView?) {
                    listener.onVideoCompleted(bannerView?.let { AudienzzBannerView(it) })
                }

                override fun onVideoPaused(bannerView: BannerView?) {
                    listener.onVideoPaused(bannerView?.let { AudienzzBannerView(it) })
                }

                override fun onVideoResumed(bannerView: BannerView?) {
                    listener.onVideoResumed(bannerView?.let { AudienzzBannerView(it) })
                }

                override fun onVideoUnMuted(bannerView: BannerView?) {
                    listener.onVideoUnMuted(bannerView?.let { AudienzzBannerView(it) })
                }

                override fun onVideoMuted(bannerView: BannerView?) {
                    listener.onVideoMuted(bannerView?.let { AudienzzBannerView(it) })
                }
            }
    }
}
