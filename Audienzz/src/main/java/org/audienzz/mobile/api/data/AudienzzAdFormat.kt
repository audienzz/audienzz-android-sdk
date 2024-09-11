package org.audienzz.mobile.api.data

import org.prebid.mobile.api.data.AdFormat
import java.util.EnumSet

/**
 * Internal ad format. Must be set up only inside the SDK.
 */
enum class AudienzzAdFormat(internal val prebidAdFormat: AdFormat) {

    BANNER(AdFormat.BANNER),
    INTERSTITIAL(AdFormat.INTERSTITIAL),
    NATIVE(AdFormat.NATIVE),
    VAST(AdFormat.VAST), ;

    companion object {

        @JvmStatic
        fun fromSet(
            adUnitFormats: EnumSet<AudienzzAdUnitFormat>,
            isInterstitial: Boolean,
        ): EnumSet<AudienzzAdFormat> =
            EnumSet.copyOf(
                AdFormat.fromSet(
                    EnumSet.copyOf(adUnitFormats.map { it.prebidAdUnitFormat }),
                    isInterstitial,
                ).map { AudienzzAdFormat.fromPrebidAdFormat(it) },
            )

        @JvmStatic
        fun fromPrebidAdFormat(adFormat: AdFormat): AudienzzAdFormat =
            values().find { it.prebidAdFormat == adFormat } ?: BANNER
    }
}
