package org.audienzz.mobile.event.repository.local

import org.audienzz.mobile.event.entity.EventDomain

internal interface LocalEventRepository {

    suspend fun saveEvent(event: EventDomain)

    suspend fun getEvents(limit: Int): List<EventDomain>

    suspend fun deleteEvents(events: List<EventDomain>)
}
