package org.audienzz.mobile.testapp.adapter.rendering

import android.view.ViewGroup
import org.audienzz.mobile.AudienzzAdSize
import org.audienzz.mobile.api.data.AudienzzVideoPlacementType
import org.audienzz.mobile.api.rendering.AudienzzBannerView
import org.audienzz.mobile.eventhandlers.AudienzzGamBannerEventHandler
import org.audienzz.mobile.testapp.R
import org.audienzz.mobile.testapp.adapter.BaseAdHolder
import org.audienzz.mobile.testapp.constants.SizeConstants

class RenderingApiVideoBannerAdHolder(parent: ViewGroup) : BaseAdHolder(parent) {

    override val titleRes = R.string.rendering_api_video_banner_title

    private var adView: AudienzzBannerView? = null

    override fun createAds() {
        val eventHandler = AudienzzGamBannerEventHandler(
            adContainer.context,
            AD_UNIT_ID,
            AudienzzAdSize(SizeConstants.MEDIUM_BANNER_WIDTH, SizeConstants.MEDIUM_BANNER_HEIGHT),
        )
        adView = AudienzzBannerView(adContainer.context, CONFIG_ID, eventHandler).apply {
            setAutoRefreshDelay(DEFAULT_REFRESH_TIME)
            videoPlacementType = AudienzzVideoPlacementType.IN_BANNER
            view.let { adContainer.addView(it) }
            view.layoutParams.height = SizeConstants.MEDIUM_BANNER_HEIGHT
            loadAd(lazyLoad = true)
        }
    }

    override fun onDetach() {
        adView?.destroy()
    }

    companion object {
        private const val AD_UNIT_ID = "/21808260008/prebid_oxb_300x250_banner"
        private const val CONFIG_ID = "prebid-demo-video-outstream"
    }
}
