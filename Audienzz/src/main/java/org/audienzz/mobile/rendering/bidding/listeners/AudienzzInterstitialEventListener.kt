package org.audienzz.mobile.rendering.bidding.listeners

import org.audienzz.mobile.api.exceptions.AudienzzAdException

interface AudienzzInterstitialEventListener {

    fun onPrebidSdkWin() {}

    fun onAdServerWin() {}

    fun onAdFailed(exception: AudienzzAdException) {}

    fun onAdClosed() {}

    fun onAdDisplayed() {}
}
