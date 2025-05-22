package org.audienzz.mobile.rendering.models

import org.prebid.mobile.rendering.models.PlacementType

enum class AudienzzPlacementType(internal val prebidPlacementType: PlacementType) {

    UNDEFINED(PlacementType.UNDEFINED),
    IN_BANNER(PlacementType.IN_BANNER),
    IN_ARTICLE(PlacementType.IN_ARTICLE),
    IN_FEED(PlacementType.IN_FEED),
    INTERSTITIAL(PlacementType.INTERSTITIAL), ;

    companion object {

        internal fun fromPrebidPlacementType(placementType: PlacementType) =
            AudienzzPlacementType.entries.find { it.prebidPlacementType == placementType }
                ?: UNDEFINED

        internal fun fromPrebidPlacementTypeValue(value: Int) =
            AudienzzPlacementType.entries.find { it.prebidPlacementType.value == value }
                ?: UNDEFINED
    }
}
