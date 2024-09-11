package org.audienzz.mobile.rendering.bidding.listeners

import org.audienzz.mobile.rendering.bidding.data.bid.AudienzzBid

interface AudienzzRewardedEventHandler {

    fun setRewardedEventListener(listener: AudienzzRewardedVideoEventListener)

    fun requestAdWithBid(bid: AudienzzBid?)

    fun show()

    fun trackImpression()

    fun destroy()
}
