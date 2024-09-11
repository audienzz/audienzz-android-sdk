package org.audienzz.mobile.api.data

import org.audienzz.mobile.rendering.models.AudienzzPlacementType
import org.prebid.mobile.api.data.VideoPlacementType

enum class AudienzzVideoPlacementType(internal val prebidVideoPlacementType: VideoPlacementType) {
    IN_BANNER(VideoPlacementType.IN_BANNER),
    IN_ARTICLE(VideoPlacementType.IN_ARTICLE),
    IN_FEED(VideoPlacementType.IN_FEED), ;

    val value: Int = prebidVideoPlacementType.value

    companion object {

        @JvmStatic
        internal fun fromPrebidVideoPlacementType(videoPlacementType: VideoPlacementType) =
            values().find { it.prebidVideoPlacementType == videoPlacementType } ?: IN_BANNER

        @JvmStatic
        fun mapToVideoPlacementType(placementTypeValue: Int) =
            VideoPlacementType.mapToVideoPlacementType(placementTypeValue)?.let {
                AudienzzVideoPlacementType.fromPrebidVideoPlacementType(it)
            }

        @JvmStatic
        fun mapToPlacementType(
            videoPlacementType: AudienzzVideoPlacementType,
        ): AudienzzPlacementType = AudienzzPlacementType.fromPrebidPlacementType(
            VideoPlacementType.mapToPlacementType(videoPlacementType.prebidVideoPlacementType),
        )
    }
}
