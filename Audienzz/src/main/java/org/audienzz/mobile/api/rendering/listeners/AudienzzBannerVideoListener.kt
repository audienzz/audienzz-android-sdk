package org.audienzz.mobile.api.rendering.listeners

import org.audienzz.mobile.api.rendering.AudienzzBannerView

/**
 * Listener interface representing banner video events.
 * All methods will be invoked on the main thread.
 */
interface AudienzzBannerVideoListener {

    /**
     * Executed when the video complete its playback
     *
     * @param bannerView view of the corresponding event.
     */
    fun onVideoCompleted(bannerView: AudienzzBannerView?)

    /**
     * Executed when the video playback is not visible
     *
     * @param bannerView view of the corresponding event.
     */
    fun onVideoPaused(bannerView: AudienzzBannerView?)

    /**
     * Executed when the video is paused and visibility constraints are satisfied again
     *
     * @param bannerView view of the corresponding event.
     */
    fun onVideoResumed(bannerView: AudienzzBannerView?)

    /**
     * Executed when the video playback is unmuted
     *
     * @param bannerView view of the corresponding event.
     */
    fun onVideoUnMuted(bannerView: AudienzzBannerView?)

    /**
     * Executed when the video playback is muted
     *
     * @param bannerView view of the corresponding event.
     */
    fun onVideoMuted(bannerView: AudienzzBannerView?)
}
