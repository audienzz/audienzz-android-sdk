package org.audienzz.mobile.testapp.adapter.original

import android.content.res.Configuration
import android.util.Log
import android.view.ViewGroup
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.admanager.AdManagerInterstitialAd
import org.audienzz.mobile.AudienzzInterstitialAdUnit
import org.audienzz.mobile.AudienzzSignals
import org.audienzz.mobile.AudienzzVideoParameters
import org.audienzz.mobile.api.data.AudienzzAdUnitFormat
import org.audienzz.mobile.original.AudienzzInterstitialAdHandler
import org.audienzz.mobile.original.callbacks.AudienzzInterstitialAdLoadCallback
import org.audienzz.mobile.testapp.R
import org.audienzz.mobile.testapp.adapter.BaseAdHolder
import org.audienzz.mobile.testapp.constants.VideoConstants
import org.audienzz.mobile.testapp.utils.FullscreenAdUtils
import org.audienzz.mobile.util.getActivity
import org.audienzz.mobile.util.lazyAdLoader
import java.util.EnumSet
import java.util.Random

class OriginalApiInterstitialAdHolder(parent: ViewGroup) : BaseAdHolder(parent) {

    override val titleRes = R.string.original_api_interstitial_video_title

    private var adUnitDisplay: AudienzzInterstitialAdUnit? = null
    private var adUnitVideo: AudienzzInterstitialAdUnit? = null
    private var adUnitMultiformat: AudienzzInterstitialAdUnit? = null

    private var lazyLoadedInterstitialAd: AdManagerInterstitialAd? = null

    private var buttonDisplay: Button? = null
    private var buttonVideo: Button? = null
    private var buttonMultiformat: Button? = null

    override fun createAds() {
        createDisplayAd()
        createVideoAd()
        createMultiformatAd()
    }

    private fun createDisplayAd() {
        buttonDisplay = createButton(R.string.show_display_interstitial)
        adUnitDisplay = AudienzzInterstitialAdUnit(
            CONFIG_ID_BANNER,
            DEFAULT_MIN_WIDTH,
            DEFAULT_MIN_HEIGHT,
        )

        val orientation = adContainer.resources.configuration.orientation
        val handler = AudienzzInterstitialAdHandler(
            adUnitDisplay!!,
            if (orientation == Configuration.ORIENTATION_PORTRAIT) {
                AD_UNIT_ID_DISPLAY
            } else {
                FALLBACK_AD_UNIT_ID
            },
        )

        buttonDisplay?.setOnClickListener {
            adContainer.context.getActivity()?.let {
                lazyLoadedInterstitialAd?.show(it)
            }
        }

        setLazyLoadInterstitialAd(handler)
    }

    private fun setLazyLoadInterstitialAd(handler: AudienzzInterstitialAdHandler) {
        buttonDisplay?.lazyAdLoader(
            adHandler = handler,
            adLoadCallback = object : AudienzzInterstitialAdLoadCallback() {
                override fun onAdLoaded(interstitialAd: AdManagerInterstitialAd) {
                    super.onAdLoaded(interstitialAd)
                    buttonDisplay?.isEnabled = true
                    lazyLoadedInterstitialAd = interstitialAd
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    super.onAdFailedToLoad(loadAdError)
                    showAdLoadingErrorDialog(adContainer.context, loadAdError)
                }
            },
            fullScreenContentCallback = FullscreenAdUtils.createFullScreenCallback(
                logTag = TAG,
                onAdDismissedCallback = {
                    lazyLoadedInterstitialAd = null
                    buttonDisplay?.isEnabled = false
                    setLazyLoadInterstitialAd(handler)
                },
            ),
            resultCallback = { resultCode, request, listener ->
                showFetchErrorDialog(adContainer.context, resultCode)
                AdManagerInterstitialAd.load(
                    adContainer.context,
                    AD_UNIT_ID_VIDEO,
                    request,
                    listener,
                )
            },
        )
    }

    private fun createVideoAd() {
        buttonVideo = createButton(R.string.show_video_interstitial)

        adUnitVideo = AudienzzInterstitialAdUnit(
            CONFIG_ID_VIDEO,
            EnumSet.of(AudienzzAdUnitFormat.VIDEO),
        )
        adUnitVideo?.videoParameters = configureVideoParameters()

        val handler = AudienzzInterstitialAdHandler(adUnitVideo!!, AD_UNIT_ID_VIDEO)
        buttonVideo?.isEnabled = true
        buttonVideo?.setOnClickListener {
            handler.load(
                adLoadCallback = createAdLoadCallback(),
                fullScreenContentCallback = FullscreenAdUtils.createFullScreenCallback(TAG),
                resultCallback = { resultCode, request, listener ->
                    showFetchErrorDialog(adContainer.context, resultCode)
                    AdManagerInterstitialAd.load(
                        adContainer.context,
                        AD_UNIT_ID_VIDEO,
                        request,
                        listener,
                    )
                },
            )
        }
    }

    private fun createMultiformatAd() {
        val configId = if (Random().nextBoolean()) {
            CONFIG_ID_BANNER
        } else {
            CONFIG_ID_VIDEO
        }

        adUnitMultiformat = AudienzzInterstitialAdUnit(
            configId,
            EnumSet.of(AudienzzAdUnitFormat.BANNER, AudienzzAdUnitFormat.VIDEO),
        )
        adUnitMultiformat?.setMinSizePercentage(
            DEFAULT_MIN_WIDTH,
            DEFAULT_MIN_HEIGHT,
        )
        adUnitMultiformat?.videoParameters = AudienzzVideoParameters(listOf("video/mp4"))

        buttonMultiformat = createButton(R.string.show_multiformat_interstitial)

        val handler = AudienzzInterstitialAdHandler(adUnitMultiformat!!, AD_UNIT_ID_MULTIFORMAT)
        buttonMultiformat?.isEnabled = true
        buttonMultiformat?.setOnClickListener {
            handler.load(
                adLoadCallback = createAdLoadCallback(),
                fullScreenContentCallback = FullscreenAdUtils.createFullScreenCallback(TAG),
                resultCallback = { resultCode, request, listener ->
                    showFetchErrorDialog(adContainer.context, resultCode)
                    AdManagerInterstitialAd.load(
                        adContainer.context,
                        AD_UNIT_ID_VIDEO,
                        request,
                        listener,
                    )
                },
            )
        }
    }

    private fun configureVideoParameters(): AudienzzVideoParameters {
        return AudienzzVideoParameters(listOf("video/x-flv", "video/mp4")).apply {
            placement = AudienzzSignals.Placement.Interstitial
            api = listOf(AudienzzSignals.Api.VPAID_1, AudienzzSignals.Api.VPAID_2)
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
                Log.d(TAG, "Ad failed to loaded")
                showAdLoadingErrorDialog(adContainer.context, loadAdError)
            }
        }
    }

    override fun onAttach() {
        adUnitDisplay?.resumeAutoRefresh()
        adUnitVideo?.resumeAutoRefresh()
        adUnitMultiformat?.resumeAutoRefresh()
    }

    override fun onDetach() {
        adUnitDisplay?.stopAutoRefresh()
        adUnitVideo?.stopAutoRefresh()
        adUnitMultiformat?.stopAutoRefresh()
    }

    companion object {
        private const val TAG = "Original API InterstitialAd"
        private const val AD_UNIT_ID_DISPLAY =
            "/96628199/de_audienzz.ch_v2/de_audienzz.ch_320_adnz_wideboard_1"
        private const val AD_UNIT_ID_VIDEO =
            "/96628199/de_audienzz.ch_v2/de_audienzz.ch_320_adnz_wideboard_1"
        private const val AD_UNIT_ID_MULTIFORMAT =
            "/96628199/de_audienzz.ch_v2/de_audienzz.ch_320_adnz_wideboard_1"
        private const val CONFIG_ID_BANNER = "34400101"
        private const val CONFIG_ID_VIDEO = "34400101"
        private const val FALLBACK_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"
        private const val DEFAULT_MIN_WIDTH = 80
        private const val DEFAULT_MIN_HEIGHT = 60
    }
}
