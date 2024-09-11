package org.audienzz.mobile.event.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import org.audienzz.mobile.event.database.entity.EventDb

@Dao
internal interface EventDao {

    @Insert
    fun insert(event: EventDb)

    @Query("SELECT * FROM event ORDER BY timestamp ASC LIMIT :limit")
    fun getOldestEvents(limit: Int): List<EventDb>

    @Delete
    fun delete(events: List<EventDb>)
}
