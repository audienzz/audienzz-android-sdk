package org.audienzz.mobile.event.repository.remote

import org.audienzz.mobile.event.entity.EventDomain

internal interface RemoteEventRepository {

    suspend fun batchUpload(events: List<EventDomain>)
}
