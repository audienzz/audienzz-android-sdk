package org.audienzz.mobile.testapp.adapter.original

import android.util.Log
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.admanager.AdManagerInterstitialAd
import org.audienzz.mobile.AudienzzInterstitialAdUnit
import org.audienzz.mobile.AudienzzPrebidMobile
import org.audienzz.mobile.AudienzzRemoteConfigInterstitial
import org.audienzz.mobile.AudienzzSignals
import org.audienzz.mobile.AudienzzVideoParameters
import org.audienzz.mobile.api.data.AudienzzAdUnitFormat
import org.audienzz.mobile.original.AudienzzInterstitialAdHandler
import org.audienzz.mobile.original.callbacks.AudienzzInterstitialAdLoadCallback
import org.audienzz.mobile.testapp.R
import org.audienzz.mobile.testapp.adapter.BaseAdHolder
import org.audienzz.mobile.testapp.constants.VideoConstants
import org.audienzz.mobile.testapp.utils.FullscreenAdUtils
import java.util.EnumSet

class OriginalApiInterstitialAdHolder(parent: ViewGroup) : BaseAdHolder(parent) {

    override val titleRes = R.string.original_api_interstitial_video_title

    private var displayInterstitial: AudienzzRemoteConfigInterstitial? = null

    override fun createAds() {
        createDisplayAd()
        createVideoAd()
        createMultiformatAd()
    }

    private fun createDisplayAd() {
        val button = createButton(R.string.show_display_interstitial)
        button.isEnabled = true
        button.setOnClickListener {
            displayInterstitial?.destroy()
            displayInterstitial = AudienzzRemoteConfigInterstitial(adContainer.context, INTERSTITIAL_CONFIG_ID)
            displayInterstitial?.loadAd()
        }
    }

    private fun createVideoAd() {
        val button = createButton(R.string.show_video_interstitial)
        button.isEnabled = true
        button.setOnClickListener {
            AudienzzPrebidMobile.getAdUnitConfig(INTERSTITIAL_CONFIG_ID) { config ->
                config ?: return@getAdUnitConfig

                val adUnit = AudienzzInterstitialAdUnit(
                    config.prebidConfig.placementId,
                    EnumSet.of(AudienzzAdUnitFormat.VIDEO),
                )
                adUnit.videoParameters = configureVideoParameters()

                AudienzzInterstitialAdHandler(adUnit, config.gamConfig.adUnitPath).load(
                    adLoadCallback = createAdLoadCallback(),
                    fullScreenContentCallback = FullscreenAdUtils.createFullScreenCallback(TAG),
                    resultCallback = { resultCode, request, listener ->
                        showFetchErrorDialog(adContainer.context, resultCode)
                        AdManagerInterstitialAd.load(
                            adContainer.context,
                            config.gamConfig.adUnitPath,
                            request,
                            listener,
                        )
                    },
                )
            }
        }
    }

    private fun createMultiformatAd() {
        val button = createButton(R.string.show_multiformat_interstitial)
        button.isEnabled = true
        button.setOnClickListener {
            AudienzzPrebidMobile.getAdUnitConfig(INTERSTITIAL_CONFIG_ID) { config ->
                config ?: return@getAdUnitConfig

                val adUnit = AudienzzInterstitialAdUnit(
                    config.prebidConfig.placementId,
                    EnumSet.of(AudienzzAdUnitFormat.BANNER, AudienzzAdUnitFormat.VIDEO),
                )
                adUnit.setMinSizePercentage(DEFAULT_MIN_WIDTH, DEFAULT_MIN_HEIGHT)
                adUnit.videoParameters = AudienzzVideoParameters(listOf("video/mp4"))

                AudienzzInterstitialAdHandler(adUnit, config.gamConfig.adUnitPath).load(
                    adLoadCallback = createAdLoadCallback(),
                    fullScreenContentCallback = FullscreenAdUtils.createFullScreenCallback(TAG),
                    resultCallback = { resultCode, request, listener ->
                        showFetchErrorDialog(adContainer.context, resultCode)
                        AdManagerInterstitialAd.load(
                            adContainer.context,
                            config.gamConfig.adUnitPath,
                            request,
                            listener,
                        )
                    },
                )
            }
        }
    }

    private fun configureVideoParameters(): AudienzzVideoParameters {
        return AudienzzVideoParameters(listOf("video/x-flv", "video/mp4")).apply {
            placement = AudienzzSignals.Placement.Interstitial
            api = listOf(
                AudienzzSignals.Api.VPAID_1,
                AudienzzSignals.Api.VPAID_2,
                AudienzzSignals.Api.OMID_1,
            )
            maxBitrate = VideoConstants.MAX_BITRATE
            minBitrate = VideoConstants.MIN_BITRATE
            maxDuration = VideoConstants.MAX_DURATION
            minDuration = VideoConstants.MIN_DURATION
            playbackMethod = listOf(AudienzzSignals.PlaybackMethod.AutoPlaySoundOn)
            protocols = listOf(AudienzzSignals.Protocols.VAST_2_0)
        }
    }

    private fun createAdLoadCallback(): AudienzzInterstitialAdLoadCallback {
        return object : AudienzzInterstitialAdLoadCallback() {
            override fun onAdLoaded(interstitialAd: AdManagerInterstitialAd) {
                super.onAdLoaded(interstitialAd)
                Log.d(TAG, "Ad was loaded")
                (adContainer.context as? AppCompatActivity)?.let {
                    interstitialAd.show(it)
                }
            }

            override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                super.onAdFailedToLoad(loadAdError)
                Log.d(TAG, "Ad failed to load")
                showAdLoadingErrorDialog(adContainer.context, loadAdError)
            }
        }
    }

    override fun onDetach() {
        displayInterstitial?.destroy()
    }

    companion object {
        private const val TAG = "Original API InterstitialAd"
        private const val INTERSTITIAL_CONFIG_ID = "47"
        private const val DEFAULT_MIN_WIDTH = 80
        private const val DEFAULT_MIN_HEIGHT = 60
    }
}
