package org.audienzz.mobile.api.rendering

import org.audienzz.mobile.configuration.AudienzzAdUnitConfiguration
import org.audienzz.mobile.rendering.bidding.data.bid.AudienzzBidResponse

interface AudienzzPrebidMobileInterstitialControllerInterface {

    fun loadAd(adUnitConfiguration: AudienzzAdUnitConfiguration?, bidResponse: AudienzzBidResponse?)

    fun show()

    fun destroy()
}
