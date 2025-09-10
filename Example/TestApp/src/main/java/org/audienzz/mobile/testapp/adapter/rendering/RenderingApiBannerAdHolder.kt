package org.audienzz.mobile.testapp.adapter.rendering

import android.view.ViewGroup
import org.audienzz.mobile.AudienzzAdSize
import org.audienzz.mobile.api.rendering.AudienzzBannerView
import org.audienzz.mobile.eventhandlers.AudienzzGamBannerEventHandler
import org.audienzz.mobile.testapp.R
import org.audienzz.mobile.testapp.adapter.BaseAdHolder
import org.audienzz.mobile.testapp.constants.SizeConstants

class RenderingApiBannerAdHolder(parent: ViewGroup) : BaseAdHolder(parent) {

    override val titleRes = R.string.rendering_api_banner_title

    private var adView: AudienzzBannerView? = null

    override fun createAds() {
        val eventHandler = AudienzzGamBannerEventHandler(
            adContainer.context,
            AD_UNIT_ID,
            AudienzzAdSize(SizeConstants.SMALL_BANNER_WIDTH, SizeConstants.SMALL_BANNER_HEIGHT),
        )
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
        private const val AD_UNIT_ID = "/21808260008/prebid_oxb_320x50_banner"
        private const val CONFIG_ID = "prebid-demo-banner-320-50"
    }
}
