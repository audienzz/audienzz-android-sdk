package org.audienzz.mobile.testapp.adapter.original

import android.util.Log
import android.view.ViewGroup
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.admanager.AdManagerAdView
import org.audienzz.mobile.AudienzzBannerAdUnit
import org.audienzz.mobile.AudienzzBannerParameters
import org.audienzz.mobile.AudienzzPrebidMobile
import org.audienzz.mobile.AudienzzSignals
import org.audienzz.mobile.addentum.AudienzzAdViewUtils
import org.audienzz.mobile.original.AudienzzAdViewHandler
import org.audienzz.mobile.testapp.R
import org.audienzz.mobile.testapp.adapter.BaseAdHolder
import org.audienzz.mobile.testapp.constants.SizeConstants

class OriginalApiUnfilledAdHolder(parent: ViewGroup) : BaseAdHolder(parent) {

    override val titleRes: Int = R.string.original_api_unfilled

    private var unfilledAdUnit: AudienzzBannerAdUnit? = null
    private var adViewHandler: AudienzzAdViewHandler? = null

    override fun createAds() {
        AudienzzPrebidMobile.getAdUnitConfig(BANNER_CONFIG_ID) { config ->
            config ?: return@getAdUnitConfig

            val placementId = config.prebidConfig.placementId
            val gamPath = config.gamConfig.adUnitPath

            unfilledAdUnit = AudienzzBannerAdUnit(
                placementId,
                SizeConstants.SMALL_BANNER_WIDTH,
                SizeConstants.SMALL_BANNER_HEIGHT,
            )

            val adView = AdManagerAdView(adContainer.context)

            adView.adUnitId = gamPath
            adView.setAdSizes(
                AdSize(
                    SizeConstants.SMALL_BANNER_WIDTH,
                    SizeConstants.SMALL_BANNER_HEIGHT,
                ),
            )
            applyAdCallback(adView)

            adContainer.addView(adView)
            addBottomMargin(adView)

            val parameters = AudienzzBannerParameters()
            parameters.api = listOf(
                AudienzzSignals.Api.MRAID_1,
                AudienzzSignals.Api.MRAID_2,
                AudienzzSignals.Api.MRAID_3,
                AudienzzSignals.Api.OMID_1,
            )
            unfilledAdUnit?.bannerParameters = parameters
            unfilledAdUnit?.setAutoRefreshInterval(config.config.refreshTimeSeconds ?: DEFAULT_REFRESH_TIME)

            val handler = AudienzzAdViewHandler(
                adView = adView,
                adUnit = unfilledAdUnit!!,
            )
            adViewHandler = handler
            // withLazyLoading = false: this view lives inside a RecyclerView cell which is
            // only created just before it appears — prefetchMarginDp has no effect here.
            // RecyclerView's own prefetch (setInitialPrefetchItemCount) handles early creation.
            handler.load(
                withLazyLoading = false,
                callback = { request, _ ->
                    adView.loadAd(request)
                },
            )
            handler.enableSmartRefresh()
        }
    }

    private fun applyAdCallback(adView: AdManagerAdView) {
        adView.adListener = object : AdListener() {
            override fun onAdLoaded() {
                super.onAdLoaded()
                Log.d(TAG, "onAdLoaded")
                AudienzzAdViewUtils.hideScrollBar(adView)
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                super.onAdFailedToLoad(error)
                Log.d(TAG, "onAdFailedToLoad $error")
                adContainer.removeView(adView)
            }

            override fun onAdClicked() {
                super.onAdClicked()
                Log.d(TAG, "onAdClicked")
            }

            override fun onAdClosed() {
                super.onAdClosed()
                Log.d(TAG, "onAdClosed")
            }

            override fun onAdOpened() {
                super.onAdOpened()
                Log.d(TAG, "onAdOpened")
            }

            override fun onAdImpression() {
                super.onAdImpression()
                Log.d(TAG, "onAdImpression")
            }

            override fun onAdSwipeGestureClicked() {
                super.onAdSwipeGestureClicked()
                Log.d(TAG, "onAdSwipeGestureClicked")
            }
        }
    }

    override fun onAttach() {
        unfilledAdUnit?.resumeAutoRefresh()
    }

    override fun onDetach() {
        adViewHandler?.disableSmartRefresh()
        adViewHandler = null
        unfilledAdUnit?.stopAutoRefresh()
    }

    companion object {
        private const val TAG = "Original API UnfilledAd"
        private const val BANNER_CONFIG_ID = "46"
    }
}
