package org.audienzz.mobile.testapp.adapter.rendering

import android.app.Activity
import android.view.ViewGroup
import org.audienzz.mobile.api.data.AudienzzAdUnitFormat
import org.audienzz.mobile.api.exceptions.AudienzzAdException
import org.audienzz.mobile.api.rendering.AudienzzInterstitialAdUnit
import org.audienzz.mobile.api.rendering.listeners.AudienzzInterstitialAdUnitListener
import org.audienzz.mobile.eventhandlers.AudienzzGamInterstitialEventHandler
import org.audienzz.mobile.testapp.R
import org.audienzz.mobile.testapp.adapter.BaseAdHolder
import org.audienzz.mobile.util.addOnBecameVisibleOnScreenListener
import java.util.EnumSet

class RenderingApiInterstitialAdHolder(parent: ViewGroup) : BaseAdHolder(parent) {

    override val titleRes = R.string.rendering_api_interstitial_title

    private var displayAdUnit: AudienzzInterstitialAdUnit? = null
    private var videoAdUnit: AudienzzInterstitialAdUnit? = null

    override fun createAds() {
        addDisplayInterstitialAd()
        addVideoInterstitialAd()
    }

    private fun addDisplayInterstitialAd() {
        val button = createButton(R.string.show_display_interstitial)

        button.addOnBecameVisibleOnScreenListener {
            val eventHandler = AudienzzGamInterstitialEventHandler(
                adContainer.context as Activity,
                // TODO: replace with your own config from Audienzz dashboard
                DISPLAY_AD_UNIT_ID,
            )

            // TODO: replace with your own config from Audienzz dashboard
            displayAdUnit = AudienzzInterstitialAdUnit(
                adContainer.context,
                DISPLAY_CONFIG_ID,
                EnumSet.of(AudienzzAdUnitFormat.BANNER),
                eventHandler,
            )

            displayAdUnit?.setInterstitialAdUnitListener(object : AudienzzInterstitialAdUnitListener {
                override fun onAdLoaded(interstitialAdUnit: AudienzzInterstitialAdUnit) {
                    button.isEnabled = true
                    button.setOnClickListener {
                        displayAdUnit?.show()
                    }
                }

                override fun onAdFailed(
                    interstitialAdUnit: AudienzzInterstitialAdUnit,
                    exception: AudienzzAdException?,
                ) {
                    showErrorDialog(adContainer.context, exception?.message.orEmpty())
                }
            })

            displayAdUnit?.loadAd()
        }
    }

    private fun addVideoInterstitialAd() {
        val button = createButton(R.string.show_video_interstitial)

        button.addOnBecameVisibleOnScreenListener {
            val eventHandler = AudienzzGamInterstitialEventHandler(
                adContainer.context as Activity,
                // TODO: replace with your own config from Audienzz dashboard
                VIDEO_AD_UNIT_ID,
            )

            // TODO: replace with your own config from Audienzz dashboard
            videoAdUnit = AudienzzInterstitialAdUnit(
                adContainer.context,
                VIDEO_CONFIG_ID,
                EnumSet.of(AudienzzAdUnitFormat.VIDEO),
                eventHandler,
            )

            videoAdUnit?.setInterstitialAdUnitListener(object : AudienzzInterstitialAdUnitListener {
                override fun onAdLoaded(interstitialAdUnit: AudienzzInterstitialAdUnit) {
                    button.isEnabled = true
                    button.setOnClickListener {
                        interstitialAdUnit.show()
                    }
                }

                override fun onAdFailed(
                    interstitialAdUnit: AudienzzInterstitialAdUnit,
                    exception: AudienzzAdException?,
                ) {
                    showErrorDialog(adContainer.context, exception?.message.orEmpty())
                }
            })

            videoAdUnit?.loadAd()
        }
    }

    override fun onDetach() {
        displayAdUnit?.destroy()
        videoAdUnit?.destroy()
    }

    companion object {
        // TODO: replace with your own config from Audienzz dashboard
        private const val DISPLAY_AD_UNIT_ID = "/21808260008/prebid_oxb_html_interstitial"
        // TODO: replace with your own placement ID from Audienzz dashboard
        private const val DISPLAY_CONFIG_ID = "wuobgeuc"
        // TODO: replace with your own config from Audienzz dashboard
        private const val VIDEO_AD_UNIT_ID = "/21808260008/prebid_oxb_interstitial_video"
        // TODO: replace with your own placement ID from Audienzz dashboard
        private const val VIDEO_CONFIG_ID = "wuobgeuc"
    }
}
