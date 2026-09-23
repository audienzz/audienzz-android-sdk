package org.audienzz.mobile

import androidx.annotation.IntRange
import org.audienzz.mobile.event.entity.AdSubtype
import org.prebid.mobile.InterstitialAdUnit

/**
 * An interstitial built by hand.
 *
 * The formats and API frameworks it requests are not arguments: they are backend-controlled (see
 * [InterstitialCapabilities]), and a hand-built interstitial, which has no ad config, asks for
 * banner and video with MRAID 1/2/3 + OMID 1. `bannerParameters.api`, `videoParameters.api` and
 * `impOrtbConfig` cannot change that; their other settings are kept.
 */
class AudienzzInterstitialAdUnit internal constructor(
    private val adUnit: InterstitialAdUnit,
    adSizes: Set<AudienzzAdSize>? = null,
) : AudienzzBannerBaseAdUnit(adUnit) {

    /**
     * Formats and API frameworks this interstitial's requests advertise. The default unless a
     * bridge that read the ad config itself hands the backend values over
     * ([setBackendCapabilities]), or the remote-config owner resolves them. Never a publisher
     * setting.
     */
    internal var capabilities: InterstitialCapabilities = InterstitialCapabilities.DEFAULT

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
        applyCapabilities()
    }

    constructor(configId: String) : this(InterstitialAdUnit(configId))

    constructor(
        configId: String,
        minWidthPerc: Int,
        minHeightPerc: Int,
    ) : this(InterstitialAdUnit(configId, minWidthPerc, minHeightPerc))

    /** The remote-config path: the placement's sizes, from the backend. */
    internal constructor(configId: String, adSizes: Set<AudienzzAdSize>) :
        this(InterstitialAdUnit(configId), adSizes)

    /**
     * Writes [capabilities] onto the Prebid unit. Called by the handler for every accepted
     * request, after anything the publisher set, so the backend values always win.
     */
    internal fun applyCapabilities() = capabilities.apply(adUnit)

    /**
     * Bridge-only: the ad config's raw `prebidConfig.format` / `prebidConfig.apis`, for a bridge
     * whose remote interstitials read the ad config themselves (Flutter). Validated exactly as the
     * native remote interstitial validates them; takes effect on the next accepted request.
     */
    @AudienzzBridgeApi
    fun setBackendCapabilities(format: String?, apis: List<Int>?) {
        capabilities = InterstitialCapabilities.resolve(format, apis)
    }

    fun setMinSizePercentage(
        @IntRange(from = 0, to = 100) width: Int,
        @IntRange(from = 0, to = 100) height: Int,
    ) {
        adUnit.setMinSizePercentage(width, height)
    }

    internal fun getSubType(): AdSubtype = capabilities.adSubtype
}
