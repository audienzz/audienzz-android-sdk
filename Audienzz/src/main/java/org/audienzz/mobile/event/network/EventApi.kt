package org.audienzz.mobile.event.network

import org.audienzz.mobile.event.network.entity.EventContainerNetwork
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

interface EventApi {

    @Headers("Content-Type: application/cloudevents-batch+json")
    @POST("submit/batch")
    suspend fun submitBatch(@Body events: List<EventContainerNetwork>)

    @Headers("Content-Type: application/cloudevents+json")
    @POST("submit")
    suspend fun submit(@Body event: EventContainerNetwork)
}
