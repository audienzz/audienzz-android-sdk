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
        adViewHandler?.disableSmartRefresh()
        adViewHandler = null
        adUnit?.stopAutoRefresh()
        adUnit = null
        adView?.destroy()
        adView = null
        removeAllViews()
        scope.cancel()
    }

    fun onResume() {
        adUnit?.resumeAutoRefresh()
    }

    fun onPause() {
        adUnit?.stopAutoRefresh()
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
        handler.load(
            withLazyLoading = true,
            prefetchMarginDp = config.config.prefetchDistanceDp ?: DEFAULT_PREFETCH_DISTANCE_DP,
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
    }
}
