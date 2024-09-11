package org.audienzz.mobile.testapp.adapter

import android.view.ViewGroup
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.admanager.AdManagerAdView
import org.audienzz.mobile.AudienzzBannerAdUnit
import org.audienzz.mobile.AudienzzSignals
import org.audienzz.mobile.AudienzzVideoParameters
import org.audienzz.mobile.addentum.AudienzzAdViewUtils
import org.audienzz.mobile.addentum.AudienzzPbFindSizeListener
import org.audienzz.mobile.api.data.AudienzzAdUnitFormat
import org.audienzz.mobile.original.AudienzzAdViewHandler
import org.audienzz.mobile.testapp.R
import java.util.EnumSet

class GamOriginalApiVideoBannerAdHolder(parent: ViewGroup) : AdHolder(parent) {

    override val titleRes: Int
        get() = R.string.gam_original_video_banner_title

    private var adUnit: AudienzzBannerAdUnit? = null

    override fun createAds() {
        adUnit = AudienzzBannerAdUnit(
            CONFIG_ID,
            WIDTH,
            HEIGHT,
            EnumSet.of(AudienzzAdUnitFormat.VIDEO),
        )
        adUnit?.videoParameters = configureVideoParameters()

        val adView = AdManagerAdView(adContainer.context)
        adView.adUnitId = AD_UNIT_ID
        adView.setAdSizes(AdSize(WIDTH, HEIGHT))
        adView.adListener = createListener(adView)

        adContainer.addView(adView)
        addBottomMargin(adView)

        AudienzzAdViewHandler(
            adView = adView,
            adUnit = adUnit!!,
        ).load(callback = { request, resultCode ->
            showFetchErrorDialog(adContainer.context, resultCode)
            adView.loadAd(request)
        })
    }

    private fun configureVideoParameters(): AudienzzVideoParameters {
        return AudienzzVideoParameters(listOf("video/x-flv", "video/mp4")).apply {
            api = listOf(
                AudienzzSignals.Api.VPAID_1,
                AudienzzSignals.Api.VPAID_2
            )
            maxBitrate = 1500
            minBitrate = 300
            maxDuration = 30
            minDuration = 5
            playbackMethod = listOf(AudienzzSignals.PlaybackMethod.AutoPlaySoundOn)
            protocols = listOf(
                AudienzzSignals.Protocols.VAST_2_0
            )
        }
    }

    private fun createListener(gamView: AdManagerAdView): AdListener {
        return object : AdListener() {
            override fun onAdLoaded() {
                AudienzzAdViewUtils.findPrebidCreativeSize(
                    gamView,
                    object : AudienzzPbFindSizeListener {
                        override fun success(width: Int, height: Int) {
                            gamView.setAdSizes(AdSize(width, height))
                        }

                        override fun failure(errorCode: Int) {
                            showFindCreativeSizeErrorDialog(adContainer.context, errorCode)
                        }
                    },
                )
            }
        }
    }

    override fun onAttach() {
        adUnit?.resumeAutoRefresh()
    }

    override fun onDetach() {
        adUnit?.stopAutoRefresh()
    }

    companion object {

        const val AD_UNIT_ID = "/21808260008/prebid-demo-original-api-video-banner"
        const val CONFIG_ID = "prebid-demo-video-outstream-original-api"
        const val WIDTH = 300
        const val HEIGHT = 250
    }
}
