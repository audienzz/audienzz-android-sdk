package org.audienzz.mobile.event.network.mapper

import org.audienzz.mobile.event.entity.EventDomain
import org.audienzz.mobile.event.network.entity.EventContainerNetwork
import org.audienzz.mobile.event.network.entity.EventDataNetwork
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject

internal class EventNetworkMapper @Inject constructor() {
    private val dateFormatter =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

    fun toNetwork(event: EventDomain): EventContainerNetwork = event.run {
        EventContainerNetwork(
            specversion = "1.0",
            source = "mobile-sdk",
            datacontenttype = "application/json",
            type = eventType?.nameString.orEmpty(),
            id = uuid.orEmpty(),
            time = dateFormatter.format(Date(timestamp)),
            data = EventDataNetwork(
                visitorId = visitorId,
                companyId = companyId,
                sessionId = sessionId,
                deviceId = deviceId,
                timestamp = null,
                resultCode = resultCode,
                adUnitId = adUnitId,
                adViewId = null,
                targetKeywords = targetKeywords,
                isAutorefresh = isAutorefresh,
                autorefreshTime = autorefreshTime,
                isRefresh = isRefresh,
                sizes = sizes,
                adType = adType?.nameString,
                adSubtype = adSubtype?.nameString,
                apiType = apiType?.nameString,
                errorMessage = errorMessage,
                screenName = screenName,
            ),
        )
    }
}
