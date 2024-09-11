package org.audienzz.mobile.api.data

import org.audienzz.mobile.rendering.models.AudienzzAdPosition
import org.prebid.mobile.api.data.BannerAdPosition

enum class AudienzzBannerAdPosition(internal val prebidBannerAdPosition: BannerAdPosition) {
    UNDEFINED(BannerAdPosition.UNDEFINED),
    UNKNOWN(BannerAdPosition.UNKNOWN),
    HEADER(BannerAdPosition.HEADER),
    FOOTER(BannerAdPosition.FOOTER),
    SIDEBAR(BannerAdPosition.SIDEBAR), ;

    val value: Int = prebidBannerAdPosition.value

    companion object {

        @JvmStatic
        internal fun fromPrebidBannerAdPosition(bannerAdPosition: BannerAdPosition) =
            values().find { it.prebidBannerAdPosition == bannerAdPosition } ?: UNDEFINED

        @JvmStatic
        fun mapToDisplayAdPosition(adPosition: Int) =
            fromPrebidBannerAdPosition(BannerAdPosition.mapToDisplayAdPosition(adPosition))

        @JvmStatic
        fun mapToAdPosition(bannerAdPosition: AudienzzBannerAdPosition): AudienzzAdPosition =
            AudienzzAdPosition.fromPrebidAdPositionValue(
                BannerAdPosition.mapToAdPosition(bannerAdPosition.prebidBannerAdPosition).value,
            )
    }
}
