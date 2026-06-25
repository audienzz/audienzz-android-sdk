package org.audienzz.mobile.event.network.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class EventNetwork(
    @SerialName("event_type") val eventType: String,
    @SerialName("company_id") val companyId: String?,
    @SerialName("source") val source: String,
    @SerialName("event_id") val eventId: String,
    @SerialName("page_impression_id") val pageImpressionId: String?,
    @SerialName("session_id") val sessionId: String?,
    @SerialName("session_start_timestamp") val sessionStartTimestamp: Long?,
    @SerialName("session_sequence") val sessionSequence: Int,
    @SerialName("event_timestamp") val eventTimestamp: String,
    @SerialName("locale") val locale: String,
    @SerialName("zone_offset_seconds") val zoneOffsetSeconds: Int,
    @SerialName("screen_height") val screenHeight: Int,
    @SerialName("screen_width") val screenWidth: Int,
    @SerialName("viewport_height") val viewportHeight: Int,
    @SerialName("viewport_width") val viewportWidth: Int,
    @SerialName("page_url") val pageUrl: String?,
    @SerialName("visitor_id") val visitorId: String?,
    @SerialName("attributes") val attributes: Map<String, String>,
)
