package org.audienzz.mobile.api.rendering.listeners

import org.audienzz.mobile.api.exceptions.AudienzzAdException
import org.audienzz.mobile.api.rendering.AudienzzBannerView

/**
 * Listener interface representing BannerView events.
 * All methods will be invoked on the main thread.
 */
interface AudienzzBannerViewListener {

    /**
     * Executed when the ad is loaded and is ready for display.
     *
     * @param bannerView view of the corresponding event.
     */
    fun onAdLoaded(bannerView: AudienzzBannerView?)

    /**
     * Executed when the ad is displayed on screen.
     *
     * @param bannerView view of the corresponding event.
     */
    fun onAdDisplayed(bannerView: AudienzzBannerView?)

    /**
     * Executed when an error is encountered on initialization / loading or display step.
     *
     * @param bannerView view of the corresponding event.
     * @param exception  exception containing detailed message and error type.
     */
    fun onAdFailed(bannerView: AudienzzBannerView?, exception: AudienzzAdException?)

    /**
     * Executed when bannerView is clicked.
     *
     * @param bannerView view of the corresponding event.
     */
    fun onAdClicked(bannerView: AudienzzBannerView?)

    /**
     * Executed when modal window (e.g. browser) on top of bannerView is closed.
     *
     * @param bannerView view of the corresponding event.
     */
    fun onAdClosed(bannerView: AudienzzBannerView?)
}
