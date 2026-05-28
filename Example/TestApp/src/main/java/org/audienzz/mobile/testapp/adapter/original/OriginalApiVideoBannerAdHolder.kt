package org.audienzz.mobile.testapp.adapter.original

import android.view.ViewGroup
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.admanager.AdManagerAdView
import org.audienzz.mobile.AudienzzBannerAdUnit
import org.audienzz.mobile.AudienzzPrebidMobile
import org.audienzz.mobile.AudienzzSignals
import org.audienzz.mobile.AudienzzVideoParameters
import org.audienzz.mobile.addentum.AudienzzAdViewUtils
import org.audienzz.mobile.addentum.AudienzzPbFindSizeListener
import org.audienzz.mobile.api.data.AudienzzAdUnitFormat
import org.audienzz.mobile.original.AudienzzAdViewHandler
import org.audienzz.mobile.testapp.R
import org.audienzz.mobile.testapp.adapter.BaseAdHolder
import org.audienzz.mobile.testapp.constants.SizeConstants
import org.audienzz.mobile.testapp.constants.VideoConstants
import java.util.EnumSet

class OriginalApiVideoBannerAdHolder(parent: ViewGroup) : BaseAdHolder(parent) {

    override val titleRes = R.string.original_api_video_banner_title

    private var adUnit: AudienzzBannerAdUnit? = null
    private var adViewHandler: AudienzzAdViewHandler? = null

    override fun createAds() {
        AudienzzPrebidMobile.getAdUnitConfig(BANNER_CONFIG_ID) { config ->
            config ?: return@getAdUnitConfig

            val placementId = config.prebidConfig.placementId
            val gamPath = config.gamConfig.adUnitPath

            adUnit = AudienzzBannerAdUnit(
                placementId,
                SizeConstants.MEDIUM_BANNER_WIDTH,
                SizeConstants.MEDIUM_BANNER_HEIGHT,
                EnumSet.of(AudienzzAdUnitFormat.VIDEO),
            )
            adUnit?.videoParameters = configureVideoParameters()
            adUnit?.setAutoRefreshInterval(config.config.refreshTimeSeconds ?: DEFAULT_REFRESH_TIME)

            val adView = AdManagerAdView(adContainer.context)
            adView.adUnitId = gamPath
            adView.setAdSizes(
                AdSize(
                    SizeConstants.MEDIUM_BANNER_WIDTH,
                    SizeConstants.MEDIUM_BANNER_HEIGHT,
                ),
            )
            adView.adListener = createListener(adView)

            adContainer.addView(adView)
            addBottomMargin(adView)

            val handler = AudienzzAdViewHandler(
                adView = adView,
                adUnit = adUnit!!,
            )
            adViewHandler = handler
            // withLazyLoading = false: this view lives inside a RecyclerView cell which is
            // only created just before it appears — prefetchMarginDp has no effect here.
            // RecyclerView's own prefetch (setInitialPrefetchItemCount) handles early creation.
            handler.load(
                withLazyLoading = false,
                callback = { request, resultCode ->
                    showFetchErrorDialog(adContainer.context, resultCode)
                    adView.loadAd(request)
                },
            )
            handler.enableSmartRefresh()
        }
    }

    private fun configureVideoParameters(): AudienzzVideoParameters {
        return AudienzzVideoParameters(listOf("video/x-flv", "video/mp4")).apply {
            api = listOf(
                AudienzzSignals.Api.VPAID_1,
                AudienzzSignals.Api.VPAID_2,
                AudienzzSignals.Api.OMID_1,
            )
            maxBitrate = VideoConstants.MAX_BITRATE
            minBitrate = VideoConstants.MIN_BITRATE
            maxDuration = VideoConstants.MAX_DURATION
            minDuration = VideoConstants.MIN_DURATION
            playbackMethod = listOf(AudienzzSignals.PlaybackMethod.AutoPlaySoundOn)
            protocols = listOf(
                AudienzzSignals.Protocols.VAST_2_0,
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
                            logFindCreativeSizeError(errorCode)
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
        adViewHandler?.disableSmartRefresh()
        adViewHandler = null
        adUnit?.stopAutoRefresh()
    }

    companion object {
        private const val BANNER_CONFIG_ID = "46"
    }
}
