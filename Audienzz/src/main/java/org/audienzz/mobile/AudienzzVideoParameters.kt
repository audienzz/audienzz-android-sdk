package org.audienzz.mobile

import org.prebid.mobile.VideoParameters

data class AudienzzVideoParameters internal constructor(
    internal val prebidVideoParameters: VideoParameters,
) {

    /**
     * List of supported API frameworks for this impression. If an API is not explicitly listed,
     * it is assumed not to be supported.
     */
    var api: List<AudienzzSignals.Api>?
        get() = prebidVideoParameters.api?.map { AudienzzSignals.Api.fromPrebidApi(it) }
        set(value) {
            prebidVideoParameters.api = value?.map { it.prebidApi }
        }

    /**
     * Maximum bit rate in Kbps.
     */
    var maxBitrate: Int?
        get() = prebidVideoParameters.maxBitrate
        set(value) {
            prebidVideoParameters.maxBitrate = value
        }

    /**
     * Minimum bit rate in Kbps.
     */
    var minBitrate: Int?
        get() = prebidVideoParameters.minBitrate
        set(value) {
            prebidVideoParameters.minBitrate = value
        }

    /**
     * Maximum video ad duration in seconds.
     */
    var maxDuration: Int?
        get() = prebidVideoParameters.maxDuration
        set(value) {
            prebidVideoParameters.maxDuration = value
        }

    /**
     * Minimum video ad duration in seconds.
     */
    var minDuration: Int?
        get() = prebidVideoParameters.minDuration
        set(value) {
            prebidVideoParameters.minDuration = value
        }

    /**
     * Content MIME types supported
     * <p>
     * # Example #
     * "video/mp4"
     * "video/x-ms-wmv"
     */
    val mimes: List<String>? = prebidVideoParameters.mimes

    /**
     * Allowed playback methods. If none specified, assume all are allowed.
     */
    var playbackMethod: List<AudienzzSignals.PlaybackMethod>?
        get() = prebidVideoParameters.playbackMethod?.map {
            AudienzzSignals.PlaybackMethod.fromPrebidPlaybackMethod(it)
        }
        set(value) {
            prebidVideoParameters.playbackMethod = value?.map { it.prebidPlaybackMethod }
        }

    /**
     * Array of supported video bid response protocols.
     */
    var protocols: List<AudienzzSignals.Protocols>?
        get() = prebidVideoParameters.protocols?.map {
            AudienzzSignals.Protocols.fromPrebidProtocols(it)
        }
        set(value) {
            prebidVideoParameters.protocols = value?.map { it.prebidProtocols }
        }

    /**
     * Indicates the start delay in seconds for pre-roll, mid-roll, or post-roll ad placements.
     */
    var startDelay: AudienzzSignals.StartDelay?
        get() = prebidVideoParameters.startDelay?.let(
            AudienzzSignals.StartDelay::fromPrebidStartDelay,
        )
        set(value) {
            prebidVideoParameters.startDelay = value?.prebidStartDelay
        }

    /**
     * Placement type for the impression.
     */
    var placement: AudienzzSignals.Placement?
        get() = prebidVideoParameters.placement?.let(
            AudienzzSignals.Placement::fromPrebidPlacement,
        )
        set(value) {
            prebidVideoParameters.placement = value?.prebidPlacement
        }

    /**
     * Placement type for the impression.
     */
    var linearity: Int?
        get() = prebidVideoParameters.linearity
        set(value) {
            prebidVideoParameters.linearity = value
        }

    var adSize: AudienzzAdSize?
        get() = prebidVideoParameters.adSize?.let { AudienzzAdSize(it) }
        set(value) {
            prebidVideoParameters.adSize = value?.adSize
        }

    constructor(mimes: List<String>) : this(VideoParameters(mimes))
}
