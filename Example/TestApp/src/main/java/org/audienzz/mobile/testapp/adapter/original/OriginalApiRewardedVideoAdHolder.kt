package org.audienzz.mobile.testapp.adapter.original

import android.view.ViewGroup
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import org.audienzz.mobile.AudienzzRewardedVideoAdUnit
import org.audienzz.mobile.AudienzzSignals
import org.audienzz.mobile.AudienzzVideoParameters
import org.audienzz.mobile.original.AudienzzRewardedVideoAdHandler
import org.audienzz.mobile.original.callbacks.AudienzzRewardedAdLoadCallback
import org.audienzz.mobile.testapp.R
import org.audienzz.mobile.testapp.adapter.BaseAdHolder
import org.audienzz.mobile.testapp.utils.FullscreenAdUtils
import org.audienzz.mobile.util.lazyAdLoader

class OriginalApiRewardedVideoAdHolder(parent: ViewGroup) : BaseAdHolder(parent) {

    override val titleRes = R.string.original_api_rewarded_video_title

    private var adUnit: AudienzzRewardedVideoAdUnit? = null

    private var lazyLoadedRewarded: RewardedAd? = null

    private var button: Button? = null

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
            fullScreenContentCallback = FullscreenAdUtils.createFullScreenCallback(TAG),
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
        private const val TAG = "Original API RewardedAd"
        private const val AD_UNIT_ID = "ca-app-pub-3940256099942544/1712485313"
        private const val CONFIG_ID = "34400101"
    }
}
