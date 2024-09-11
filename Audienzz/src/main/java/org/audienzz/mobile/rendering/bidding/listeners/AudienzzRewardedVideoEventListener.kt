package org.audienzz.mobile.rendering.bidding.listeners

import org.audienzz.mobile.api.exceptions.AudienzzAdException

interface AudienzzRewardedVideoEventListener {

    fun onPrebidSdkWin() {}

    fun onAdServerWin(userReward: Any?) {}

    fun onAdFailed(exception: AudienzzAdException?) {}

    fun onAdClicked() {}

    fun onAdClosed() {}

    fun onAdDisplayed() {}

    fun onUserEarnedReward() {}
}
