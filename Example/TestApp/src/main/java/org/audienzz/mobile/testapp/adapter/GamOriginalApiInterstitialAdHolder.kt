package org.audienzz.mobile.testapp.adapter

import android.content.res.Configuration
import android.view.ViewGroup
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.admanager.AdManagerInterstitialAd
import com.google.android.gms.ads.admanager.AdManagerInterstitialAdLoadCallback
import org.audienzz.mobile.AudienzzInterstitialAdUnit
import org.audienzz.mobile.AudienzzSignals
import org.audienzz.mobile.AudienzzVideoParameters
import org.audienzz.mobile.api.data.AudienzzAdUnitFormat
import org.audienzz.mobile.original.AudienzzInterstitialAdHandler
import org.audienzz.mobile.testapp.R
import org.audienzz.mobile.util.getActivity
import org.audienzz.mobile.util.lazyLoadAd
import java.util.EnumSet
import java.util.Random

class GamOriginalApiInterstitialAdHolder(parent: ViewGroup) : AdHolder(parent) {

    override val titleRes: Int
        get() = R.string.gam_original_interstitial_video_title

    private var adUnitDisplay: AudienzzInterstitialAdUnit? = null
    private var adUnitVideo: AudienzzInterstitialAdUnit? = null
    private var adUnitMultiformat: AudienzzInterstitialAdUnit? = null

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
        adUnitDisplay = AudienzzInterstitialAdUnit(CONFIG_ID_BANNER, 80, 60)

        val orientation = adContainer.resources.configuration.orientation
        val handler = AudienzzInterstitialAdHandler(
            adUnitDisplay!!,
            if (orientation == Configuration.ORIENTATION_PORTRAIT) {
                AD_UNIT_ID_DISPLAY
            } else {
                FALLBACK_AD_UNIT_ID
            },
        )

        var interstitial: AdManagerInterstitialAd? = null

        buttonDisplay?.setOnClickListener {
            adContainer.context.getActivity()?.let {
                interstitial?.show(it)
            }
        }

        buttonDisplay?.lazyLoadAd(
            adHandler = handler,
            listener = object : AdManagerInterstitialAdLoadCallback() {
                override fun onAdLoaded(interstitialAd: AdManagerInterstitialAd) {
                    super.onAdLoaded(interstitialAd)
                    buttonDisplay?.isEnabled = true
                    interstitial = interstitialAd
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    super.onAdFailedToLoad(loadAdError)
                    showAdLoadingErrorDialog(adContainer.context, loadAdError)
                }
            },
            resultCallback = { resultCode ->
                showFetchErrorDialog(adContainer.context, resultCode)
            })
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
                context = adContainer.context,
                listener = createAdListener(buttonVideo),
                resultCallback = { resultCode ->
                    showFetchErrorDialog(adContainer.context, resultCode)
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
        adUnitMultiformat?.setMinSizePercentage(80, 60)
        adUnitMultiformat?.videoParameters = AudienzzVideoParameters(listOf("video/mp4"))

        buttonMultiformat = createButton(R.string.show_multiformat_interstitial)

        val handler = AudienzzInterstitialAdHandler(adUnitMultiformat!!, AD_UNIT_ID_MULTIFORMAT)
        buttonMultiformat?.isEnabled = true
        buttonMultiformat?.setOnClickListener {
            handler.load(
                context = adContainer.context,
                listener = createAdListener(buttonMultiformat),
                resultCallback = { resultCode ->
                    showFetchErrorDialog(adContainer.context, resultCode)
                },
            )
        }
    }

    private fun configureVideoParameters(): AudienzzVideoParameters {
        return AudienzzVideoParameters(listOf("video/x-flv", "video/mp4")).apply {
            placement = AudienzzSignals.Placement.Interstitial
            api = listOf(AudienzzSignals.Api.VPAID_1, AudienzzSignals.Api.VPAID_2)
            maxBitrate = 1500
            minBitrate = 300
            maxDuration = 30
            minDuration = 5
            playbackMethod = listOf(AudienzzSignals.PlaybackMethod.AutoPlaySoundOn)
            protocols = listOf(AudienzzSignals.Protocols.VAST_2_0)
        }
    }

    private fun createAdListener(button: Button?): AdManagerInterstitialAdLoadCallback {
        return object : AdManagerInterstitialAdLoadCallback() {
            override fun onAdLoaded(interstitialAd: AdManagerInterstitialAd) {
                super.onAdLoaded(interstitialAd)
                (adContainer.context as? AppCompatActivity)?.let {
                    interstitialAd.show(it)
                }
            }

            override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                super.onAdFailedToLoad(loadAdError)
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

        private const val AD_UNIT_ID_DISPLAY =
            "/21808260008/prebid-demo-app-original-api-display-interstitial"
        private const val AD_UNIT_ID_VIDEO =
            "/21808260008/prebid-demo-app-original-api-video-interstitial"
        private const val AD_UNIT_ID_MULTIFORMAT =
            "/21808260008/prebid-demo-intestitial-multiformat"

        private const val CONFIG_ID_BANNER = "prebid-demo-display-interstitial-320-480"
        private const val CONFIG_ID_VIDEO = "prebid-demo-video-interstitial-320-480-original-api"

        private const val FALLBACK_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"
    }
}
