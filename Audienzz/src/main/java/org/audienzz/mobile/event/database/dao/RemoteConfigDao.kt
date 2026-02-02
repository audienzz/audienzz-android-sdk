package org.audienzz.mobile.event.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import org.audienzz.mobile.event.database.entity.RemoteConfigEntity

@Dao
interface RemoteConfigDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAdConfig(config: RemoteConfigEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAdConfigs(configs: List<RemoteConfigEntity>)

    @Query("SELECT * FROM remote_config WHERE configId = :configId")
    suspend fun getAdConfig(configId: String): RemoteConfigEntity?

    @Query("DELETE FROM remote_config")
    suspend fun clearAdConfigs()
}
