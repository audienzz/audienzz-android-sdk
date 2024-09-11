package org.audienzz.mobile

import androidx.annotation.IntRange
import org.audienzz.mobile.api.data.AudienzzAdUnitFormat
import org.prebid.mobile.InterstitialAdUnit
import java.util.EnumSet

class AudienzzInterstitialAdUnit internal constructor(
    private val adUnit: InterstitialAdUnit,
) : AudienzzBannerBaseAdUnit(adUnit) {

    constructor(configId: String) : this(InterstitialAdUnit(configId))

    constructor(
        configId: String,
        minWidthPerc: Int,
        minHeightPerc: Int,
    ) : this(InterstitialAdUnit(configId, minWidthPerc, minHeightPerc))

    constructor(
        configId: String,
        adUnitFormats: EnumSet<AudienzzAdUnitFormat>,
    ) : this(
        InterstitialAdUnit(
            configId,
            EnumSet.copyOf(adUnitFormats.map { it.prebidAdUnitFormat }),
        ),
    )

    fun setMinSizePercentage(
        @IntRange(from = 0, to = 100) width: Int,
        @IntRange(from = 0, to = 100) height: Int,
    ) {
        adUnit.setMinSizePercentage(width, height)
    }
}
