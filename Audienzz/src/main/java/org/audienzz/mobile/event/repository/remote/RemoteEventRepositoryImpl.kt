package org.audienzz.mobile.event.repository.remote

import org.audienzz.mobile.event.network.EventApi
import org.audienzz.mobile.event.network.entity.EventNetwork
import javax.inject.Inject

internal class RemoteEventRepositoryImpl @Inject constructor(
    private val api: EventApi,
) : RemoteEventRepository {

    // Events arrive already mapped: the payload is frozen at creation time so it survives process
    // death with the context it was produced under. See EventLoggerImpl.
    override suspend fun submitBatch(events: List<EventNetwork>) {
        api.submit(events)
    }
}
