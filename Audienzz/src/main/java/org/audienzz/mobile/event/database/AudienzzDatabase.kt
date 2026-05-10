package org.audienzz.mobile.event.database

import androidx.room.Database
import androidx.room.RoomDatabase
import org.audienzz.mobile.event.database.dao.PublisherConfigDao
import org.audienzz.mobile.event.database.dao.RemoteConfigDao
import org.audienzz.mobile.event.database.entity.PublisherConfigEntity
import org.audienzz.mobile.event.database.entity.RemoteConfigEntity

@Database(
    entities = [
        RemoteConfigEntity::class,
        PublisherConfigEntity::class,
    ],
    version = 3,
)
internal abstract class AudienzzDatabase : RoomDatabase() {

    abstract fun remoteConfigDao(): RemoteConfigDao
    abstract fun publisherConfigDao(): PublisherConfigDao
}
