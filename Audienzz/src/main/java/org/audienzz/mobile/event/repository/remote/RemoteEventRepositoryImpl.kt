package org.audienzz.mobile.event.repository.remote

import org.audienzz.mobile.event.entity.EventDomain
import org.audienzz.mobile.event.network.EventApi
import org.audienzz.mobile.event.network.mapper.EventNetworkMapper
import javax.inject.Inject

internal class RemoteEventRepositoryImpl @Inject constructor(
    private val api: EventApi,
    private val mapper: EventNetworkMapper,
) : RemoteEventRepository {

    override suspend fun submit(event: EventDomain) {
        api.submit(mapper.toNetwork(event))
    }
}
