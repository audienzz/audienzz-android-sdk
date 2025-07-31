package org.audienzz.mobile.testapp.adapter

import android.app.Activity
import android.content.res.Configuration
import android.view.ViewGroup
import org.audienzz.mobile.api.data.AudienzzAdUnitFormat
import org.audienzz.mobile.api.exceptions.AudienzzAdException
import org.audienzz.mobile.api.rendering.AudienzzInterstitialAdUnit
import org.audienzz.mobile.api.rendering.listeners.AudienzzInterstitialAdUnitListener
import org.audienzz.mobile.eventhandlers.AudienzzGamInterstitialEventHandler
import org.audienzz.mobile.testapp.R
import org.audienzz.mobile.util.addOnBecameVisibleOnScreenListener
import java.util.EnumSet

class GamRenderApiInterstitialAdHolder(parent: ViewGroup) : AdHolder(parent) {

    override val titleRes = R.string.gam_render_interstitial_title

    private var displayAdUnit: AudienzzInterstitialAdUnit? = null
    private var videoAdUnit: AudienzzInterstitialAdUnit? = null

    override fun createAds() {
        addDisplayInterstitialAd()
        addVideoInterstitialAd()
    }

    private fun addDisplayInterstitialAd() {
        val button = createButton(R.string.show_display_interstitial)

        val orientation = adContainer.resources.configuration.orientation
        val eventHandler = AudienzzGamInterstitialEventHandler(
            adContainer.context as Activity,
            if (orientation == Configuration.ORIENTATION_PORTRAIT) {
                DISPLAY_AD_UNIT_ID
            } else {
                FALLBACK_AD_UNIT_ID
            },
        )

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

            override fun onAdDisplayed(interstitialAdUnit: AudienzzInterstitialAdUnit) {}

            override fun onAdFailed(
                interstitialAdUnit: AudienzzInterstitialAdUnit,
                exception: AudienzzAdException?,
            ) {
                showErrorDialog(
                    adContainer.context,
                    exception?.message.orEmpty(),
                )
            }

            override fun onAdClicked(interstitialAdUnit: AudienzzInterstitialAdUnit) {}

            override fun onAdClosed(interstitialAdUnit: AudienzzInterstitialAdUnit) {}
        })

        button.addOnBecameVisibleOnScreenListener {
            displayAdUnit?.loadAd()
        }
    }

    private fun addVideoInterstitialAd() {
        val button = createButton(R.string.show_video_interstitial)

        val eventHandler = AudienzzGamInterstitialEventHandler(
            adContainer.context as Activity,
            VIDEO_AD_UNIT_ID,
        )
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

            override fun onAdDisplayed(interstitialAdUnit: AudienzzInterstitialAdUnit) {}

            override fun onAdFailed(
                interstitialAdUnit: AudienzzInterstitialAdUnit,
                exception: AudienzzAdException?,
            ) {
                showErrorDialog(
                    adContainer.context,
                    exception?.message.orEmpty(),
                )
            }

            override fun onAdClicked(interstitialAdUnit: AudienzzInterstitialAdUnit) {}

            override fun onAdClosed(interstitialAdUnit: AudienzzInterstitialAdUnit) {}
        })

        button.addOnBecameVisibleOnScreenListener { videoAdUnit?.loadAd() }
    }

    override fun onDetach() {
        displayAdUnit?.destroy()
        videoAdUnit?.destroy()
    }

    companion object {

        private const val DISPLAY_AD_UNIT_ID = "/21808260008/prebid_oxb_html_interstitial"
        private const val DISPLAY_CONFIG_ID = "prebid-demo-display-interstitial-320-480"

        private const val VIDEO_AD_UNIT_ID =
            "/21808260008/prebid_oxb_interstitial_video"
        const val VIDEO_CONFIG_ID = "prebid-demo-video-interstitial-320-480"

        private const val FALLBACK_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"
    }
}
