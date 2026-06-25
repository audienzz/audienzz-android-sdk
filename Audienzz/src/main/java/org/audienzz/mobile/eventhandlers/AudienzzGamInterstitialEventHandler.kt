package org.audienzz.mobile.eventhandlers

import android.app.Activity
import org.audienzz.mobile.api.exceptions.AudienzzAdException
import org.audienzz.mobile.api.rendering.AudienzzInterstitialAdUnit
import org.audienzz.mobile.rendering.bidding.data.bid.AudienzzBid
import org.audienzz.mobile.rendering.bidding.listeners.AudienzzInterstitialEventHandler
import org.audienzz.mobile.rendering.bidding.listeners.AudienzzInterstitialEventListener
import org.prebid.mobile.api.exceptions.AdException
import org.prebid.mobile.eventhandlers.GamInterstitialEventHandler
import org.prebid.mobile.rendering.bidding.listeners.InterstitialEventListener

class AudienzzGamInterstitialEventHandler internal constructor(
    internal val gamInterstitialEventHandler: GamInterstitialEventHandler,
) : AudienzzInterstitialEventHandler, AudienzzGamAdEventListener {

    internal var adUnitId: String = ""
    private var adUnit: AudienzzInterstitialAdUnit? = null

    constructor(activity: Activity, gamAdUnitId: String) : this(
        GamInterstitialEventHandler(activity, gamAdUnitId),
    ) {
        this.adUnitId = gamAdUnitId
        setInterstitialEventListener(object : AudienzzInterstitialEventListener {})
    }

    override fun setAdUnit(adUnit: AudienzzInterstitialAdUnit) {
        this.adUnit = adUnit
    }

    override fun setInterstitialEventListener(listener: AudienzzInterstitialEventListener?) {
        gamInterstitialEventHandler.setInterstitialEventListener(
            object : InterstitialEventListener {
                override fun onPrebidSdkWin() {
                    listener?.onPrebidSdkWin()
                }

                override fun onAdServerWin() {
                    listener?.onAdServerWin()
                }

                override fun onAdFailed(exception: AdException?) {
                    exception?.let {
                        listener?.onAdFailed(AudienzzAdException(it))
                    }
                }

                override fun onAdClosed() {
                    listener?.onAdClosed()
                }

                override fun onAdDisplayed() {
                    listener?.onAdDisplayed()
                }
            },
        )
    }

    override fun requestAdWithBid(bid: AudienzzBid?) {
        gamInterstitialEventHandler.requestAdWithBid(bid?.prebidBid)
    }

    override fun show() {
        gamInterstitialEventHandler.show()
    }

    override fun trackImpression() {
        gamInterstitialEventHandler.trackImpression()
    }

    override fun destroy() {
        gamInterstitialEventHandler.destroy()
    }

    override fun onEvent(adEvent: AudienzzAdEvent) {
        gamInterstitialEventHandler.onEvent(adEvent.prebidAdEvent)
    }
}
