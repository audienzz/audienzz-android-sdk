package org.audienzz.mobile.event.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import org.audienzz.mobile.event.database.entity.PublisherConfigEntity

@Dao
interface PublisherConfigDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPublisherConfig(config: PublisherConfigEntity)

    @Query("SELECT * FROM publisher_config WHERE publisherId = :publisherId")
    suspend fun getPublisherConfig(publisherId: String): PublisherConfigEntity?
}
