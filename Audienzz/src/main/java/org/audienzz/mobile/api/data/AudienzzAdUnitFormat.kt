package org.audienzz.mobile.api.data

import org.prebid.mobile.api.data.AdUnitFormat

@Suppress("DEPRECATION")
enum class AudienzzAdUnitFormat(internal val prebidAdUnitFormat: AdUnitFormat) {
    BANNER(AdUnitFormat.BANNER),

    VIDEO(AdUnitFormat.VIDEO),

    @Deprecated("Use BANNER instead")
    DISPLAY(AdUnitFormat.DISPLAY),
}
