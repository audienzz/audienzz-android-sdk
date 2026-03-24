package org.audienzz.mobile.testapp.adapter.rendering

import android.app.Activity
import android.view.ViewGroup
import org.audienzz.mobile.AudienzzPrebidMobile
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
            AudienzzPrebidMobile.getAdUnitConfig(INTERSTITIAL_CONFIG_ID) { config ->
                config ?: return@getAdUnitConfig

                val eventHandler = AudienzzGamInterstitialEventHandler(
                    adContainer.context as Activity,
                    config.gamConfig.adUnitPath,
                )

                displayAdUnit = AudienzzInterstitialAdUnit(
                    adContainer.context,
                    config.prebidConfig.placementId,
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
    }

    private fun addVideoInterstitialAd() {
        val button = createButton(R.string.show_video_interstitial)

        button.addOnBecameVisibleOnScreenListener {
            AudienzzPrebidMobile.getAdUnitConfig(INTERSTITIAL_CONFIG_ID) { config ->
                config ?: return@getAdUnitConfig

                val eventHandler = AudienzzGamInterstitialEventHandler(
                    adContainer.context as Activity,
                    config.gamConfig.adUnitPath,
                )

                videoAdUnit = AudienzzInterstitialAdUnit(
                    adContainer.context,
                    config.prebidConfig.placementId,
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
    }

    override fun onDetach() {
        displayAdUnit?.destroy()
        videoAdUnit?.destroy()
    }

    companion object {
        private const val INTERSTITIAL_CONFIG_ID = "47"
    }
}
