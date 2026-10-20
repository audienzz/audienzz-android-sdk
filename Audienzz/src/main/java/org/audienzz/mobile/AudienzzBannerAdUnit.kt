package org.audienzz.mobile

import org.audienzz.mobile.api.data.AudienzzAdUnitFormat
import org.prebid.mobile.BannerAdUnit
import org.prebid.mobile.api.data.AdFormat
import java.util.EnumSet

class AudienzzBannerAdUnit internal constructor(
    private val adUnit: BannerAdUnit,
) : AudienzzBannerBaseAdUnit(adUnit) {

    /** Whether [enableNativeFormat] has been applied. */
    val isNativeFormatEnabled: Boolean
        get() = adUnit.configuration.isAdType(AdFormat.NATIVE)

    init {
        adUnit.bannerParameters = AudienzzBannerParameters().prebidBannerParameters
    }

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

    /**
     * Also bid for native in this banner slot: the auction gets a native impression next to the
     * banner one, and Prebid adds `hb_format` (`banner` / `native`) to the GAM keys.
     *
     * Rendering stays in GAM: a native win is drawn by a GAM native style (Prebid Universal
     * Creative reading the cached bid), in the same `AdManagerAdView`. No app-side native views.
     * GAM must target its Prebid line items on `hb_format`, or a native win can land on the banner
     * creative. Call before the first `fetchDemand`.
     */
    @JvmOverloads
    fun enableNativeFormat(
        parameters: AudienzzNativeParameters = AudienzzNativeParameters.gamNativeStyleDefaults(),
    ) {
        // Prebid's own multiformat ad unit configures native exactly this way; its class is
        // package-private, so we apply the same configuration to the banner unit we already run.
        // addAdFormat(NATIVE) installs an empty native configuration, so set ours after it.
        adUnit.configuration.addAdFormat(AdFormat.NATIVE)
        adUnit.configuration.setNativeConfiguration(
            parameters.prebidNativeParameters.nativeConfiguration,
        )
    }
}
