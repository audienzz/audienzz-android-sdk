package org.audienzz.mobile.event

import org.audienzz.mobile.di.MainComponent
import org.audienzz.mobile.event.entity.AdSubtype
import org.audienzz.mobile.event.entity.AdType
import org.audienzz.mobile.event.entity.ApiType
import org.audienzz.mobile.event.entity.EventDomain
import org.audienzz.mobile.event.entity.EventType.AD_CLICK
import org.audienzz.mobile.event.entity.EventType.AD_IMPRESSION
import org.audienzz.mobile.event.entity.EventType.AD_VIEW
import org.audienzz.mobile.event.entity.EventType.BID_REQUEST
import org.audienzz.mobile.event.entity.EventType.BID_RESPONSE
import org.audienzz.mobile.event.entity.EventType.BID_WON
import org.audienzz.mobile.event.entity.EventType.HEADER_LOADED
import org.audienzz.mobile.event.entity.EventType.NO_BID

internal interface EventLogger {

    fun logEvent(event: EventDomain)

    fun onScreenResumed(screenName: String)
}

internal val eventLogger: EventLogger?
    get() = MainComponent.eventLogger

internal fun EventLogger.headerLoaded(
    adViewId: String? = null,
    adUnitId: String,
    sizes: String? = null,
    adType: AdType,
    adSubtype: AdSubtype,
    apiType: ApiType,
) {
    logEvent(
        EventDomain(
            eventType = HEADER_LOADED,
            adViewId = adViewId,
            adUnitId = adUnitId,
            sizes = sizes,
            adType = adType,
            adSubtype = adSubtype,
            apiType = apiType,
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

@Suppress("LongParameterList")
internal fun EventLogger.bidResponse(
    adUnitId: String,
    adViewId: String? = null,
    resultCode: String?,
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
            eventType = BID_RESPONSE,
            adUnitId = adUnitId,
            adViewId = adViewId,
            resultCode = resultCode,
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

@Suppress("LongParameterList")
internal fun EventLogger.bidWon(
    adUnitId: String,
    adViewId: String? = null,
    targetKeywords: List<String>?,
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
            eventType = BID_WON,
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

@Suppress("LongParameterList")
internal fun EventLogger.noBid(
    adUnitId: String,
    adViewId: String? = null,
    resultCode: String?,
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
            eventType = NO_BID,
            adUnitId = adUnitId,
            adViewId = adViewId,
            resultCode = resultCode,
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

internal fun EventLogger.adImpression(
    adUnitId: String,
    adType: AdType,
    adSubtype: AdSubtype,
    apiType: ApiType,
) {
    logEvent(
        EventDomain(
            eventType = AD_IMPRESSION,
            adUnitId = adUnitId,
            adType = adType,
            adSubtype = adSubtype,
            apiType = apiType,
        ),
    )
}

internal fun EventLogger.adView(
    adUnitId: String,
    adType: AdType,
    adSubtype: AdSubtype,
    apiType: ApiType,
) {
    logEvent(
        EventDomain(
            eventType = AD_VIEW,
            adUnitId = adUnitId,
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
