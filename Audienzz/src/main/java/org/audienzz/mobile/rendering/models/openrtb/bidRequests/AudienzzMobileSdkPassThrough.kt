package org.audienzz.mobile.rendering.models.openrtb.bidRequests

import org.audienzz.mobile.api.data.AudienzzPosition
import org.audienzz.mobile.configuration.AudienzzAdUnitConfiguration
import org.json.JSONObject
import org.prebid.mobile.rendering.models.openrtb.bidRequests.MobileSdkPassThrough

/**
 * A class responsible for parsing "prebidmobilesdk" pass through type from bid response.
 * It contains interstitial control settings, like close button size or skip delay.
 * It can be located in ext.prebid.passthrough[] or seatbid[].bid[].ext.prebid.passthrough[].
 */
class AudienzzMobileSdkPassThrough internal constructor(
    internal val prebidMobileSdkPassThrough: MobileSdkPassThrough,
) {

    var isMuted: Boolean?
        get() = prebidMobileSdkPassThrough.isMuted
        set(value) {
            prebidMobileSdkPassThrough.isMuted = value
        }

    var maxVideoDuration: Int?
        get() = prebidMobileSdkPassThrough.maxVideoDuration
        set(value) {
            prebidMobileSdkPassThrough.maxVideoDuration = value
        }

    var skipDelay: Int?
        get() = prebidMobileSdkPassThrough.skipDelay
        set(value) {
            prebidMobileSdkPassThrough.skipDelay = value
        }

    var closeButtonArea: Double?
        get() = prebidMobileSdkPassThrough.closeButtonArea
        set(value) {
            prebidMobileSdkPassThrough.closeButtonArea = value
        }

    var skipButtonArea: Double?
        get() = prebidMobileSdkPassThrough.skipButtonArea
        set(value) {
            prebidMobileSdkPassThrough.skipButtonArea = value
        }

    var closeButtonPosition: AudienzzPosition?
        get() = AudienzzPosition.fromPrebidPosition(prebidMobileSdkPassThrough.closeButtonPosition)
        set(value) {
            prebidMobileSdkPassThrough.closeButtonPosition = value?.prebidPosition
        }

    var bannerTimeout: Int?
        get() = prebidMobileSdkPassThrough.bannerTimeout
        set(value) {
            prebidMobileSdkPassThrough.bannerTimeout = value
        }

    var preRenderTimeout: Int?
        get() = prebidMobileSdkPassThrough.preRenderTimeout
        set(value) {
            prebidMobileSdkPassThrough.preRenderTimeout = value
        }

    fun modifyAdUnitConfiguration(adUnitConfiguration: AudienzzAdUnitConfiguration) {
        prebidMobileSdkPassThrough.modifyAdUnitConfiguration(
            adUnitConfiguration.prebidAdUnitConfiguration,
        )
    }

    companion object {

        fun create(extJson: JSONObject): AudienzzMobileSdkPassThrough? =
            MobileSdkPassThrough.create(extJson)?.let { AudienzzMobileSdkPassThrough(it) }

        /**
         * Creates unified AudienzzMobileSdkPassThrough. An object from bid has higher priority.
         *
         * @param fromBid  - object from seatbid[].bid[].ext.prebid.passthrough[]
         * @param fromRoot - object from ext.prebid.passthrough[]
         */
        fun combine(
            fromBid: AudienzzMobileSdkPassThrough?,
            fromRoot: AudienzzMobileSdkPassThrough?,
        ): AudienzzMobileSdkPassThrough? =
            MobileSdkPassThrough.combine(
                fromBid?.prebidMobileSdkPassThrough,
                fromRoot?.prebidMobileSdkPassThrough,
            )?.let { AudienzzMobileSdkPassThrough(it) }

        /**
         * Combines unified pass through object with rendering controls
         * from ad unit configuration. Settings from ad unit configuration
         * have lower priority.
         */
        fun combine(
            unifiedPassThrough: AudienzzMobileSdkPassThrough?,
            configuration: AudienzzAdUnitConfiguration,
        ): AudienzzMobileSdkPassThrough = AudienzzMobileSdkPassThrough(
            MobileSdkPassThrough.combine(
                unifiedPassThrough?.prebidMobileSdkPassThrough,
                configuration.prebidAdUnitConfiguration,
            ),
        )
    }
}
