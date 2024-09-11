package org.audienzz.mobile.rendering.bidding.listeners

import android.view.View
import org.audienzz.mobile.api.exceptions.AudienzzAdException

interface AudienzzBannerEventListener {

    fun onPrebidSdkWin() {}

    fun onAdServerWin(view: View?) {}

    fun onAdFailed(exception: AudienzzAdException?) {}

    fun onAdClicked() {}

    fun onAdClosed() {}
}
