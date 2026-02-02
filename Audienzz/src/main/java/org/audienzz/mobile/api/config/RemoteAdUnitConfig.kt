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
