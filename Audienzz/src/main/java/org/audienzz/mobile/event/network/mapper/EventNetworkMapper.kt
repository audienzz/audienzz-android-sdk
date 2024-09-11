package org.audienzz.mobile.event.network.mapper

import org.audienzz.mobile.event.entity.EventDomain
import org.audienzz.mobile.event.network.entity.EventContainerNetwork
import org.audienzz.mobile.event.network.entity.EventDataNetwork
import java.time.Instant
import java.time.format.DateTimeFormatter
import javax.inject.Inject

internal class EventNetworkMapper @Inject constructor() {

    fun toNetwork(event: EventDomain): EventContainerNetwork = event.run {
        EventContainerNetwork(
            specversion = "1.0",
            source = "mobile-sdk",
            datacontenttype = "application/json",
            type = eventType?.nameString.orEmpty(),
            id = uuid.orEmpty(),
            time = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(timestamp)),
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
