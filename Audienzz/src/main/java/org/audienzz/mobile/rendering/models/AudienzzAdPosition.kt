package org.audienzz.mobile.rendering.models

import org.prebid.mobile.rendering.models.AdPosition

enum class AudienzzAdPosition(internal val prebidAdPosition: AdPosition) {
    UNDEFINED(AdPosition.UNDEFINED),
    UNKNOWN(AdPosition.UNKNOWN),
    HEADER(AdPosition.HEADER),
    FOOTER(AdPosition.FOOTER),
    SIDEBAR(AdPosition.SIDEBAR),
    FULLSCREEN(AdPosition.FULLSCREEN), ;

    companion object {

        @JvmStatic
        internal fun fromPrebidAdPositionValue(value: Int) =
            values().find { it.prebidAdPosition.value == value } ?: UNDEFINED
    }
}
