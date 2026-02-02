package org.audienzz.mobile.testapp.adapter.original

import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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
    private var errorTextView: TextView? = null

    private lateinit var adWrapper: FrameLayout

    override fun createAds() {
        adContainer.removeAllViews()

        val context = adContainer.context

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        adWrapper = FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        button = Button(context).apply {
            text = context.getString(R.string.show_rewarded)
            isEnabled = false
        }

        errorTextView = TextView(context).apply {
            visibility = TextView.GONE
            setTextColor(ContextCompat.getColor(context, android.R.color.holo_red_dark))
            textSize = 14f
            setPadding(0, 8, 0, 0)
        }

        layout.addView(button)
        layout.addView(adWrapper)
        layout.addView(errorTextView)
        adContainer.addView(layout)

        adUnit = AudienzzRewardedVideoAdUnit(CONFIG_ID)
        adUnit?.videoParameters = configureVideoParameters()

        val handler = AudienzzRewardedVideoAdHandler(adUnit!!, AD_UNIT_ID)

        setLazyLoadedRewarded(handler)

        button?.setOnClickListener {
            (context as? AppCompatActivity)?.let { activity ->
                lazyLoadedRewarded?.show(activity) { }
            }
        }
    }

    private fun setLazyLoadedRewarded(handler: AudienzzRewardedVideoAdHandler) {
        adWrapper.lazyAdLoader(
            adHandler = handler,
            adLoadCallback = object : AudienzzRewardedAdLoadCallback() {
                override fun onAdLoaded(rewardedAd: RewardedAd) {
                    button?.isEnabled = true
                    lazyLoadedRewarded = rewardedAd
                    errorTextView?.isVisible = false
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    button?.isEnabled = false
                    errorTextView?.apply {
                        text = "Ad failed to load: ${loadAdError.message}"
                        isVisible = true
                    }
                }
            },
            fullScreenContentCallback = FullscreenAdUtils.createFullScreenCallback(TAG),
            resultCallback = { resultCode, request, listener ->
                errorTextView?.apply {
                    text = "Ad fetch error: code $resultCode"
                    visibility = TextView.VISIBLE
                }

                RewardedAd.load(
                    adWrapper.context,
                    AD_UNIT_ID,
                    request,
                    listener,
                )
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
