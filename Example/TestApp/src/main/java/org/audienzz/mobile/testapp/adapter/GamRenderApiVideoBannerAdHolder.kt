package org.audienzz.mobile.testapp.adapter

import android.view.ViewGroup
import org.audienzz.mobile.AudienzzAdSize
import org.audienzz.mobile.api.data.AudienzzVideoPlacementType
import org.audienzz.mobile.api.rendering.AudienzzBannerView
import org.audienzz.mobile.eventhandlers.AudienzzGamBannerEventHandler
import org.audienzz.mobile.testapp.R

class GamRenderApiVideoBannerAdHolder(parent: ViewGroup) : AdHolder(parent) {

    override val titleRes = R.string.gam_render_video_banner_title

    private var adView: AudienzzBannerView? = null

    override fun createAds() {
        val eventHandler = AudienzzGamBannerEventHandler(
            adContainer.context,
            AD_UNIT_ID,
            AudienzzAdSize(WIDTH, HEIGHT),
        )
        adView = AudienzzBannerView(adContainer.context, CONFIG_ID, eventHandler).apply {
            setAutoRefreshDelay(refreshTimeSeconds)
            videoPlacementType = AudienzzVideoPlacementType.IN_BANNER
            view.let { adContainer.addView(it) }
            view.layoutParams.height = HEIGHT
            loadAd(lazyLoad = true)
        }
    }

    override fun onDetach() {
        adView?.destroy()
    }

    companion object {

        private const val AD_UNIT_ID = "/21808260008/prebid_oxb_300x250_banner"
        private const val CONFIG_ID = "prebid-demo-video-outstream"
        private const val WIDTH = 300
        private const val HEIGHT = 250
    }
}
