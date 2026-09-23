package org.audienzz.mobile

import androidx.annotation.IntRange
import org.audienzz.mobile.api.data.AudienzzAdUnitFormat
import org.audienzz.mobile.event.entity.AdSubtype
import org.prebid.mobile.InterstitialAdUnit
import java.util.EnumSet

class AudienzzInterstitialAdUnit internal constructor(
    private val adUnit: InterstitialAdUnit,
    private val formats: EnumSet<AudienzzAdUnitFormat>?,
    adSizes: Set<AudienzzAdSize>? = null,
) : AudienzzBannerBaseAdUnit(adUnit) {

    init {
        // The sizes that end up as `banner.format` in the bid request.
        //
        // [adSizes] is what the placement is actually configured for — the remote-config path
        // passes the sizes from the backend. Without it this fell back to a hardcoded 1x1, so a
        // 320x480 interstitial asked the exchange for a 1x1 slot: bidders size their response to
        // the format they are given, so the request did not describe the ad being filled.
        //
        // 1x1 remains the fallback for a caller that supplies nothing, because that is the
        // long-standing behaviour of the manual API and some setups do use it as a placeholder.
        val parameters = AudienzzBannerParameters()
        parameters.adSizes = adSizes?.takeIf { it.isNotEmpty() } ?: setOf(AudienzzAdSize(1, 1))
        adUnit.bannerParameters = parameters.prebidBannerParameters
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

    /**
     * As above, with the sizes the placement is configured for.
     *
     * Used by the remote-config path, which knows them from the backend. A caller that omits them
     * gets the 1x1 fallback described in `init`.
     */
    constructor(
        configId: String,
        adUnitFormats: EnumSet<AudienzzAdUnitFormat>,
        adSizes: Set<AudienzzAdSize>,
    ) : this(
        InterstitialAdUnit(
            configId,
            EnumSet.copyOf(adUnitFormats.map { it.prebidAdUnitFormat }),
        ),
        adUnitFormats,
        adSizes,
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
