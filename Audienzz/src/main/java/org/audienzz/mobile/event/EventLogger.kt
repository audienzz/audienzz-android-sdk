package org.audienzz.mobile.event

import org.audienzz.mobile.di.MainComponent
import org.audienzz.mobile.event.entity.AdSubtype
import org.audienzz.mobile.event.entity.AdType
import org.audienzz.mobile.event.entity.ApiType
import org.audienzz.mobile.event.entity.EventDomain
import org.audienzz.mobile.event.entity.EventType.AD_CLICK
import org.audienzz.mobile.event.entity.EventType.AD_IMPRESSION
import org.audienzz.mobile.event.entity.EventType.BID_REQUEST
import org.audienzz.mobile.event.entity.EventType.BID_RESPONSE
import org.audienzz.mobile.event.entity.EventType.BID_WON
import org.audienzz.mobile.event.entity.EventType.NO_BID
import org.audienzz.mobile.event.entity.EventType.VIEWABILITY_START
import org.audienzz.mobile.event.entity.EventType.VIEWABILITY_SUCCESS

internal interface EventLogger {

    fun logEvent(event: EventDomain)

    fun onScreenResumed(screenName: String)
}

internal val eventLogger: EventLogger?
    get() = MainComponent.eventLogger

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
    timeToRespond: Long? = null,
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
            timeToRespond = timeToRespond,
        ),
    )
}

@Suppress("LongParameterList")
internal fun EventLogger.bidWon(
    adUnitId: String,
    adViewId: String? = null,
    isAutorefresh: Boolean,
    autorefreshTime: Long = 0,
    isRefresh: Boolean,
    sizes: String? = null,
    adType: AdType,
    adSubtype: AdSubtype,
    apiType: ApiType,
    priceBucket: String? = null,
    hbSize: String? = null,
    hbFormat: String? = null,
    cpm: Double? = null,
    currency: String? = null,
    creativeId: String? = null,
    auctionId: String? = null,
    adId: String? = null,
) {
    logEvent(
        EventDomain(
            eventType = BID_WON,
            adUnitId = adUnitId,
            adViewId = adViewId,
            isAutorefresh = isAutorefresh,
            autorefreshTime = autorefreshTime,
            isRefresh = isRefresh,
            sizes = sizes,
            adType = adType,
            adSubtype = adSubtype,
            apiType = apiType,
            priceBucket = priceBucket,
            hbSize = hbSize,
            hbFormat = hbFormat,
            cpm = cpm,
            currency = currency,
            creativeId = creativeId,
            auctionId = auctionId,
            adId = adId,
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

@Suppress("LongParameterList")
internal fun EventLogger.adImpression(
    adUnitId: String,
    adType: AdType,
    adSubtype: AdSubtype,
    apiType: ApiType,
    bidderCode: String? = null,
    winnerBidderCode: String? = null,
    cpm: Double? = null,
    currency: String? = null,
    creativeId: String? = null,
    auctionId: String? = null,
    adId: String? = null,
) {
    logEvent(
        EventDomain(
            eventType = AD_IMPRESSION,
            adUnitId = adUnitId,
            adType = adType,
            adSubtype = adSubtype,
            apiType = apiType,
            bidderCode = bidderCode,
            winnerBidderCode = winnerBidderCode,
            cpm = cpm,
            currency = currency,
            creativeId = creativeId,
            auctionId = auctionId,
            adId = adId,
        ),
    )
}

internal fun EventLogger.viewabilityStart(
    adUnitId: String,
    adType: AdType,
    adSubtype: AdSubtype,
    apiType: ApiType,
) {
    logEvent(
        EventDomain(
            eventType = VIEWABILITY_START,
            adUnitId = adUnitId,
            adType = adType,
            adSubtype = adSubtype,
            apiType = apiType,
        ),
    )
}

internal fun EventLogger.viewabilitySuccess(
    adUnitId: String,
    adType: AdType,
    adSubtype: AdSubtype,
    apiType: ApiType,
) {
    logEvent(
        EventDomain(
            eventType = VIEWABILITY_SUCCESS,
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
