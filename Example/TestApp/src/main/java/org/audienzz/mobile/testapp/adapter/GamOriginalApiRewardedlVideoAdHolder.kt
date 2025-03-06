package org.audienzz.mobile.testapp.adapter

import android.util.Log
import android.view.ViewGroup
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import org.audienzz.mobile.AudienzzRewardedVideoAdUnit
import org.audienzz.mobile.AudienzzSignals
import org.audienzz.mobile.AudienzzVideoParameters
import org.audienzz.mobile.original.AudienzzRewardedVideoAdHandler
import org.audienzz.mobile.testapp.R
import org.audienzz.mobile.util.AudienzzFullScreenContentCallback
import org.audienzz.mobile.util.lazyLoadAd

class GamOriginalApiRewardedlVideoAdHolder(parent: ViewGroup) : AdHolder(parent) {

    override val titleRes: Int
        get() = R.string.gam_original_rewarded_video_title

    private var adUnit: AudienzzRewardedVideoAdUnit? = null

    private var button: Button? = null
    private val logTagName: String = "Rewarded TAG"

    override fun createAds() {
        adUnit = AudienzzRewardedVideoAdUnit(CONFIG_ID)
        adUnit?.videoParameters = configureVideoParameters()

        val handler = AudienzzRewardedVideoAdHandler(adUnit!!, AD_UNIT_ID)

        button = createButton(R.string.show_rewarded)

        var rewarded: RewardedAd? = null
        adContainer.lazyLoadAd(
            adHandler = handler,
            listener = object : RewardedAdLoadCallback() {
                override fun onAdLoaded(rewardedAd: RewardedAd) {
                    button?.isEnabled = true
                    rewarded = rewardedAd
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    showAdLoadingErrorDialog(adContainer.context, loadAdError)
                }
            },
            resultCallback = { resultCode ->
                showFetchErrorDialog(adContainer.context, resultCode)
            },
            manager = AudienzzFullScreenContentCallback(
                onAdClickedAd = {
                    Log.d(logTagName, "ad was clicked")
                },
                onAdImpression = {
                    Log.d(logTagName, "on ad impression")
                },
                onAdDismissedFullScreen = {
                    Log.d(logTagName, "ad was dissmissed")
                },
                onAdShowedFullScreen = {
                    Log.d(logTagName, "ad was showed")
                },
            ),
            requestCallback = { request, listener ->
                RewardedAd.load(
                    adContainer.context,
                    AD_UNIT_ID,
                    request,
                    listener,
                )
            },
        )

        button?.setOnClickListener {
            (adContainer.context as? AppCompatActivity)?.let { activity ->
                rewarded?.show(activity) { }
            }
        }
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
