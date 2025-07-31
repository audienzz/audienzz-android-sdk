package org.audienzz.mobile.testapp.adapter

import android.app.Activity
import android.view.ViewGroup
import org.audienzz.mobile.AudienzzReward
import org.audienzz.mobile.api.exceptions.AudienzzAdException
import org.audienzz.mobile.api.rendering.AudienzzRewardedAdUnit
import org.audienzz.mobile.api.rendering.listeners.AudienzzRewardedAdUnitListener
import org.audienzz.mobile.eventhandlers.AudienzzGamRewardedEventHandler
import org.audienzz.mobile.testapp.R
import org.audienzz.mobile.util.addOnBecameVisibleOnScreenListener

class GamRenderApiRewardedVideoAdHolder(parent: ViewGroup) : AdHolder(parent) {

    override val titleRes = R.string.gam_render_rewarded_video_title

    private var adUnit: AudienzzRewardedAdUnit? = null

    override fun createAds() {
        val button = createButton(R.string.show_rewarded)

        val eventHandler = AudienzzGamRewardedEventHandler(
            adContainer.context as Activity,
            AD_UNIT_ID,
        )
        adUnit = AudienzzRewardedAdUnit(adContainer.context, CONFIG_ID, eventHandler)
        adUnit?.setRewardedAdUnitListener(object : AudienzzRewardedAdUnitListener {
            override fun onAdLoaded(rewardedAdUnit: AudienzzRewardedAdUnit?) {
                button.isEnabled = true
                button.setOnClickListener {
                    adUnit?.show()
                }
            }

            override fun onAdDisplayed(rewardedAdUnit: AudienzzRewardedAdUnit?) {}

            override fun onAdFailed(
                rewardedAdUnit: AudienzzRewardedAdUnit?,
                exception: AudienzzAdException?,
            ) {
                showErrorDialog(
                    adContainer.context,
                    exception?.message.orEmpty(),
                )
            }

            override fun onAdClicked(rewardedAdUnit: AudienzzRewardedAdUnit?) {}

            override fun onAdClosed(rewardedAdUnit: AudienzzRewardedAdUnit?) {}

            override fun onUserEarnedReward(
                rewardedAdUnit: AudienzzRewardedAdUnit?,
                reward: AudienzzReward?,
            ) {
            }
        })
        button.addOnBecameVisibleOnScreenListener {
            adUnit?.loadAd()
        }
    }

    override fun onDetach() {
        adUnit?.destroy()
    }

    companion object {

        private const val AD_UNIT_ID =
            "/21808260008/prebid-demo-app-original-api-video-interstitial"
        private const val CONFIG_ID = "prebid-demo-video-rewarded-320-480"
    }
}
