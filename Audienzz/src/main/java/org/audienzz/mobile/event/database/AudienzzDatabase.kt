package org.audienzz.mobile.event.database

import androidx.room.Database
import androidx.room.RoomDatabase
import org.audienzz.mobile.event.database.dao.EventDao
import org.audienzz.mobile.event.database.entity.EventDb

@Database(entities = [EventDb::class], version = 1)
internal abstract class AudienzzDatabase : RoomDatabase() {

    abstract fun eventDao(): EventDao
}
