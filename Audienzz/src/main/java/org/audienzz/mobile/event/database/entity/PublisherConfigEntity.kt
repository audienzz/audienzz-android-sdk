package org.audienzz.mobile.event.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * @param publisherId unique identifier of the publisher
 * @param data json string of the config
 * @param timestamp timestamp of the record creation
 */
@Entity(tableName = "publisher_config")
data class PublisherConfigEntity(
    @PrimaryKey
    val publisherId: String,
    val data: String,
    val timestamp: Long,
)
