package org.audienzz.mobile.testapp.adapter.rendering

import android.app.Activity
import android.view.ViewGroup
import org.audienzz.mobile.api.exceptions.AudienzzAdException
import org.audienzz.mobile.api.rendering.AudienzzRewardedAdUnit
import org.audienzz.mobile.api.rendering.listeners.AudienzzRewardedAdUnitListener
import org.audienzz.mobile.eventhandlers.AudienzzGamRewardedEventHandler
import org.audienzz.mobile.testapp.R
import org.audienzz.mobile.testapp.adapter.BaseAdHolder
import org.audienzz.mobile.util.addOnBecameVisibleOnScreenListener

class RenderingApiRewardedVideoAdHolder(parent: ViewGroup) : BaseAdHolder(parent) {

    override val titleRes = R.string.rendering_api_rewarded_video_title

    private var adUnit: AudienzzRewardedAdUnit? = null

    override fun createAds() {
        val button = createButton(R.string.show_rewarded)

        button.addOnBecameVisibleOnScreenListener {
            val eventHandler = AudienzzGamRewardedEventHandler(
                adContainer.context as Activity,
                // TODO: replace with your own config from Audienzz dashboard
                AD_UNIT_ID,
            )
            // TODO: replace with your own config from Audienzz dashboard
            adUnit = AudienzzRewardedAdUnit(adContainer.context, CONFIG_ID, eventHandler)
            adUnit?.setRewardedAdUnitListener(object : AudienzzRewardedAdUnitListener {
                override fun onAdLoaded(rewardedAdUnit: AudienzzRewardedAdUnit?) {
                    button.isEnabled = true
                    button.setOnClickListener {
                        adUnit?.show()
                    }
                }

                override fun onAdFailed(
                    rewardedAdUnit: AudienzzRewardedAdUnit?,
                    exception: AudienzzAdException?,
                ) {
                    showErrorDialog(
                        adContainer.context,
                        exception?.message.orEmpty(),
                    )
                }
            })
            adUnit?.loadAd()
        }
    }

    override fun onDetach() {
        adUnit?.destroy()
    }

    companion object {
        // TODO: replace with your own config from Audienzz dashboard
        private const val AD_UNIT_ID =
            "/21808260008/prebid-demo-app-original-api-video-interstitial"
        // TODO: replace with your own placement ID from Audienzz dashboard
        private const val CONFIG_ID = "wuobgeuc"
    }
}
