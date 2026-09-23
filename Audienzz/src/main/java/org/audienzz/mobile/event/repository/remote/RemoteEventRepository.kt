package org.audienzz.mobile.event.repository.remote

import org.audienzz.mobile.event.network.entity.EventNetwork

internal interface RemoteEventRepository {

    suspend fun submitBatch(events: List<EventNetwork>)
}
