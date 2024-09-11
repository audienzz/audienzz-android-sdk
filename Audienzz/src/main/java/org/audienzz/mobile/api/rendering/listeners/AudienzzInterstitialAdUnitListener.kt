package org.audienzz.mobile.api.rendering.listeners

import org.audienzz.mobile.api.exceptions.AudienzzAdException
import org.audienzz.mobile.api.rendering.AudienzzInterstitialAdUnit

/**
 * Listener interface representing InterstitialAdUnit events.
 * All methods will be invoked on the main thread.
 */
interface AudienzzInterstitialAdUnitListener {

    /**
     * Executed when the ad is loaded and is ready for display.
     *
     * @param interstitialAdUnit view of the corresponding event.
     */
    fun onAdLoaded(interstitialAdUnit: AudienzzInterstitialAdUnit) {}

    /**
     * Executed when the ad is displayed on screen.
     *
     * @param interstitialAdUnit view of the corresponding event.
     */
    fun onAdDisplayed(interstitialAdUnit: AudienzzInterstitialAdUnit) {}

    /**
     * Executed when an error is encountered on initialization / loading or display step.
     *
     * @param interstitialAdUnit view of the corresponding event.
     * @param exception  exception containing detailed message and error type.
     */
    fun onAdFailed(
        interstitialAdUnit: AudienzzInterstitialAdUnit,
        exception: AudienzzAdException?,
    ) {
    }

    /**
     * Executed when interstitialAdUnit is clicked.
     *
     * @param interstitialAdUnit view of the corresponding event.
     */
    fun onAdClicked(interstitialAdUnit: AudienzzInterstitialAdUnit) {}

    /**
     * Executed when interstitialAdUnit is closed.
     *
     * @param interstitialAdUnit view of the corresponding event.
     */
    fun onAdClosed(interstitialAdUnit: AudienzzInterstitialAdUnit) {}
}
