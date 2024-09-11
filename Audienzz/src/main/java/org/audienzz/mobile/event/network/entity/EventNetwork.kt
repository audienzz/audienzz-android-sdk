package org.audienzz.mobile.event.network.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class EventContainerNetwork(
    @SerialName("specversion") val specversion: String,
    @SerialName("source") val source: String,
    @SerialName("datacontenttype") val datacontenttype: String,
    @SerialName("type") val type: String,
    @SerialName("id") val id: String,
    @SerialName("data") val data: EventDataNetwork,
    @SerialName("time") val time: String,
)

@Serializable
data class EventDataNetwork(
    @SerialName("visitorId") val visitorId: String?,
    @SerialName("companyId") val companyId: String?,
    @SerialName("sessionId") val sessionId: String?,
    @SerialName("deviceId") val deviceId: String?,
    @SerialName("timestamp") val timestamp: Long?,
    @SerialName("resultCode") val resultCode: String?,
    @SerialName("adUnitId") val adUnitId: String?,
    @SerialName("adViewId") val adViewId: String?,
    @SerialName("targetKeywords") val targetKeywords: List<String>?,
    @SerialName("autorefresh") val isAutorefresh: Boolean?,
    @SerialName("autorefreshTime") val autorefreshTime: Long?,
    @SerialName("refresh") val isRefresh: Boolean?,
    @SerialName("sizes") val sizes: String?,
    @SerialName("adType") val adType: String?,
    @SerialName("adSubtype") val adSubtype: String?,
    @SerialName("apiType") val apiType: String?,
    @SerialName("errorMessage") val errorMessage: String?,
    @SerialName("screenName") val screenName: String?,
)
