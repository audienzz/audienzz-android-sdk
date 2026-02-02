package org.audienzz.mobile.api.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PublisherConfig(
    @SerialName("id")
    val id: Int,
    @SerialName("prebidServer")
    val prebidServerConfig: PrebidServerConfig,
    @SerialName("ortb")
    val ortbConfig: OrtbConfig? = null,
    @SerialName("android")
    val androidConfig: AndroidConfig? = null,
    @SerialName("ios")
    val iosConfig: IosConfig? = null,
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
