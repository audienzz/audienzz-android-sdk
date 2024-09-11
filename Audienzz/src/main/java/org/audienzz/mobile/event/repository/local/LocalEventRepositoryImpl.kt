package org.audienzz.mobile.event.repository.local

import org.audienzz.mobile.event.EventDbMapper
import org.audienzz.mobile.event.database.dao.EventDao
import org.audienzz.mobile.event.entity.EventDomain
import javax.inject.Inject

internal class LocalEventRepositoryImpl @Inject constructor(
    private val dao: EventDao,
    private val dbMapper: EventDbMapper,
) : LocalEventRepository {

    override suspend fun saveEvent(event: EventDomain) {
        dao.insert(dbMapper.toDb(event))
    }

    override suspend fun getEvents(limit: Int) =
        dao.getOldestEvents(limit)
            .map(dbMapper::toDomain)

    override suspend fun deleteEvents(events: List<EventDomain>) {
        dao.delete(events.map(dbMapper::toDb))
    }
}
