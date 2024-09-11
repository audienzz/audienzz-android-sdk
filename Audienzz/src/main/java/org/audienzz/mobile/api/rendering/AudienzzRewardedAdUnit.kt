package org.audienzz.mobile.api.rendering

import android.content.Context
import org.audienzz.mobile.api.exceptions.AudienzzAdException
import org.audienzz.mobile.api.rendering.listeners.AudienzzRewardedAdUnitListener
import org.audienzz.mobile.event.adClick
import org.audienzz.mobile.event.adCreation
import org.audienzz.mobile.event.adFailedToLoad
import org.audienzz.mobile.event.closeAd
import org.audienzz.mobile.event.entity.AdSubtype
import org.audienzz.mobile.event.entity.AdType
import org.audienzz.mobile.event.entity.ApiType
import org.audienzz.mobile.event.eventLogger
import org.audienzz.mobile.eventhandlers.AudienzzGamRewardedEventHandler
import org.audienzz.mobile.rendering.bidding.data.bid.AudienzzBid
import org.audienzz.mobile.rendering.bidding.listeners.AudienzzRewardedEventHandler
import org.audienzz.mobile.rendering.bidding.listeners.AudienzzRewardedVideoEventListener
import org.prebid.mobile.api.exceptions.AdException
import org.prebid.mobile.api.rendering.RewardedAdUnit
import org.prebid.mobile.api.rendering.listeners.RewardedAdUnitListener
import org.prebid.mobile.rendering.bidding.data.bid.Bid
import org.prebid.mobile.rendering.bidding.interfaces.RewardedEventHandler
import org.prebid.mobile.rendering.bidding.listeners.RewardedVideoEventListener

class AudienzzRewardedAdUnit internal constructor(
    internal val prebidRewardedAdUnit: RewardedAdUnit,
) : AudienzzBaseInterstitialAdUnit(prebidRewardedAdUnit) {

    val userReward: Any? get() = prebidRewardedAdUnit.userReward

    private var eventHandler: AudienzzRewardedEventHandler? = null

    constructor(
        context: Context,
        configId: String,
        eventHandler: AudienzzRewardedEventHandler,
    ) : this(RewardedAdUnit(context, configId, getRewardedEventHandler(eventHandler))) {
        this.eventHandler = eventHandler
        (eventHandler as? AudienzzGamRewardedEventHandler)?.adUnitId?.let {
            eventLogger?.adCreation(
                adUnitId = it,
                adType = AdType.BANNER,
                adSubtype = AdSubtype.MULTIFORMAT,
                apiType = ApiType.RENDER,
            )
        }
        setRewardedAdUnitListener(object : AudienzzRewardedAdUnitListener {})
    }

    constructor(context: Context, configId: String) : this(RewardedAdUnit(context, configId))

    fun setRewardedAdUnitListener(
        rewardedAdUnitListener: AudienzzRewardedAdUnitListener?,
    ) {
        prebidRewardedAdUnit.setRewardedAdUnitListener(
            rewardedAdUnitListener?.let {
                getRewardedAdUnitListener(
                    it,
                    (eventHandler as? AudienzzGamRewardedEventHandler)?.adUnitId,
                )
            },
        )
    }

    companion object {

        private fun getRewardedEventHandler(eventHandler: AudienzzRewardedEventHandler) =
            object : RewardedEventHandler {
                override fun setRewardedEventListener(listener: RewardedVideoEventListener) {
                    eventHandler.setRewardedEventListener(
                        getRewardedVideoEventListener(listener),
                    )
                }

                override fun requestAdWithBid(bid: Bid?) {
                    eventHandler.requestAdWithBid(bid?.let { AudienzzBid(it) })
                }

                override fun show() {
                    eventHandler.show()
                }

                override fun trackImpression() {
                    eventHandler.trackImpression()
                }

                override fun destroy() {
                    eventHandler.destroy()
                }
            }

        private fun getRewardedVideoEventListener(listener: RewardedVideoEventListener) =
            object : AudienzzRewardedVideoEventListener {
                override fun onPrebidSdkWin() {
                    listener.onPrebidSdkWin()
                }

                override fun onAdServerWin(userReward: Any?) {
                    listener.onAdServerWin(userReward)
                }

                override fun onAdFailed(exception: AudienzzAdException?) {
                    listener.onAdFailed(exception?.prebidAdException)
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

        private fun getRewardedAdUnitListener(
            listener: AudienzzRewardedAdUnitListener,
            adUnitId: String?,
        ) =
            object : RewardedAdUnitListener {
                override fun onAdLoaded(rewardedAdUnit: RewardedAdUnit?) {
                    listener.onAdLoaded(rewardedAdUnit?.let { AudienzzRewardedAdUnit(it) })
                }

                override fun onAdDisplayed(rewardedAdUnit: RewardedAdUnit?) {
                    listener.onAdDisplayed(rewardedAdUnit?.let { AudienzzRewardedAdUnit(it) })
                }

                override fun onAdFailed(rewardedAdUnit: RewardedAdUnit?, exception: AdException?) {
                    if (adUnitId != null) {
                        eventLogger?.adFailedToLoad(
                            adUnitId = adUnitId,
                            errorMessage = exception?.message,
                        )
                    }
                    listener.onAdFailed(
                        rewardedAdUnit?.let { AudienzzRewardedAdUnit(it) },
                        exception?.let { AudienzzAdException(it) },
                    )
                }

                override fun onAdClicked(rewardedAdUnit: RewardedAdUnit?) {
                    if (adUnitId != null) {
                        eventLogger?.adClick(adUnitId = adUnitId)
                    }
                    listener.onAdClicked(rewardedAdUnit?.let { AudienzzRewardedAdUnit(it) })
                }

                override fun onAdClosed(rewardedAdUnit: RewardedAdUnit?) {
                    if (adUnitId != null) {
                        eventLogger?.closeAd(adUnitId = adUnitId)
                    }
                    listener.onAdClosed(rewardedAdUnit?.let { AudienzzRewardedAdUnit(it) })
                }

                override fun onUserEarnedReward(rewardedAdUnit: RewardedAdUnit?) {
                    listener.onUserEarnedReward(rewardedAdUnit?.let { AudienzzRewardedAdUnit(it) })
                }
            }
    }
}
