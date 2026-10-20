package org.audienzz.mobile.api.config

import kotlinx.serialization.json.JsonArray
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * ws-sdk-config. The SDK id the app initializes with addresses `sdks/{sdkId}`; the `publishers/`
 * routes are what released SDKs call and are kept as a fallback when `sdks/` is not served.
 */
interface RemoteConfigApi {

    @GET("sdks/{sdkId}")
    suspend fun getSdkConfig(
        @Path("sdkId") sdkId: String,
    ): PublisherConfig

    @GET("sdks/{sdkId}/ad-configs")
    suspend fun getSdkAdUnitConfigs(
        @Path("sdkId") sdkId: String,
    ): JsonArray

    @GET("publishers/{publisherId}")
    suspend fun getPublisherConfig(
        @Path("publisherId") publisherId: String,
    ): PublisherConfig

    // H9: returns the raw JSON array (not List<RemoteAdUnitConfig>) so each element can be decoded
    // independently — one malformed ad-unit config must not discard every other config.
    @GET("publishers/{publisherId}/ad-configs")
    suspend fun getAdUnitConfigs(
        @Path("publisherId") publisherId: String,
    ): JsonArray

    @GET("publishers/{publisherId}/ad-configs/{adUnitConfigId}")
    suspend fun getAdUnitConfig(
        @Path("publisherId") publisherId: String,
        @Path("adUnitConfigId") adUnitConfigId: String,
    ): RemoteAdUnitConfig
}
