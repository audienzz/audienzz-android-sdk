package org.audienzz.mobile.rendering.bidding.listeners

import org.audienzz.mobile.api.exceptions.AudienzzAdException

interface AudienzzDisplayViewListener {

    // Called every time an ad had loaded and is ready for display
    fun onAdLoaded()

    // Called every time the ad is displayed on the screen
    fun onAdDisplayed()

    // Called every time the ad is displayed on the screen
    fun onAdFailed(exception: AudienzzAdException?)

    // Called when the banner view will launch a dialog on top of the current view
    fun onAdClicked()

    // Called when the banner view has dismissed the modal on top of the current view
    fun onAdClosed()
}
