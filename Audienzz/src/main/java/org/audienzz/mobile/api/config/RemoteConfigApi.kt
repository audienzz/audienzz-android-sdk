package org.audienzz.mobile.api.config

import kotlinx.serialization.json.JsonArray
import retrofit2.http.GET
import retrofit2.http.Path

interface RemoteConfigApi {

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
