package org.audienzz.mobile

import org.prebid.mobile.BannerParameters

data class AudienzzBannerParameters internal constructor(
    internal val prebidBannerParameters: BannerParameters,
) {

    init {
        if (prebidBannerParameters.api == null) {
            prebidBannerParameters.api = listOf(
                AudienzzSignals.Api.MRAID_2,
                AudienzzSignals.Api.MRAID_3,
                AudienzzSignals.Api.OMID_1,
            ).map { it.prebidApi }
        }
    }

    /**
     * List of supported API frameworks for this impression. If an API is not explicitly listed,
     * it is assumed not to be supported.
     */
    var api: List<AudienzzSignals.Api>?
        get() = prebidBannerParameters.api?.map { AudienzzSignals.Api.fromPrebidApi(it) }
        set(value) {
            prebidBannerParameters.api = value?.map { it.prebidApi }
        }

    var interstitialMinWidthPercentage: Int?
        get() = prebidBannerParameters.interstitialMinWidthPercentage
        set(value) {
            prebidBannerParameters.interstitialMinWidthPercentage = value
        }

    var interstitialMinHeightPercentage: Int?
        get() = prebidBannerParameters.interstitialMinHeightPercentage
        set(value) {
            prebidBannerParameters.interstitialMinHeightPercentage = value
        }

    var adSizes: Set<AudienzzAdSize>?
        get() = prebidBannerParameters.adSizes?.map { AudienzzAdSize(it) }?.toSet()
        set(value) {
            prebidBannerParameters.adSizes = value?.map { it.adSize }?.toSet()
        }

    constructor() : this(BannerParameters())
}
