package org.audienzz.mobile.rendering.bidding.listeners

import org.audienzz.mobile.api.rendering.AudienzzInterstitialAdUnit
import org.audienzz.mobile.rendering.bidding.data.bid.AudienzzBid

interface AudienzzInterstitialEventHandler {

    fun setInterstitialEventListener(listener: AudienzzInterstitialEventListener?)

    fun requestAdWithBid(bid: AudienzzBid?)

    fun show()

    fun trackImpression()

    fun destroy()

    fun setAdUnit(adUnit: AudienzzInterstitialAdUnit)
}
