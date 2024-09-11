package org.audienzz.mobile.testapp.adapter

import android.view.ViewGroup
import org.audienzz.mobile.AudienzzAdSize
import org.audienzz.mobile.api.rendering.AudienzzBannerView
import org.audienzz.mobile.eventhandlers.AudienzzGamBannerEventHandler
import org.audienzz.mobile.testapp.R

class GamRenderApiHtmlBannerAdHolder(parent: ViewGroup) : AdHolder(parent) {

    override val titleRes: Int
        get() = R.string.gam_render_html_banner_title

    private var adView: AudienzzBannerView? = null

    override fun createAds() {
        val eventHandler = AudienzzGamBannerEventHandler(
            adContainer.context,
            AD_UNIT_ID,
            AudienzzAdSize(WIDTH, HEIGHT),
        )
        adView = AudienzzBannerView(adContainer.context, CONFIG_ID, eventHandler).apply {
            view.let { adContainer.addView(it) }
            setAutoRefreshDelay(refreshTimeSeconds)
            loadAd()
        }
    }

    override fun onDetach() {
        adView?.destroy()
    }

    companion object {

        private const val AD_UNIT_ID = "/21808260008/prebid_oxb_320x50_banner"
        private const val CONFIG_ID = "prebid-demo-banner-320-50"
        private const val WIDTH = 320
        private const val HEIGHT = 50
    }
}
