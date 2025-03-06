package org.audienzz.mobile

import androidx.annotation.IntRange
import org.audienzz.mobile.api.data.AudienzzAdUnitFormat
import org.audienzz.mobile.event.entity.AdSubtype
import org.prebid.mobile.AdSize
import org.prebid.mobile.BannerParameters
import org.prebid.mobile.InterstitialAdUnit
import java.util.EnumSet

class AudienzzInterstitialAdUnit internal constructor(
    private val adUnit: InterstitialAdUnit,
    private val formats: EnumSet<AudienzzAdUnitFormat>?,
) : AudienzzBannerBaseAdUnit(adUnit) {

    init {

        val bannerParameters = BannerParameters()
        val adSizes = AdSize(1, 1)
        bannerParameters.adSizes = setOf(adSizes)
        adUnit.bannerParameters = bannerParameters
    }

    constructor(configId: String) : this(
        InterstitialAdUnit(configId),
        null,
    )

    constructor(
        configId: String,
        minWidthPerc: Int,
        minHeightPerc: Int,
    ) : this(
        InterstitialAdUnit(configId, minWidthPerc, minHeightPerc),
        null,
    )

    constructor(
        configId: String,
        adUnitFormats: EnumSet<AudienzzAdUnitFormat>,
    ) : this(
        InterstitialAdUnit(
            configId,
            EnumSet.copyOf(adUnitFormats.map { it.prebidAdUnitFormat }),
        ),
        adUnitFormats,
    )

    fun setMinSizePercentage(
        @IntRange(from = 0, to = 100) width: Int,
        @IntRange(from = 0, to = 100) height: Int,
    ) {
        adUnit.setMinSizePercentage(width, height)
    }

    internal fun getSubType(): AdSubtype {
        return when {
            formats.isNullOrEmpty() -> AdSubtype.HTML

            formats.containsAll(
                setOf(
                    AudienzzAdUnitFormat.VIDEO,
                    AudienzzAdUnitFormat.BANNER,
                ),
            ) -> AdSubtype.MULTIFORMAT

            formats.size == 1 -> when (formats.first()) {
                AudienzzAdUnitFormat.VIDEO -> AdSubtype.VIDEO
                AudienzzAdUnitFormat.BANNER -> AdSubtype.HTML
            }

            else -> AdSubtype.MULTIFORMAT
        }
    }
}
