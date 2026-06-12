package org.audienzz.mobile.api.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PublisherConfig(
    @SerialName("id")
    val id: Int,
    @SerialName("prebidServer")
    val prebidServerConfig: PrebidServerConfig,
    @SerialName("gamConfig")
    val gamConfig: GamConfig? = null,
    @SerialName("ortb")
    val ortbConfig: OrtbConfig? = null,
    @SerialName("android")
    val androidConfig: AndroidConfig? = null,
    @SerialName("ios")
    val iosConfig: IosConfig? = null,
    /** Backend-controlled PPID enable flag. null when absent in the remote payload.
     *  Priority: client override > this value > SDK default (true). */
    @SerialName("ppidEnabled")
    val ppidEnabled: Boolean? = null,
)

/**
 * Google Mobile Ads global configuration, sourced from the backend publisher config.
 */
@Serializable
data class GamConfig(
    /**
     * Global app volume for GMA ad audio. Range: 0.0 (muted) – 1.0 (full volume).
     * Defaults to 0.0 (muted) if absent.
     */
    @SerialName("setAppVolume")
    val appVolume: Float? = 0f,
)

@Serializable
data class PrebidServerConfig(
    @SerialName("url")
    val url: String,
    @SerialName("accountId")
    val accountId: Int,
    @SerialName("statusUrl")
    val statusUrl: String? = null,
)

@Serializable
data class OrtbConfig(
    @SerialName("schain")
    val schainConfig: SchainConfig? = null,
    @SerialName("publisherName")
    val publisherName: String? = null,
    @SerialName("domain")
    val domain: String? = null,
)

@Serializable
data class SchainConfig(
    @SerialName("sellerId")
    val sellerId: String? = null,
    @SerialName("advertisingSystemDomain")
    val advertisingSystemDomain: String? = null,
)

@Serializable
data class AndroidConfig(
    @SerialName("ortb")
    val ortbConfig: AndroidOrtbConfig? = null,
)

@Serializable
data class IosConfig(
    @SerialName("ortb")
    val ortbConfig: IosOrtbConfig? = null,
)

@Serializable
data class AndroidOrtbConfig(
    @SerialName("bundleName")
    val bundleName: String? = null,
    @SerialName("storeUrl")
    val storeUrl: String? = null,
)

@Serializable
data class IosOrtbConfig(
    @SerialName("bundleId")
    val bundleId: String? = null,
    @SerialName("sourceApp")
    val sourceApp: String? = null,
    @SerialName("storeUrl")
    val storeUrl: String? = null,
)
