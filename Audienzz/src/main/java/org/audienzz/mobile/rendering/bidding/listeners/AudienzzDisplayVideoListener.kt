package org.audienzz.mobile.rendering.bidding.listeners

/**
 * Listener interface representing Display video events.
 * All methods will be invoked on the main thread.
 */
interface AudienzzDisplayVideoListener {

    // Called when the video complete its playback
    fun onVideoCompleted()

    // Called when the video playback is not visible
    fun onVideoPaused()

    // Called when the video is paused and visibility constraints are satisfied again
    fun onVideoResumed()

    // Called when the video playback is unmuted
    fun onVideoUnMuted()

    // Called when the video playback is muted
    fun onVideoMuted()
}
