package org.audienzz.mobile.event.network

import org.audienzz.mobile.event.network.entity.EventNetwork
import retrofit2.http.Body
import retrofit2.http.POST

interface EventApi {

    @POST("submit/batch")
    suspend fun submit(@Body events: List<EventNetwork>)
}
