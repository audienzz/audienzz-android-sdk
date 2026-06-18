package org.audienzz.mobile

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.admanager.AdManagerAdRequest
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
import org.audienzz.mobile.api.original.AudienzzPrebidAdUnit
import org.audienzz.mobile.api.original.AudienzzPrebidRequest
import org.audienzz.mobile.di.MainComponent
import org.audienzz.mobile.event.adCreation
import org.audienzz.mobile.event.adFailedToLoad
import org.audienzz.mobile.event.bidRequest
import org.audienzz.mobile.event.bidWinner
import org.audienzz.mobile.event.entity.AdSubtype
import org.audienzz.mobile.event.entity.AdType
import org.audienzz.mobile.event.entity.ApiType
import org.audienzz.mobile.event.eventLogger
import org.audienzz.mobile.original.AudienzzAdViewHandler
import org.audienzz.mobile.util.addContinuousVisibilityListener
import org.audienzz.mobile.util.addOnBecameVisibleOnScreenListener
import org.audienzz.mobile.util.addPrefetchMarginListener
import org.audienzz.mobile.util.adViewId
import org.audienzz.mobile.util.dpToPx
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

    // Banner path
    private var adUnit: AudienzzBannerAdUnit? = null
    private var adViewHandler: AudienzzAdViewHandler? = null

    // Multiformat native path
    private var prebidAdUnit: AudienzzPrebidAdUnit? = null
    private var nativeSmartRefreshListener: android.view.ViewTreeObserver.OnPreDrawListener? = null
    private var nativeLastRefreshTime: Long = 0
    private val nativeRefreshHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var nativePendingRefreshRunnable: Runnable? = null

    // Shared
    private var adView: AdManagerAdView? = null
    private var externalAdListener: AdListener? = null

    /**
     * Override the native ad slot height (dp). Takes precedence over the backend `heightAndroid`
     * value. Set before calling [loadAd]. When `null` (default), the backend value is used; if that
     * is also absent the slot uses `WRAP_CONTENT` (fluid auto-size).
     */
    var nativeAdHeightDp: Int? = null

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

    /**
     * Loads the ad from a caller-supplied [RemoteAdUnitConfig] instead of fetching it from the
     * remote config backend. Useful for testing or when the config is constructed locally.
     */
    fun loadAdWithConfig(config: RemoteAdUnitConfig) {
        createAdFromConfig(config)
    }

    fun destroy() {
        // Banner path
        adViewHandler?.disableSmartRefresh()
        adViewHandler = null
        adUnit?.stopAutoRefresh()
        adUnit = null

        // Multiformat native path
        nativeSmartRefreshListener?.let {
            adView?.viewTreeObserver?.takeIf { vto -> vto.isAlive }
                ?.removeOnPreDrawListener(it)
        }
        nativeSmartRefreshListener = null
        nativePendingRefreshRunnable?.let { nativeRefreshHandler.removeCallbacks(it) }
        nativePendingRefreshRunnable = null
        prebidAdUnit?.stopAutoRefresh()
        prebidAdUnit = null

        adView?.destroy()
        adView = null
        removeAllViews()
        scope.cancel()
    }

    fun onResume() {
        adUnit?.resumeAutoRefresh()
        prebidAdUnit?.resumeAutoRefresh()
    }

    fun onPause() {
        adUnit?.stopAutoRefresh()
        prebidAdUnit?.stopAutoRefresh()
    }

    fun setAdListener(listener: AdListener) {
        externalAdListener = listener
    }

    fun getAdSize(): AdSize? = adView?.adSize

    // ─────────────────────────────────────────────────────────────────────────
    // Internal loading
    // ─────────────────────────────────────────────────────────────────────────

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

    private fun createAdFromConfig(config: RemoteAdUnitConfig) {
        removeAllViews()
        if (config.config.nativeAdConfig?.enabled == true) {
            createMultiformatAdFromConfig(config)
        } else {
            createBannerAdFromConfig(config)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Banner-only path
    // ─────────────────────────────────────────────────────────────────────────

    @Suppress("SpreadOperator")
    private fun createBannerAdFromConfig(config: RemoteAdUnitConfig) {
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
        handler.load(
            withLazyLoading = true,
            prefetchMarginDp = config.config.prefetchDistanceDp ?: DEFAULT_PREFETCH_DISTANCE_DP,
        ) { request, resultCode ->
            Log.d(TAG, "Ad request prepared, resultCode=${resultCode ?: "unknown"}")
            adViewLocal.loadAd(request)
        }
        handler.enableSmartRefresh()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Multiformat native path (banner + native in-webview)
    // ─────────────────────────────────────────────────────────────────────────

    @Suppress("SpreadOperator")
    private fun createMultiformatAdFromConfig(config: RemoteAdUnitConfig) {
        val gamConfig = config.gamConfig
        val prebidConfig = config.prebidConfig
        val nativeConfig = config.config.nativeAdConfig

        val sortedGamSizes = gamConfig.adSizes
            .sortedByDescending { it.width * it.height }
            .map { AdSize(it.width, it.height) }

        val sortedPrebidSizes = prebidConfig.adSizes
            .sortedByDescending { it.width * it.height }

        if (sortedGamSizes.isEmpty() || sortedPrebidSizes.isEmpty()) {
            Log.e(TAG, "No valid sizes in remote config for id=$adConfigId (multiformat)")
            return
        }

        // Resolve height: client override → backend (Android-specific) → WRAP_CONTENT
        val resolvedHeightDp: Int? = nativeAdHeightDp ?: nativeConfig?.heightAndroid
        val adViewHeightPx = resolvedHeightDp
            ?.let { context.resources.dpToPx(it) }
            ?: ViewGroup.LayoutParams.WRAP_CONTENT

        Log.d(TAG, "Multiformat native: resolvedHeightDp=$resolvedHeightDp, adViewHeightPx=$adViewHeightPx")

        // GAM view: FLUID first (native-in-webview), then banner fallback sizes
        val adViewLocal = AdManagerAdView(context).apply {
            adUnitId = gamConfig.adUnitPath
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                adViewHeightPx,
            )
            val finalSizes = buildList {
                add(AdSize.FLUID)
                addAll(sortedGamSizes)
            }
            setAdSizes(finalSizes[0], *finalSizes.drop(1).toTypedArray())
            adListener = createAdListener()
        }

        adView = adViewLocal
        addView(adViewLocal)

        // Prebid multiformat unit
        val prebidAdUnitLocal = AudienzzPrebidAdUnit(prebidConfig.placementId).also {
            it.setAutoRefreshInterval(config.config.refreshTimeSeconds ?: DEFAULT_REFRESH_SECONDS)
        }
        prebidAdUnit = prebidAdUnitLocal

        // Prebid request: banner + native parameters
        val prebidRequest = AudienzzPrebidRequest().apply {
            setBannerParameters(AudienzzBannerParameters().apply {
                adSizes = sortedPrebidSizes.map { AudienzzAdSize(it.width, it.height) }.toSet()
                api = listOf(
                    AudienzzSignals.Api.MRAID_1,
                    AudienzzSignals.Api.MRAID_2,
                    AudienzzSignals.Api.MRAID_3,
                    AudienzzSignals.Api.OMID_1,
                )
            })
            setNativeParameters(buildDefaultNativeParameters())
        }

        val prefetchMarginDp = config.config.prefetchDistanceDp ?: DEFAULT_PREFETCH_DISTANCE_DP
        val refreshIntervalMs =
            ((config.config.refreshTimeSeconds ?: DEFAULT_REFRESH_SECONDS) * 1000).toLong()

        // Log ad creation
        eventLogger?.adCreation(
            adUnitId = gamConfig.adUnitPath,
            adViewId = adViewLocal.adViewId,
            adType = AdType.NATIVE,
            adSubtype = AdSubtype.MULTIFORMAT,
            apiType = ApiType.ORIGINAL,
        )

        val loadTrigger = {
            fetchMultiformatDemand(
                adViewLocal = adViewLocal,
                prebidAdUnitLocal = prebidAdUnitLocal,
                prebidRequest = prebidRequest,
                adUnitId = gamConfig.adUnitPath,
                refreshIntervalMs = refreshIntervalMs,
            )
        }

        if (prefetchMarginDp > 0) {
            adViewLocal.addPrefetchMarginListener(marginDp = prefetchMarginDp) { loadTrigger() }
        } else {
            adViewLocal.addOnBecameVisibleOnScreenListener { loadTrigger() }
        }

        enableNativeSmartRefresh(
            adViewLocal = adViewLocal,
            prebidAdUnitLocal = prebidAdUnitLocal,
            prebidRequest = prebidRequest,
            adUnitId = gamConfig.adUnitPath,
            refreshIntervalMs = refreshIntervalMs,
        )
    }

    private fun fetchMultiformatDemand(
        adViewLocal: AdManagerAdView,
        prebidAdUnitLocal: AudienzzPrebidAdUnit,
        prebidRequest: AudienzzPrebidRequest,
        adUnitId: String,
        refreshIntervalMs: Long,
    ) {
        val ppid = AudienzzPrebidMobile.ppidManager?.getPpid()
        val gamRequestBuilder = AdManagerAdRequest.Builder()
        if (ppid != null) gamRequestBuilder.setPublisherProvidedId(ppid)
        val gamRequest = AudienzzTargetingParams.CUSTOM_TARGETING_MANAGER
            .applyToGamRequestBuilder(gamRequestBuilder)
            .build()

        val isAutorefresh = refreshIntervalMs > 0
        val isRefresh = nativeLastRefreshTime != 0L
        Log.d(TAG, "fetchMultiformatDemand() adUnitId=$adUnitId isRefresh=$isRefresh")

        eventLogger?.bidRequest(
            adUnitId = adUnitId,
            adViewId = adViewLocal.adViewId,
            adType = AdType.NATIVE,
            adSubtype = AdSubtype.MULTIFORMAT,
            apiType = ApiType.ORIGINAL,
            isAutorefresh = isAutorefresh,
            autorefreshTime = refreshIntervalMs,
            isRefresh = isRefresh,
        )

        prebidAdUnitLocal.fetchDemand(gamRequest, prebidRequest) { _ ->
            nativeLastRefreshTime = System.currentTimeMillis()
            eventLogger?.bidWinner(
                adUnitId = adUnitId,
                adViewId = adViewLocal.adViewId,
                adType = AdType.NATIVE,
                adSubtype = AdSubtype.MULTIFORMAT,
                apiType = ApiType.ORIGINAL,
                isAutorefresh = isAutorefresh,
                autorefreshTime = refreshIntervalMs,
                isRefresh = isRefresh,
                resultCode = null,
                targetKeywords = gamRequest.keywords.toList(),
            )
            adViewLocal.loadAd(gamRequest)
        }
    }

    private fun enableNativeSmartRefresh(
        adViewLocal: AdManagerAdView,
        prebidAdUnitLocal: AudienzzPrebidAdUnit,
        prebidRequest: AudienzzPrebidRequest,
        adUnitId: String,
        refreshIntervalMs: Long,
    ) {
        if (refreshIntervalMs <= 0) return

        nativeSmartRefreshListener = adViewLocal.addContinuousVisibilityListener(
            onBecameVisible = {
                if (nativeLastRefreshTime == 0L) {
                    Log.d(TAG, "smartRefresh (native) $adUnitId — became visible before first load, skipping")
                    return@addContinuousVisibilityListener
                }
                nativePendingRefreshRunnable?.let { nativeRefreshHandler.removeCallbacks(it) }

                val elapsed = System.currentTimeMillis() - nativeLastRefreshTime
                val remaining = maxOf(0L, refreshIntervalMs - elapsed)

                if (remaining == 0L) {
                    Log.d(TAG, "smartRefresh (native) $adUnitId — stale, refreshing now")
                    fetchMultiformatDemand(adViewLocal, prebidAdUnitLocal, prebidRequest, adUnitId, refreshIntervalMs)
                    prebidAdUnitLocal.resumeAutoRefresh()
                } else {
                    Log.d(TAG, "smartRefresh (native) $adUnitId — fresh, scheduling in ${remaining}ms")
                    val runnable = Runnable {
                        fetchMultiformatDemand(adViewLocal, prebidAdUnitLocal, prebidRequest, adUnitId, refreshIntervalMs)
                        prebidAdUnitLocal.resumeAutoRefresh()
                    }
                    nativePendingRefreshRunnable = runnable
                    nativeRefreshHandler.postDelayed(runnable, remaining)
                }
            },
            onBecameHidden = {
                Log.d(TAG, "smartRefresh (native) $adUnitId — hidden, pausing")
                nativePendingRefreshRunnable?.let { nativeRefreshHandler.removeCallbacks(it) }
                nativePendingRefreshRunnable = null
                prebidAdUnitLocal.stopAutoRefresh()
            },
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Shared helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun buildDefaultNativeParameters(): AudienzzNativeParameters {
        val assets = buildList {
            add(AudienzzNativeTitleAsset().apply {
                len = NATIVE_TITLE_MAX_LEN
                isRequired = true
            })
            add(AudienzzNativeImageAsset(
                NATIVE_ICON_SIZE, NATIVE_ICON_SIZE,
                NATIVE_ICON_SIZE, NATIVE_ICON_SIZE,
            ).apply {
                imageType = AudienzzNativeImageAsset.ImageType.ICON
                isRequired = true
            })
            add(AudienzzNativeImageAsset(
                NATIVE_IMAGE_SIZE, NATIVE_IMAGE_SIZE,
                NATIVE_IMAGE_SIZE, NATIVE_IMAGE_SIZE,
            ).apply {
                imageType = AudienzzNativeImageAsset.ImageType.MAIN
                isRequired = true
            })
            add(AudienzzNativeDataAsset().apply {
                len = NATIVE_TITLE_MAX_LEN
                dataType = AudienzzNativeDataAsset.DataType.SPONSORED
                isRequired = true
            })
            add(AudienzzNativeDataAsset().apply {
                dataType = AudienzzNativeDataAsset.DataType.DESC
                isRequired = true
            })
            add(AudienzzNativeDataAsset().apply {
                dataType = AudienzzNativeDataAsset.DataType.CTATEXT
                isRequired = true
            })
        }

        return AudienzzNativeParameters(assets).apply {
            addEventTracker(
                AudienzzNativeEventTracker(
                    AudienzzNativeEventTracker.EventType.IMPRESSION,
                    listOf(AudienzzNativeEventTracker.EventTrackingMethod.IMAGE),
                ),
            )
            setContextType(AudienzzNativeAdUnit.ContextType.SOCIAL_CENTRIC)
            setPlacementType(AudienzzNativeAdUnit.PlacementType.CONTENT_FEED)
        }
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
            adView?.adUnitId?.let { id ->
                eventLogger?.adFailedToLoad(adUnitId = id, errorMessage = error.message)
            }
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

        // Default native asset sizes (same as NativeAdUtils in test app)
        private const val NATIVE_TITLE_MAX_LEN = 90
        private const val NATIVE_ICON_SIZE = 20
        private const val NATIVE_IMAGE_SIZE = 200
    }
}
