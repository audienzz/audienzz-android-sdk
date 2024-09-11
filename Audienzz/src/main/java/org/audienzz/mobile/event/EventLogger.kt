package org.audienzz.mobile.event

import org.audienzz.mobile.di.MainComponent
import org.audienzz.mobile.event.entity.AdSubtype
import org.audienzz.mobile.event.entity.AdType
import org.audienzz.mobile.event.entity.ApiType
import org.audienzz.mobile.event.entity.EventDomain
import org.audienzz.mobile.event.entity.EventType.AD_CLICK
import org.audienzz.mobile.event.entity.EventType.AD_CREATION
import org.audienzz.mobile.event.entity.EventType.AD_FAILED_TO_LOAD
import org.audienzz.mobile.event.entity.EventType.BID_REQUEST
import org.audienzz.mobile.event.entity.EventType.BID_WINNER
import org.audienzz.mobile.event.entity.EventType.CLOSE_AD

internal interface EventLogger {

    fun logEvent(event: EventDomain)
}

internal val eventLogger: EventLogger?
    get() = MainComponent.eventLogger

@Suppress("LongParameterList")
internal fun EventLogger.bidWinner(
    resultCode: String?,
    adUnitId: String,
    adViewId: String? = null,
    targetKeywords: List<String>,
    isAutorefresh: Boolean,
    autorefreshTime: Long = 0,
    isRefresh: Boolean,
    sizes: String? = null,
    adType: AdType,
    adSubtype: AdSubtype,
    apiType: ApiType,
) {
    logEvent(
        EventDomain(
            eventType = BID_WINNER,
            resultCode = resultCode,
            adUnitId = adUnitId,
            adViewId = adViewId,
            targetKeywords = targetKeywords,
            isAutorefresh = isAutorefresh,
            autorefreshTime = autorefreshTime,
            isRefresh = isRefresh,
            sizes = sizes,
            adType = adType,
            adSubtype = adSubtype,
            apiType = apiType,
        ),
    )
}

internal fun EventLogger.adClick(
    adUnitId: String,
) {
    logEvent(
        EventDomain(
            eventType = AD_CLICK,
            adUnitId = adUnitId,
        ),
    )
}

@Suppress("LongParameterList")
internal fun EventLogger.bidRequest(
    adUnitId: String,
    adViewId: String? = null,
    isAutorefresh: Boolean,
    autorefreshTime: Long = 0,
    isRefresh: Boolean,
    sizes: String? = null,
    adType: AdType,
    adSubtype: AdSubtype,
    apiType: ApiType,
) {
    logEvent(
        EventDomain(
            eventType = BID_REQUEST,
            adUnitId = adUnitId,
            adViewId = adViewId,
            isAutorefresh = isAutorefresh,
            autorefreshTime = autorefreshTime,
            isRefresh = isRefresh,
            sizes = sizes,
            adType = adType,
            adSubtype = adSubtype,
            apiType = apiType,
        ),
    )
}

internal fun EventLogger.adCreation(
    adViewId: String? = null,
    adUnitId: String,
    sizes: String? = null,
    adType: AdType,
    adSubtype: AdSubtype,
    apiType: ApiType,
) {
    logEvent(
        EventDomain(
            eventType = AD_CREATION,
            adViewId = adViewId,
            adUnitId = adUnitId,
            sizes = sizes,
            adType = adType,
            adSubtype = adSubtype,
            apiType = apiType,
        ),
    )
}

internal fun EventLogger.closeAd(
    adUnitId: String,
) {
    logEvent(
        EventDomain(
            eventType = CLOSE_AD,
            adUnitId = adUnitId,
        ),
    )
}

internal fun EventLogger.adFailedToLoad(
    adUnitId: String,
    errorMessage: String?,
) {
    logEvent(
        EventDomain(
            eventType = AD_FAILED_TO_LOAD,
            adUnitId = adUnitId,
            errorMessage = errorMessage,
        ),
    )
}
