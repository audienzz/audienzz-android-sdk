package org.audienzz.mobile.testapp.adapter

import android.util.Log
import android.view.ViewGroup
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

class UnfilledAdHolder(parent: ViewGroup) : AdHolder(parent) {

    override val titleRes: Int = R.string.gam_original_unfilled

    private var unfilledAdUnit = AudienzzBannerAdUnit(
        UNFILLED_CONFIG_ID,
        AD_SIZE.width,
        AD_SIZE.height,
    )

    private val logTagName: String = "[UnfilledAd]"

    override fun createAds() {
        val adView = AdManagerAdView(adContainer.context)

        adView.adUnitId = UNFILLED_AD_UNIT_ID
        adView.setAdSizes(AD_SIZE)
        applyAdCallback(adView)

        adContainer.addView(adView)
        addBottomMargin(adView)

        val parameters = AudienzzBannerParameters()
        parameters.api = listOf(AudienzzSignals.Api.MRAID_3, AudienzzSignals.Api.OMID_1)
        unfilledAdUnit.bannerParameters = parameters
        unfilledAdUnit.setAutoRefreshInterval(refreshTimeSeconds)

        AudienzzAdViewHandler(
            adView = adView,
            adUnit = unfilledAdUnit,
        ).load(
            callback = { request, _ ->
                adView.loadAd(request)
            },
        )
    }

    private fun applyAdCallback(adView: AdManagerAdView) {
        adView.adListener = object : AdListener() {
            override fun onAdLoaded() {
                super.onAdLoaded()
                Log.d(logTagName, "onAdLoaded")
                AudienzzAdViewUtils.hideScrollBar(adView)
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                super.onAdFailedToLoad(error)
                Log.d(logTagName, "onAdFailedToLoad $error")
                adContainer.removeView(adView)
            }

            override fun onAdClicked() {
                super.onAdClicked()
                Log.d(logTagName, "onAdClicked")
            }

            override fun onAdClosed() {
                super.onAdClosed()
                Log.d(logTagName, "onAdClosed")
            }

            override fun onAdOpened() {
                super.onAdOpened()
                Log.d(logTagName, "onAdOpened")
            }

            override fun onAdImpression() {
                super.onAdImpression()
                Log.d(logTagName, "onAdImpression")
            }

            override fun onAdSwipeGestureClicked() {
                super.onAdSwipeGestureClicked()
                Log.d(logTagName, "onAdSwipeGestureClicked")
            }
        }
    }

    override fun onAttach() {
        unfilledAdUnit.resumeAutoRefresh()
    }

    override fun onDetach() {
        unfilledAdUnit.stopAutoRefresh()
    }

    companion object {
        private const val UNFILLED_AD_UNIT_ID = "/96628199/testapp_publisher/high_floor"
        private const val UNFILLED_CONFIG_ID = "2"

        private val AD_SIZE = AdSize(320, 50)
    }
}
