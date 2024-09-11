package org.audienzz.mobile.rendering.bidding.listeners

import org.audienzz.mobile.AudienzzAdSize
import org.audienzz.mobile.rendering.bidding.data.bid.AudienzzBid

interface AudienzzBannerEventHandler {

    fun getAdSizeArray(): List<AudienzzAdSize>

    fun setBannerEventListener(bannerViewListener: AudienzzBannerEventListener)

    fun requestAdWithBid(bid: AudienzzBid?)

    fun trackImpression()

    fun destroy()
}
