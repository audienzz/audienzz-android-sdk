package org.audienzz.mobile.eventhandlers

import android.app.Activity
import org.audienzz.mobile.api.exceptions.AudienzzAdException
import org.audienzz.mobile.rendering.bidding.data.bid.AudienzzBid
import org.audienzz.mobile.rendering.bidding.listeners.AudienzzRewardedEventHandler
import org.audienzz.mobile.rendering.bidding.listeners.AudienzzRewardedVideoEventListener
import org.prebid.mobile.api.exceptions.AdException
import org.prebid.mobile.eventhandlers.GamRewardedEventHandler
import org.prebid.mobile.rendering.bidding.listeners.RewardedVideoEventListener

class AudienzzGamRewardedEventHandler internal constructor(
    internal val prebidGamRewardedEventHandler: GamRewardedEventHandler,
) : AudienzzRewardedEventHandler, AudienzzGamAdEventListener {

    internal var adUnitId: String = ""

    constructor(activity: Activity, gamAdUnitId: String) : this(
        GamRewardedEventHandler(activity, gamAdUnitId),
    ) {
        this.adUnitId = gamAdUnitId
        setRewardedEventListener(object : AudienzzRewardedVideoEventListener {})
    }

    override fun onEvent(adEvent: AudienzzAdEvent) {
        prebidGamRewardedEventHandler.onEvent(adEvent.prebidAdEvent)
    }

    override fun setRewardedEventListener(listener: AudienzzRewardedVideoEventListener) {
        prebidGamRewardedEventHandler.setRewardedEventListener(
            getRewardedVideoEventListener(listener),
        )
    }

    override fun requestAdWithBid(bid: AudienzzBid?) {
        prebidGamRewardedEventHandler.requestAdWithBid(bid?.prebidBid)
    }

    override fun show() {
        prebidGamRewardedEventHandler.show()
    }

    override fun trackImpression() {
        prebidGamRewardedEventHandler.trackImpression()
    }

    override fun destroy() {
        prebidGamRewardedEventHandler.destroy()
    }

    private fun getRewardedVideoEventListener(listener: AudienzzRewardedVideoEventListener) =
        object : RewardedVideoEventListener {
            override fun onPrebidSdkWin() {
                listener.onPrebidSdkWin()
            }

            override fun onAdServerWin(userReward: Any?) {
                listener.onAdServerWin(userReward)
            }

            override fun onAdFailed(exception: AdException?) {
                listener.onAdFailed(exception?.let { AudienzzAdException(it) })
            }

            override fun onAdClicked() {
                listener.onAdClicked()
            }

            override fun onAdClosed() {
                listener.onAdClosed()
            }

            override fun onAdDisplayed() {
                listener.onAdDisplayed()
            }

            override fun onUserEarnedReward() {
                listener.onUserEarnedReward()
            }
        }
}
