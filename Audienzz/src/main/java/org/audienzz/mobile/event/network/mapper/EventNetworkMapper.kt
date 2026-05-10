package org.audienzz.mobile.event.network.mapper

import android.content.Context
import org.audienzz.mobile.event.entity.EventDomain
import org.audienzz.mobile.event.network.entity.EventNetwork
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject

internal class EventNetworkMapper @Inject constructor(
    private val context: Context,
) {
    private val dateFormatter =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

    fun toNetwork(event: EventDomain): EventNetwork {
        val metrics = context.resources.displayMetrics
        val screenWidthDp = (metrics.widthPixels / metrics.density).toInt()
        val screenHeightDp = (metrics.heightPixels / metrics.density).toInt()
        val zoneOffsetSeconds = TimeZone.getDefault().getOffset(event.timestamp) / 1000

        return EventNetwork(
            eventType = event.eventType?.nameString.orEmpty(),
            companyId = event.companyId,
            source = "android-sdk",
            eventId = event.uuid.orEmpty(),
            pageImpressionId = event.pageImpressionId,
            sessionId = event.sessionId,
            sessionStartTimestamp = event.sessionStartTimestamp,
            eventTimestamp = dateFormatter.format(Date(event.timestamp)),
            locale = Locale.getDefault().toLanguageTag(),
            zoneOffsetSeconds = zoneOffsetSeconds,
            screenHeight = screenHeightDp,
            screenWidth = screenWidthDp,
            viewportHeight = screenHeightDp,
            viewportWidth = screenWidthDp,
            pageUrl = event.screenName,
            visitorId = event.visitorId,
            attributes = buildAttributes(event),
        )
    }

    private fun buildAttributes(event: EventDomain): Map<String, String> = buildMap {
        event.deviceId?.let { put("device_id", it) }
        event.adUnitId?.let { put("ad_unit_id", it) }
        event.resultCode?.let { put("result_code", it) }
        event.sizes?.let { put("sizes", it) }
        event.adType?.let { put("ad_type", it.nameString) }
        event.adSubtype?.let { put("ad_subtype", it.nameString) }
        event.apiType?.let { put("api_type", it.nameString) }
        event.isAutorefresh?.let { put("autorefresh", it.toString()) }
        event.autorefreshTime?.let { put("autorefresh_time", it.toString()) }
        event.isRefresh?.let { put("refresh", it.toString()) }
        event.targetKeywords?.let { put("target_keywords", it.joinToString(",")) }
        event.errorMessage?.let { put("error_message", it) }
    }
}
