package org.audienzz.mobile.rendering.bidding.interfaces

import org.audienzz.mobile.api.exceptions.AudienzzAdException

interface AudienzzInterstitialControllerListener {

    fun onInterstitialReadyForDisplay()

    fun onInterstitialClicked()

    fun onInterstitialFailedToLoad(exception: AudienzzAdException?)

    fun onInterstitialDisplayed()

    fun onInterstitialClosed()
}
