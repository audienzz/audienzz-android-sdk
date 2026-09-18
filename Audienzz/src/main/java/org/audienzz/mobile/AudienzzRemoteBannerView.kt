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

    private var adUnit: AudienzzBannerAdUnit? = null
    private var adView: AdManagerAdView? = null
    private var adViewHandler: AudienzzAdViewHandler? = null
    private var externalAdListener: AdListener? = null
    private var pendingScreenKey: Any? = null

    // Delivery overrides. Both resolve publisher override -> ad config -> SDK default, the same
    // precedence used by AudienzzPrebidMobile.smartRefreshV2Override. They are read when the ad
    // handler is built, so set them before loadAd(); changing one afterwards takes effect on the
    // next load.

    /**
     * Publisher override for lazy loading. null (default) defers to the ad config's `lazyLoad`,
     * which itself falls back to [DEFAULT_LAZY_LOAD].
     *
     * false auctions as soon as [loadAd] runs, wherever the slot sits. true defers the auction
     * until the slot comes within [prefetchMarginDpOverride] dp of the viewport.
     */
    var lazyLoadOverride: Boolean? = null

    /**
     * Publisher override for the prefetch margin, in dp. null (default) defers to the ad config's
     * `prefetchDistanceDp`, which itself falls back to 200 dp. Only has an effect while lazy
     * loading is on.
     */
    var prefetchMarginDpOverride: Int? = null

    /** Resolved lazy-load setting: publisher override, then the ad config, then the SDK default. */
    internal fun resolveLazyLoad(config: RemoteAdUnitConfig): Boolean =
        lazyLoadOverride ?: config.config.lazyLoad ?: DEFAULT_LAZY_LOAD

    /** Resolved prefetch margin in dp: publisher override, then the ad config, then 200 dp. */
    internal fun resolvePrefetchMarginDp(config: RemoteAdUnitConfig): Int =
        prefetchMarginDpOverride ?: config.config.prefetchDistanceDp ?: DEFAULT_PREFETCH_DISTANCE_DP

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

    /** Visibility pause: the banner is off screen, so a refresh into it would go unseen. */
    fun onPause() {
        adViewHandler?.pauseSmartRefresh()
    }

    /**
     * Publisher pause. Durable and independent of [onPause]: nothing else clears it — not a scroll
     * back into view, not a page impression, not a return to the foreground. Only
     * [resumeAutoRefresh] does.
     */
    fun stopAutoRefresh() {
        adViewHandler?.stopAutoRefresh()
    }

    /**
     * Clears the publisher pause. Refresh actually resumes only once nothing else is holding it.
     */
    fun resumeAutoRefresh() {
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

        if (sortedGamSizes.isEmpty() || prebidPrimarySize == null) {
            Log.e(TAG, "No valid sizes in remote config for id=$adConfigId")
            return
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
        addView(adViewLocal)

        val parameters = AudienzzBannerParameters().apply {
            api = listOf(
                AudienzzSignals.Api.MRAID_1,
                AudienzzSignals.Api.MRAID_2,
                AudienzzSignals.Api.MRAID_3,
                AudienzzSignals.Api.OMID_1,
            )
        }

        val adUnitLocal = AudienzzBannerAdUnit(
            prebidConfig.placementId,
            prebidPrimarySize.width,
            prebidPrimarySize.height,
        ).apply {
            bannerParameters = parameters

            setAutoRefreshInterval(config.config.refreshTimeSeconds ?: DEFAULT_REFRESH_SECONDS)
        }

        adUnit = adUnitLocal

        val handler = AudienzzAdViewHandler(
            adView = adViewLocal,
            adUnit = adUnitLocal,
        )
        adViewHandler = handler
        pendingScreenKey?.let { handler.hostScreenOverride = it }
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
         * the ad config or the publisher asks otherwise.
         *
         * This was briefly flipped to eager. That made every mounted placement auction on [loadAd]
         * regardless of position, so a publisher opening an article bought fills for below-fold
         * slots the reader might never approach — responses that can never become impressions,
         * which is the delivery pattern we are trying to reduce, not create. Eager remains
         * available per placement (`lazyLoad: false` on the ad config, or
         * `lazyLoadOverride = false`) for slots that are always on screen.
         */
        internal const val DEFAULT_LAZY_LOAD = true
    }
}
