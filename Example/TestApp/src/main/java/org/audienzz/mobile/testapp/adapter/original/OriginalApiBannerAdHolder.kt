package org.audienzz.mobile.testapp.adapter.original

import android.util.Log
import android.view.ViewGroup
import androidx.core.view.doOnNextLayout
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.admanager.AdManagerAdView
import org.audienzz.mobile.AudienzzBannerAdUnit
import org.audienzz.mobile.AudienzzBannerParameters
import org.audienzz.mobile.AudienzzSignals
import org.audienzz.mobile.addentum.AudienzzAdViewUtils
import org.audienzz.mobile.original.AudienzzAdViewHandler
import org.audienzz.mobile.testapp.R
import org.audienzz.mobile.testapp.adapter.BaseAdHolder
import org.audienzz.mobile.testapp.constants.SizeConstants
import org.audienzz.mobile.util.pxToDp

class OriginalApiBannerAdHolder(parent: ViewGroup) : BaseAdHolder(parent) {
    override val titleRes = R.string.original_api_banner_title

    private var adUnit320x50 =
        AudienzzBannerAdUnit(
            CONFIG_ID_320_50,
            SizeConstants.SMALL_BANNER_WIDTH,
            SizeConstants.SMALL_BANNER_HEIGHT,
        )
    private var adUnit300x250 =
        AudienzzBannerAdUnit(
            CONFIG_ID_300_250,
            SizeConstants.MEDIUM_BANNER_WIDTH,
            SizeConstants.MEDIUM_BANNER_HEIGHT,
        )
    private var adUnitMultisize =
        AudienzzBannerAdUnit(
            CONFIG_ID_MULTISIZE,
            SizeConstants.SMALL_BANNER_WIDTH,
            SizeConstants.SMALL_BANNER_HEIGHT,
        )

    override fun createAds() {
        createAd(
            SizeConstants.SMALL_BANNER_WIDTH,
            SizeConstants.SMALL_BANNER_HEIGHT,
            AD_UNIT_ID_320_50,
            adUnit320x50,
        )
        createAd(
            SizeConstants.MEDIUM_BANNER_WIDTH,
            SizeConstants.MEDIUM_BANNER_HEIGHT,
            AD_UNIT_ID_300_250,
            adUnit300x250,
        )
        createAd(
            SizeConstants.SMALL_BANNER_WIDTH,
            SizeConstants.SMALL_BANNER_HEIGHT,
            AD_UNIT_ID_MULTISIZE,
            adUnitMultisize,
            true,
        )
    }

    private fun createAd(
        width: Int,
        height: Int,
        unitId: String,
        adUnit: AudienzzBannerAdUnit,
        adaptiveSize: Boolean = false,
    ) {
        val adView = AdManagerAdView(adContainer.context)
        if (adaptiveSize) {
            adView.doOnNextLayout {
                adView.setAdSizes(
                    AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(
                        adContainer.context,
                        adContainer.resources.pxToDp(it.width),
                    ),
                )
            }
        }
        adView.adUnitId = unitId
        adView.setAdSizes(AdSize(width, height))
        applyAdCallback(adView)

        adContainer.addView(adView)
        addBottomMargin(adView)

        val parameters = AudienzzBannerParameters()
        parameters.api = listOf(AudienzzSignals.Api.MRAID_3, AudienzzSignals.Api.OMID_1)
        adUnit.bannerParameters = parameters
        adUnit.setAutoRefreshInterval(DEFAULT_REFRESH_TIME)

        AudienzzAdViewHandler(
            adView = adView,
            adUnit = adUnit,
        ).load(
            callback = { request, resultCode ->
                showFetchErrorDialog(adContainer.context, resultCode)
                adView.loadAd(request)
            },
        )
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
                showAdLoadingErrorDialog(adContainer.context, error)
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
        adUnit320x50.resumeAutoRefresh()
        adUnit300x250.resumeAutoRefresh()
        adUnitMultisize.resumeAutoRefresh()
    }

    override fun onDetach() {
        adUnit320x50.stopAutoRefresh()
        adUnit300x250.stopAutoRefresh()
        adUnitMultisize.stopAutoRefresh()
    }

    companion object {
        private const val TAG: String = "Original API BannerAd"
        private const val AD_UNIT_ID_320_50 = "ca-app-pub-3940256099942544/2934735716"
        private const val CONFIG_ID_320_50 = "33994718"
        private const val AD_UNIT_ID_300_250 =
            "/96628199/de_audienzz.ch_v2/de_audienzz.ch_320_adnz_wideboard_1"
        private const val CONFIG_ID_300_250 = "33994718"
        private const val AD_UNIT_ID_MULTISIZE =
            "ca-app-pub-3940256099942544/2435281174"
        private const val CONFIG_ID_MULTISIZE = "33994718"
    }
}
