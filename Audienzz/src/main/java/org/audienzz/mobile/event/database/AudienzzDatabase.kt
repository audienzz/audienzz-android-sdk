package org.audienzz.mobile.event.database

import androidx.room.Database
import androidx.room.RoomDatabase
import org.audienzz.mobile.event.database.dao.EventDao
import org.audienzz.mobile.event.database.dao.PublisherConfigDao
import org.audienzz.mobile.event.database.dao.RemoteConfigDao
import org.audienzz.mobile.event.database.entity.EventDb
import org.audienzz.mobile.event.database.entity.PublisherConfigEntity
import org.audienzz.mobile.event.database.entity.RemoteConfigEntity

@Database(
    entities = [
        EventDb::class,
        RemoteConfigEntity::class,
        PublisherConfigEntity::class,
    ],
    version = 2,
)
internal abstract class AudienzzDatabase : RoomDatabase() {

    abstract fun eventDao(): EventDao
    abstract fun remoteConfigDao(): RemoteConfigDao
    abstract fun publisherConfigDao(): PublisherConfigDao
}
