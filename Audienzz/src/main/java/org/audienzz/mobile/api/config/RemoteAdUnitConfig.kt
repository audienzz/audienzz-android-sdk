package org.audienzz.mobile.api.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
) {
    val adSizes: List<RemoteAdSize>
        get() = adSizesRaw.map { RemoteAdSizeMapper.map(it) }
}

data class RemoteAdSize(
    val width: Int,
    val height: Int,
)
