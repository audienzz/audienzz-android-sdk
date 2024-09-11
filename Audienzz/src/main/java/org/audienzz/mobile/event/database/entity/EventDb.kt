package org.audienzz.mobile.event.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "event")
internal data class EventDb(
    @ColumnInfo(name = "id") @PrimaryKey(autoGenerate = true) val id: Int? = null,
    @ColumnInfo(name = "uuid") val uuid: String? = null,
    @ColumnInfo(name = "visitor_id") val visitorId: String?,
    @ColumnInfo(name = "company_id") val companyId: String?,
    @ColumnInfo(name = "session_id") val sessionId: String?,
    @ColumnInfo(name = "device_id") val deviceId: String?,
    @ColumnInfo(name = "event_type") val eventType: String?,
    @ColumnInfo(name = "timestamp") val timestamp: Long,
    @ColumnInfo(name = "result_code") val resultCode: String?,
    @ColumnInfo(name = "ad_unit_id") val adUnitId: String?,
    @ColumnInfo(name = "ad_view_id") val adViewId: String?,
    @ColumnInfo(name = "target_keywords") val targetKeywords: String?,
    @ColumnInfo(name = "autorefresh") val isAutorefresh: Boolean?,
    @ColumnInfo(name = "autorefresh_time") val autorefreshTime: Long?,
    @ColumnInfo(name = "refresh") val isRefresh: Boolean?,
    @ColumnInfo(name = "sizes") val sizes: String?,
    @ColumnInfo(name = "ad_type") val adType: String?,
    @ColumnInfo(name = "ad_subtype") val adSubtype: String?,
    @ColumnInfo(name = "api_type") val apiType: String?,
    @ColumnInfo(name = "error_message") val errorMessage: String?,
    @ColumnInfo(name = "screen_name") val screenName: String?,
)
