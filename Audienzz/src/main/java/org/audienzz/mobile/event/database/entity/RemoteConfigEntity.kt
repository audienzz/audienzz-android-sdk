package org.audienzz.mobile.event.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "remote_config")
data class RemoteConfigEntity(
    @PrimaryKey
    val configId: String,
    val adUnitId: String,
    val adType: String,
    val data: String, // JSON representation of AdUnitConfig
    val timestamp: Long,
)
