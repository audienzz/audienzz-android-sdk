package org.audienzz.mobile.event

import org.audienzz.mobile.event.database.entity.EventDb
import org.audienzz.mobile.event.entity.AdSubtype
import org.audienzz.mobile.event.entity.AdType
import org.audienzz.mobile.event.entity.ApiType
import org.audienzz.mobile.event.entity.EventDomain
import org.audienzz.mobile.event.entity.EventType
import javax.inject.Inject

internal class EventDbMapper @Inject constructor() {

    fun toDomain(eventDb: EventDb) = eventDb.run {
        EventDomain(
            localId = id,
            uuid = uuid,
            visitorId = visitorId,
            companyId = companyId,
            sessionId = sessionId,
            deviceId = deviceId,
            eventType = EventType.entries.find { it.nameString == eventType },
            timestamp = timestamp,
            resultCode = resultCode,
            adUnitId = adUnitId,
            adViewId = adViewId,
            targetKeywords = targetKeywords?.split(KEYWORDS_SEPARATOR),
            isAutorefresh = isAutorefresh,
            autorefreshTime = autorefreshTime,
            isRefresh = isRefresh,
            sizes = sizes,
            adType = AdType.entries.find { it.nameString == adType },
            adSubtype = AdSubtype.entries.find { it.nameString == adSubtype },
            apiType = ApiType.entries.find { it.nameString == apiType },
            errorMessage = errorMessage,
            screenName = screenName,
        )
    }

    fun toDb(event: EventDomain) = event.run {
        EventDb(
            id = localId,
            uuid = uuid,
            visitorId = visitorId,
            companyId = companyId,
            sessionId = sessionId,
            deviceId = deviceId,
            eventType = eventType?.nameString,
            timestamp = timestamp,
            resultCode = resultCode,
            adUnitId = adUnitId,
            adViewId = adViewId,
            targetKeywords = targetKeywords?.joinToString(KEYWORDS_SEPARATOR),
            isAutorefresh = isAutorefresh,
            autorefreshTime = autorefreshTime,
            isRefresh = isRefresh,
            sizes = sizes,
            adType = adType?.nameString,
            adSubtype = adSubtype?.nameString,
            apiType = apiType?.nameString,
            errorMessage = errorMessage,
            screenName = screenName,
        )
    }

    companion object {

        private const val KEYWORDS_SEPARATOR = ", "
    }
}
