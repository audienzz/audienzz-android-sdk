package org.audienzz.mobile

import org.audienzz.mobile.api.data.AudienzzAdUnitFormat
import org.prebid.mobile.BannerAdUnit
import java.util.EnumSet

class AudienzzBannerAdUnit internal constructor(
    private val adUnit: BannerAdUnit,
) : AudienzzBannerBaseAdUnit(adUnit) {

    constructor(
        configId: String,
        width: Int,
        height: Int,
        adUnitFormats: EnumSet<AudienzzAdUnitFormat>,
    ) : this(
        BannerAdUnit(
            configId,
            width,
            height,
            EnumSet.copyOf(adUnitFormats.map { it.prebidAdUnitFormat }),
        ),
    )

    constructor(
        configId: String,
        width: Int,
        height: Int,
    ) : this(BannerAdUnit(configId, width, height))

    fun addAdditionalSize(width: Int, height: Int) {
        adUnit.addAdditionalSize(width, height)
    }
}
