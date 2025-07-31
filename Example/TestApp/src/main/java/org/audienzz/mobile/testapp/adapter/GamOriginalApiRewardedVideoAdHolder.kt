package org.audienzz.mobile.testapp.adapter

import android.util.Log
import android.view.ViewGroup
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import org.audienzz.mobile.AudienzzRewardedVideoAdUnit
import org.audienzz.mobile.AudienzzSignals
import org.audienzz.mobile.AudienzzVideoParameters
import org.audienzz.mobile.original.AudienzzRewardedVideoAdHandler
import org.audienzz.mobile.original.callbacks.AudienzzFullScreenContentCallback
import org.audienzz.mobile.original.callbacks.AudienzzRewardedAdLoadCallback
import org.audienzz.mobile.testapp.R
import org.audienzz.mobile.util.lazyAdLoader

class GamOriginalApiRewardedVideoAdHolder(parent: ViewGroup) : AdHolder(parent) {

    override val titleRes = R.string.gam_original_rewarded_video_title

    private var adUnit: AudienzzRewardedVideoAdUnit? = null

    private var lazyLoadedRewarded: RewardedAd? = null

    private var button: Button? = null
    private val logTagName: String = "[RewardedAd]"

    override fun createAds() {
        adUnit = AudienzzRewardedVideoAdUnit(CONFIG_ID)
        adUnit?.videoParameters = configureVideoParameters()

        val handler = AudienzzRewardedVideoAdHandler(adUnit!!, AD_UNIT_ID)

        button = createButton(R.string.show_rewarded)

        setLazyLoadedRewarded(handler)

        button?.setOnClickListener {
            (adContainer.context as? AppCompatActivity)?.let { activity ->
                lazyLoadedRewarded?.show(activity) { }
            }
        }
    }

    private fun setLazyLoadedRewarded(handler: AudienzzRewardedVideoAdHandler) {
        adContainer.lazyAdLoader(
            adHandler = handler,
            adLoadCallback = object : AudienzzRewardedAdLoadCallback() {
                override fun onAdLoaded(rewardedAd: RewardedAd) {
                    button?.isEnabled = true
                    lazyLoadedRewarded = rewardedAd
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    showAdLoadingErrorDialog(adContainer.context, loadAdError)
                }
            },
            fullScreenContentCallback = object : AudienzzFullScreenContentCallback() {
                override fun onAdClicked() {
                    super.onAdClicked()
                    Log.d(logTagName, "onAdClicked")
                }

                override fun onAdShowedFullScreenContent() {
                    super.onAdShowedFullScreenContent()
                    Log.d(logTagName, "onAdShowedFullScreenContent")
                }

                override fun onAdDismissedFullScreenContent() {
                    super.onAdDismissedFullScreenContent()
                    lazyLoadedRewarded = null
                    button?.isEnabled = false
                    setLazyLoadedRewarded(handler)
                    Log.d(logTagName, "onAdDismissedFullScreenContent")
                }

                override fun onAdImpression() {
                    super.onAdImpression()
                    Log.d(logTagName, "onAdImpression")
                }

                override fun onAdFailedToShowFullScreenContent(p0: AdError) {
                    super.onAdFailedToShowFullScreenContent(p0)
                    Log.d(logTagName, "onAdFailedToShowFullScreenContent")
                }
            },
            resultCallback = { resultCode, request, listener ->
                showFetchErrorDialog(adContainer.context, resultCode)
                RewardedAd.load(
                    adContainer.context,
                    AD_UNIT_ID,
                    request,
                    listener,
                )
                showFetchErrorDialog(adContainer.context, resultCode)
            },
        )
    }

    private fun configureVideoParameters(): AudienzzVideoParameters {
        return AudienzzVideoParameters(listOf("video/mp4")).apply {
            protocols = listOf(AudienzzSignals.Protocols.VAST_2_0)
            playbackMethod = listOf(AudienzzSignals.PlaybackMethod.AutoPlaySoundOff)
        }
    }

    companion object {
        const val AD_UNIT_ID = "ca-app-pub-3940256099942544/1712485313"
        const val CONFIG_ID = "34400101"
    }
}
