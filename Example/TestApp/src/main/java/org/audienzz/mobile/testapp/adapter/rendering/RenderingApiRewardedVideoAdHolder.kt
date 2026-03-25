package org.audienzz.mobile.testapp.adapter.rendering

import android.app.Activity
import android.view.ViewGroup
import org.audienzz.mobile.AudienzzPrebidMobile
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
            AudienzzPrebidMobile.getAdUnitConfig(REWARDED_CONFIG_ID) { config ->
                config ?: return@getAdUnitConfig

                val placementId = config.prebidConfig.placementId
                val gamPath = config.gamConfig.adUnitPath

                val eventHandler = AudienzzGamRewardedEventHandler(
                    adContainer.context as Activity,
                    gamPath,
                )
                adUnit = AudienzzRewardedAdUnit(adContainer.context, placementId, eventHandler)
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
    }

    override fun onDetach() {
        adUnit?.destroy()
    }

    companion object {
        private const val REWARDED_CONFIG_ID = "47"
    }
}
