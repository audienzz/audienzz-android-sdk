package org.audienzz.mobile.api.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

@Serializable
data class RemoteAdUnitConfig(
    @SerialName("id")
    val id: Int,
    @SerialName("config")
    val config: RemoteConfig,
    @SerialName("gamConfig")
    val gamConfig: RemoteGamConfig,
    @SerialName("prebidConfig")
    val prebidConfig: RemotePrebidConfig,
)

@Serializable
data class RemoteConfig(
    @SerialName("adType")
    val adType: String,
    @SerialName("refreshTimeSeconds")
    val refreshTimeSeconds: Int? = null,
    @SerialName("prefetchDistanceDp")
    val prefetchDistanceDp: Int? = null,
    /**
     * Whether the banner defers its auction until it approaches the viewport.
     * null falls back to the SDK default (see AudienzzRemoteBannerView.DEFAULT_LAZY_LOAD).
     */
    @SerialName("lazyLoad")
    val lazyLoad: Boolean? = null,
    /** Reserved height (dp) for the sticky ad wrapper. null falls back to the SDK default (600). */
    @SerialName("stickyMaxHeight")
    val stickyMaxHeight: Int? = null,
    /** Y offset (dp) from the scroll viewport top where the sticky ad should pin.
     *  null falls back to 0. */
    @SerialName("stickyTopOffset")
    val stickyTopOffset: Int? = null,
)

@Serializable
data class RemoteGamConfig(
    @SerialName("adUnitPath")
    val adUnitPath: String,
    @SerialName("adSizes")
    private val adSizesRaw: List<String>,
    @SerialName("adaptiveBannerConfig")
    val adaptiveBannerConfig: RemoteAdaptiveBannerConfig? = null,
) {
    val adSizes: List<RemoteAdSize>
        get() = adSizesRaw.map { RemoteAdSizeMapper.map(it) }
}

@Serializable
data class RemoteAdaptiveBannerConfig(
    @SerialName("enabled")
    val enabled: Boolean = false,
    @SerialName("type")
    val type: String? = null,
    @SerialName("widthStrategy")
    val widthStrategy: String? = null,
    /** Requested adaptive width in density-independent pixels, not Android physical pixels. */
    @SerialName("customWidth")
    val customWidth: Int? = null,
    @SerialName("maxHeight")
    val maxHeight: Int? = null,
    @SerialName("orientationHandling")
    val orientationHandling: String? = null,
    @SerialName("includeReservationSizes")
    val isIncludeReservationSizes: Boolean = true,
)

@Serializable
data class RemotePrebidConfig(
    @SerialName("placementId")
    val placementId: String,
    @SerialName("adSizes")
    private val adSizesRaw: List<String>,
    /**
     * Interstitials: the media formats the bid request asks for. Held as raw JSON and read
     * leniently (see [format]): a strict type here made one malformed value drop the whole ad
     * config, and with it every load of that placement.
     */
    @SerialName("format")
    private val formatJson: JsonElement? = null,
    /** Interstitials: the OpenRTB API framework ids the impression advertises. See [apis]. */
    @SerialName("apis")
    private val apisJson: JsonElement? = null,
) {
    val adSizes: List<RemoteAdSize>
        get() = adSizesRaw.map { RemoteAdSizeMapper.map(it) }

    /**
     * `banner`, `video` or `bannerAndVideo` — the raw backend value; `InterstitialCapabilities`
     * validates it. null when absent or not a string.
     */
    val format: String?
        get() = (formatJson as? JsonPrimitive)?.takeIf { it.isString }?.content

    /**
     * The raw backend API ids, keeping only integral numbers (`3` or `3.0`); strings, booleans and
     * fractions are dropped. null when absent or not an array. Validated like [format].
     */
    val apis: List<Int>?
        get() = (apisJson as? JsonArray)?.mapNotNull { element ->
            val primitive = element as? JsonPrimitive ?: return@mapNotNull null
            if (primitive.isString) return@mapNotNull null
            val number = primitive.doubleOrNull ?: return@mapNotNull null
            number.takeIf { it == Math.rint(it) && kotlin.math.abs(it) <= Int.MAX_VALUE }?.toInt()
        }
}

data class RemoteAdSize(
    val width: Int,
    val height: Int,
)
