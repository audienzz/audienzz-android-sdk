package org.audienzz.mobile

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.Gravity
import android.widget.FrameLayout
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.admanager.AdManagerAdView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.audienzz.mobile.addentum.AudienzzAdViewUtils
import org.audienzz.mobile.api.config.RemoteAdUnitConfig
import org.audienzz.mobile.di.MainComponent
import org.audienzz.mobile.original.AudienzzAdViewHandler
import org.audienzz.mobile.util.pxToDp

@SuppressLint("ViewConstructor")
class AudienzzRemoteBannerView @JvmOverloads constructor(
    context: Context,
    private val adConfigId: String,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val scope = CoroutineScope(
        Dispatchers.Main + SupervisorJob() + CoroutineExceptionHandler { _, throwable ->
            Log.e(TAG, "CoroutineScope exception", throwable)
        },
    )

    /** Stable logical placement, shared with replacement handlers. */
    var requestContext = org.audienzz.mobile.targeting.AudienzzAdRequestContext()

    private var adUnit: AudienzzBannerAdUnit? = null
    private var adView: AdManagerAdView? = null
    private var adViewHandler: AudienzzAdViewHandler? = null
    private var externalAdListener: AdListener? = null
    private var pendingScreenKey: Any? = null

    /**
     * Publisher state requested before the ad handler existed.
     *
     * Remote config is fetched asynchronously, so a host can legitimately stop or cover this banner
     * while [adViewHandler] is still null. Forwarding through a nullable handler silently dropped
     * those calls, and the handler that arrived afterwards held neither — so a banner the publisher
     * had stopped went on refreshing, and a reported cover was never applied.
     */
    private var pendingPublisherStop = false
    private var pendingHostCover = false

    // Delivery settings. Lazy loading and the prefetch margin are backend-driven only: the ad
    // config's `lazyLoad` and `prefetchDistanceDp`, else the SDK defaults. There is deliberately no
    // publisher override, so one placement behaves the same in every app and on every platform.

    /** Resolved lazy-load setting: the ad config, then [DEFAULT_LAZY_LOAD]. */
    internal fun resolveLazyLoad(config: RemoteAdUnitConfig): Boolean =
        config.config.lazyLoad ?: DEFAULT_LAZY_LOAD

    /** Resolved prefetch margin in dp: the ad config's `prefetchDistanceDp`, then 200 dp. */
    internal fun resolvePrefetchMarginDp(config: RemoteAdUnitConfig): Int =
        config.config.prefetchDistanceDp ?: DEFAULT_PREFETCH_DISTANCE_DP

    /**
     * Associate this banner with a screen the SDK can't infer from the view tree — a Jetpack Compose
     * destination, or a custom navigation model. Pass the same token you report to
     * `AudienzzPrebidMobile.pageImpression(token)` (typically the route key `String`); on that
     * screen's `pageImpression` this banner reloads, and it pauses on every other. Call it before or
     * after `loadAd()` — the handler is created asynchronously, so the key is applied when ready.
     * Not needed for Activity/Fragment/ViewPager2 hosts (those are resolved automatically).
     */
    fun setScreen(screenKey: Any) {
        pendingScreenKey = screenKey
        adViewHandler?.hostScreenOverride = screenKey
    }

    init {
        layoutParams = LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.WRAP_CONTENT,
            Gravity.CENTER,
        )
    }

    fun loadAd() {
        // Reserve before remote config resolves, so network completion cannot reorder slots.
        val active = org.audienzz.mobile.screen.screenAdCoordinator?.activeScreen
        if (pendingScreenKey == null || active == null || pendingScreenKey == active) requestContext.register()
        loadBannerInternal()
    }

    fun destroy() {
        // H1: route through the handler so Prebid's ad unit is actually destroyed (its BidLoader
        // torn down), not merely paused. Fall back to stopping/destroying the unit directly if the
        // handler was never created (config load failed before createAdFromConfig).
        val handler = adViewHandler
        if (handler != null) {
            handler.destroy()
        } else {
            adUnit?.stopAutoRefresh()
            adUnit?.destroy()
        }
        adViewHandler = null
        pendingScreenKey = null
        adUnit = null
        adView?.destroy()
        adView = null
        removeAllViews()
        scope.cancel()
    }

    /**
     * Visibility resume, for a host that tracks it itself. Clears only the visibility reason, so a
     * publisher pause or a released page survives; the refresh controller decides whether the
     * banner is overdue or should wait out the remainder of its interval.
     */
    fun onResume() {
        adViewHandler?.resumeSmartRefresh()
    }

    /**
     * Applies whatever the host asked for while the handler was still being built.
     *
     * The stop goes on FIRST, before [AudienzzAdViewHandler.load] can request: installing it after
     * the load call would let an eager banner issue one request the publisher had already stopped.
     */
    private fun applyPendingPublisherState(handler: AudienzzAdViewHandler) {
        if (pendingPublisherStop) {
            handler.stopAutoRefresh()
        }
        if (pendingHostCover) {
            handler.pauseForHostCover()
        }
    }

    /** Visibility pause: the banner is off screen, so a refresh into it would go unseen. */
    fun onPause() {
        adViewHandler?.pauseSmartRefresh()
    }

    /**
     * A cover the SDK cannot infer, reported by a host that tracks it itself.
     *
     * Its own hold, independent of [onPause]/[onResume]: a scroll must not clear a cover, and
     * clearing a cover must not clear an offscreen hold.
     */
    fun setHostCover(covered: Boolean) {
        pendingHostCover = covered
        if (covered) {
            adViewHandler?.pauseForHostCover()
        } else {
            adViewHandler?.resumeFromHostCover()
        }
    }

    /**
     * Publisher pause. Durable and independent of [onPause]: nothing else clears it — not a scroll
     * back into view, not a page impression, not a return to the foreground. Only
     * [resumeAutoRefresh] does.
     */
    fun stopAutoRefresh() {
        pendingPublisherStop = true
        adViewHandler?.stopAutoRefresh()
    }

    /**
     * Clears the publisher pause. Refresh actually resumes only once nothing else is holding it.
     */
    fun resumeAutoRefresh() {
        pendingPublisherStop = false
        adViewHandler?.resumeAutoRefresh()
    }

    /**
     * Force a fresh auction now on the underlying banner, ignoring the stale-aware refresh timing.
     * Forwards to [AudienzzAdViewHandler.reloadAd] — used by the RN/Flutter bridges to reload on
     * screen change, and for a manual reload. No-op until the underlying banner has been built.
     */
    fun reloadAd() {
        adViewHandler?.reloadAd()
    }

    fun setAdListener(listener: AdListener) {
        externalAdListener = listener
    }

    fun getAdSize(): AdSize? = adView?.adSize

    private fun loadBannerInternal() {
        scope.launch {
            try {
                val config = withContext(Dispatchers.IO) {
                    MainComponent.remoteConfigManager
                        ?.getAdUnitConfig(configId = adConfigId)
                }

                if (config == null) {
                    Log.e(TAG, "Remote config not found for id=$adConfigId")
                    return@launch
                }

                createAdFromConfig(config)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load remote banner configuration", e)
            }
        }
    }

    @Suppress("SpreadOperator")
    private fun createAdFromConfig(config: RemoteAdUnitConfig) {
        // M11: a second loadAd()/createAdFromConfig would otherwise orphan the previous ad unit and
        // ad view with their refresh loop still armed (a zombie loop loading a detached view, feeding
        // C1). Tear the predecessor down before building the replacement.
        adViewHandler?.destroy()
        adViewHandler = null
        adUnit = null
        adView?.destroy()
        adView = null
        removeAllViews()

        val gamConfig = config.gamConfig
        val prebidConfig = config.prebidConfig

        val sortedGamSizes = gamConfig.adSizes
            .sortedByDescending { it.width * it.height }
            .map { AdSize(it.width, it.height) }

        val sortedPrebidSizes = prebidConfig.adSizes
            .sortedByDescending { it.width * it.height }

        val prebidPrimarySize = sortedPrebidSizes.firstOrNull()

        // GAM sizes are required: without them nothing can render. Prebid sizes are not — a slot
        // with none is simply not in header bidding, and serves GAM-only. This used to refuse to
        // load at all, while iOS sent Prebid a 0x0 request that could never fill; both now serve
        // GAM without asking Prebid.
        if (sortedGamSizes.isEmpty()) {
            Log.e(TAG, "No GAM sizes in remote config for id=$adConfigId")
            return
        }
        val headerBidding = prebidPrimarySize != null
        if (!headerBidding) {
            Log.i(TAG, "No Prebid sizes in remote config for id=$adConfigId — serving GAM-only")
        }

        val adaptiveConfig = gamConfig.adaptiveBannerConfig
        val isAdaptiveEnabled = adaptiveConfig?.enabled == true

        val primaryGamSize: AdSize = if (isAdaptiveEnabled) {
            val widthPx = when (adaptiveConfig?.widthStrategy) {
                "fullWidth" -> maxOf(width, resources.displayMetrics.widthPixels)
                "custom" -> adaptiveConfig.customWidth ?: width
                else -> width
            }

            val widthDp = context.resources.pxToDp(widthPx)

            if (adaptiveConfig.maxHeight != null) {
                AdSize.getInlineAdaptiveBannerAdSize(
                    widthDp,
                    adaptiveConfig.maxHeight,
                )
            } else {
                AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(
                    context,
                    widthDp,
                )
            }
        } else {
            sortedGamSizes.first()
        }

        val adViewLocal = AdManagerAdView(context).apply {
            adUnitId = gamConfig.adUnitPath

            val finalSizes = buildList {
                add(primaryGamSize)
                if (isAdaptiveEnabled) {
                    if (adaptiveConfig?.isIncludeReservationSizes == true) {
                        addAll(sortedGamSizes)
                    }
                } else {
                    addAll(sortedGamSizes.drop(1))
                }
            }

            setAdSizes(finalSizes[0], *finalSizes.drop(1).toTypedArray())
            adListener = createAdListener()
        }

        adView = adViewLocal
        // Center the GAM view within this full-width (MATCH_PARENT) host. Without a gravity the
        // child defaults to TOP|START, so a creative narrower than the host (a 300-wide banner on a
        // wide/tablet screen, or a smaller multisize fill) renders left-aligned. This centers the
        // CREATIVE inside the host; the `layoutParams` set in `init` centers the host inside the
        // publisher's container — two different problems, both needed. Same fix as the RN bridge
        // and AURemoteConfigBannerView. (From origin/main, 4a304c9.)
        addView(
            adViewLocal,
            LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_HORIZONTAL,
            ),
        )

        val parameters = AudienzzBannerParameters().apply {
            api = listOf(
                AudienzzSignals.Api.MRAID_1,
                AudienzzSignals.Api.MRAID_2,
                AudienzzSignals.Api.MRAID_3,
                AudienzzSignals.Api.OMID_1,
            )
        }

        // With header bidding off the ad unit still carries the refresh interval and formats the
        // handler reads, but no request is ever made through it, so its size is never sent.
        val adUnitLocal = AudienzzBannerAdUnit(
            prebidConfig.placementId,
            prebidPrimarySize?.width ?: sortedGamSizes.first().width,
            prebidPrimarySize?.height ?: sortedGamSizes.first().height,
        ).apply {
            bannerParameters = parameters

            setAutoRefreshInterval(config.config.refreshTimeSeconds ?: DEFAULT_REFRESH_SECONDS)
        }

        adUnit = adUnitLocal

        val handler = AudienzzAdViewHandler(
            adView = adViewLocal,
            adUnit = adUnitLocal,
            requestContext = requestContext,
        )
        adViewHandler = handler
        handler.headerBiddingEnabled = headerBidding
        pendingScreenKey?.let { handler.hostScreenOverride = it }
        // Before load(): a stop requested while config was resolving must be in place before the
        // handler can issue its first request.
        applyPendingPublisherState(handler)
        handler.load(
            withLazyLoading = resolveLazyLoad(config),
            prefetchMarginDp = resolvePrefetchMarginDp(config),
        ) { request, resultCode ->
            Log.d(TAG, "Ad request prepared, resultCode=${resultCode ?: "unknown"}")
            adViewLocal.loadAd(request)
        }
        handler.enableSmartRefresh()
    }

    private fun createAdListener() = object : AdListener() {

        override fun onAdLoaded() {
            super.onAdLoaded()
            Log.d(TAG, "onAdLoaded")
            adView?.let { AudienzzAdViewUtils.hideScrollBar(it) }
            externalAdListener?.onAdLoaded()
        }

        override fun onAdFailedToLoad(error: LoadAdError) {
            super.onAdFailedToLoad(error)
            Log.e(TAG, "onAdFailedToLoad: $error")
            externalAdListener?.onAdFailedToLoad(error)
        }

        override fun onAdClicked() {
            super.onAdClicked()
            Log.d(TAG, "onAdClicked")
            externalAdListener?.onAdClicked()
        }

        override fun onAdOpened() {
            super.onAdOpened()
            Log.d(TAG, "onAdOpened")
            externalAdListener?.onAdOpened()
        }

        override fun onAdClosed() {
            super.onAdClosed()
            Log.d(TAG, "onAdClosed")
            externalAdListener?.onAdClosed()
        }

        override fun onAdImpression() {
            super.onAdImpression()
            Log.d(TAG, "onAdImpression")
            externalAdListener?.onAdImpression()
        }
    }

    companion object {
        private const val TAG = "AudienzzRemoteConfigBannerView"
        private const val DEFAULT_REFRESH_SECONDS = 30
        private const val DEFAULT_PREFETCH_DISTANCE_DP = 200

        /**
         * Remote-config banners defer their auction until the slot approaches the viewport unless
         * the ad config asks otherwise.
         *
         * This was briefly flipped to eager. That made every mounted placement auction on [loadAd]
         * regardless of position, so a publisher opening an article bought fills for below-fold
         * slots the reader might never approach — responses that can never become impressions,
         * which is the delivery pattern we are trying to reduce, not create. Eager remains
         * available per placement (`lazyLoad: false` on the ad config) for slots that are always
         * on screen.
         */
        internal const val DEFAULT_LAZY_LOAD = true
    }
}
