package org.audienzz.mobile.testapp.adapter.rendering

import android.view.ViewGroup
import org.audienzz.mobile.AudienzzAdSize
import org.audienzz.mobile.AudienzzPrebidMobile
import org.audienzz.mobile.api.rendering.AudienzzBannerView
import org.audienzz.mobile.eventhandlers.AudienzzGamBannerEventHandler
import org.audienzz.mobile.testapp.R
import org.audienzz.mobile.testapp.adapter.BaseAdHolder

class RenderingApiBannerAdHolder(parent: ViewGroup) : BaseAdHolder(parent) {

    override val titleRes = R.string.rendering_api_banner_title

    private var adView: AudienzzBannerView? = null

    override fun createAds() {
        AudienzzPrebidMobile.getAdUnitConfig(BANNER_CONFIG_ID) { config ->
            config ?: return@getAdUnitConfig

            val placementId = config.prebidConfig.placementId
            val gamPath = config.gamConfig.adUnitPath
            val adSizes = config.prebidConfig.adSizes.map { AudienzzAdSize(it.width, it.height) }
            val primarySize = adSizes.firstOrNull() ?: AudienzzAdSize(320, 50)

            val eventHandler = AudienzzGamBannerEventHandler(
                adContainer.context,
                gamPath,
                primarySize,
            )
            adView = AudienzzBannerView(adContainer.context, placementId, eventHandler).apply {
                view.let { adContainer.addView(it) }
                setAutoRefreshDelay(DEFAULT_REFRESH_TIME)
                loadAd()
            }
        }
    }

    override fun onDetach() {
        adView?.destroy()
    }

    companion object {
        private const val BANNER_CONFIG_ID = "46"
    }
}
