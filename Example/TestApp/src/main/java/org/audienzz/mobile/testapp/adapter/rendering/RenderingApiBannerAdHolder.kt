package org.audienzz.mobile.testapp.adapter.rendering

import android.view.ViewGroup
import org.audienzz.mobile.AudienzzAdSize
import org.audienzz.mobile.api.rendering.AudienzzBannerView
import org.audienzz.mobile.eventhandlers.AudienzzGamBannerEventHandler
import org.audienzz.mobile.testapp.R
import org.audienzz.mobile.testapp.adapter.BaseAdHolder

class RenderingApiBannerAdHolder(parent: ViewGroup) : BaseAdHolder(parent) {

    override val titleRes = R.string.rendering_api_banner_title

    private var adView: AudienzzBannerView? = null

    override fun createAds() {
        val eventHandler = AudienzzGamBannerEventHandler(
            adContainer.context,
            // TODO: replace with your own config from Audienzz dashboard
            AD_UNIT_ID,
            AudienzzAdSize(320, 50),
        )
        // TODO: replace with your own config from Audienzz dashboard
        adView = AudienzzBannerView(adContainer.context, CONFIG_ID, eventHandler).apply {
            view.let { adContainer.addView(it) }
            setAutoRefreshDelay(DEFAULT_REFRESH_TIME)
            loadAd()
        }
    }

    override fun onDetach() {
        adView?.destroy()
    }

    companion object {
        // TODO: replace with your own config from Audienzz dashboard
        private const val AD_UNIT_ID = "/21808260008/prebid_oxb_320x50_banner"
        // TODO: replace with your own placement ID from Audienzz dashboard
        private const val CONFIG_ID = "wuobgeuc"
    }
}
