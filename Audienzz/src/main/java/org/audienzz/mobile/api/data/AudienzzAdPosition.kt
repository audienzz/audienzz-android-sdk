package org.audienzz.mobile.api.data

import org.prebid.mobile.rendering.models.AdPosition

enum class AudienzzAdPosition(internal val prebidAdPosition: AdPosition) {
    UNDEFINED(AdPosition.UNDEFINED),
    UNKNOWN(AdPosition.UNKNOWN),
    HEADER(AdPosition.HEADER),
    FOOTER(AdPosition.FOOTER),
    SIDEBAR(AdPosition.SIDEBAR),
    ;

    val value: Int = prebidAdPosition.value

    companion object {

        @JvmStatic
        internal fun fromPrebidAdPosition(adPosition: AdPosition) =
            AudienzzAdPosition.entries.find { it.prebidAdPosition == adPosition }
                ?: UNDEFINED
    }
}
