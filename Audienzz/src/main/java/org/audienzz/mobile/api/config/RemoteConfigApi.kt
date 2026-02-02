package org.audienzz.mobile.api.config

import retrofit2.http.GET
import retrofit2.http.Path

interface RemoteConfigApi {

    @GET("publishers/{publisherId}")
    suspend fun getPublisherConfig(
        @Path("publisherId") publisherId: String,
    ): PublisherConfig

    @GET("publishers/{publisherId}/ad-configs")
    suspend fun getAdUnitConfigs(
        @Path("publisherId") publisherId: String,
    ): List<RemoteAdUnitConfig>

    @GET("publishers/{publisherId}/ad-configs/{adUnitConfigId}")
    suspend fun getAdUnitConfig(
        @Path("publisherId") publisherId: String,
        @Path("adUnitConfigId") adUnitConfigId: String,
    ): RemoteAdUnitConfig
}
