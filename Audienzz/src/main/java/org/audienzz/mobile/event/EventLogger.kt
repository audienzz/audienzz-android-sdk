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

/**
 * Winning-bid economics captured when a bid resolves and reused across the render events
 * (`bidResponse`/`bidWon`/`adImpression`/`adClick`/`viewability.*`). Mirrors the web attribute set.
 */
internal data class RenderEconomics(
    val bidderCode: String? = null,
    val winnerBidderCode: String? = null,
    val winnerType: String? = null,
    val priceBucket: String? = null,
    val hbSize: String? = null,
    val hbFormat: String? = null,
    val mediaType: String? = null,
    val size: String? = null,
    val cpm: Double? = null,
    val currency: String? = null,
    val creativeId: String? = null,
    val auctionId: String? = null,
    val adId: String? = null,
    val timeToRespond: Long? = null,
    val slotReload: Int? = null,
)

private fun EventDomain.applyEconomics(ec: RenderEconomics?): EventDomain =
    if (ec == null) {
        this
    } else {
        copy(
            bidderCode = bidderCode ?: ec.bidderCode,
            winnerBidderCode = winnerBidderCode ?: ec.winnerBidderCode,
            winnerType = ec.winnerType,
            priceBucket = ec.priceBucket,
            hbSize = ec.hbSize,
            hbFormat = ec.hbFormat,
            mediaType = ec.mediaType,
            size = ec.size,
            cpm = ec.cpm,
            currency = ec.currency,
            creativeId = ec.creativeId,
            auctionId = ec.auctionId,
            adId = ec.adId,
            timeToRespond = timeToRespond ?: ec.timeToRespond,
            slotReload = ec.slotReload,
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
    adUnitCode: String? = null,
    mediaTypes: String? = null,
    auctionId: String? = null,
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
            adUnitCode = adUnitCode,
            mediaTypes = mediaTypes,
            auctionId = auctionId,
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
    adUnitCode: String? = null,
    economics: RenderEconomics? = null,
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
            adUnitCode = adUnitCode,
        ).applyEconomics(economics),
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
    adUnitCode: String? = null,
    economics: RenderEconomics? = null,
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
            adUnitCode = adUnitCode,
        ).applyEconomics(economics),
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
    adUnitCode: String? = null,
    mediaTypes: String? = null,
    auctionId: String? = null,
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
            adUnitCode = adUnitCode,
            mediaTypes = mediaTypes,
            auctionId = auctionId,
        ),
    )
}

@Suppress("LongParameterList")
internal fun EventLogger.adImpression(
    adUnitId: String,
    adType: AdType,
    adSubtype: AdSubtype,
    apiType: ApiType,
    adUnitCode: String? = null,
    economics: RenderEconomics? = null,
) {
    logEvent(
        EventDomain(
            eventType = AD_IMPRESSION,
            adUnitId = adUnitId,
            adType = adType,
            adSubtype = adSubtype,
            apiType = apiType,
            adUnitCode = adUnitCode,
        ).applyEconomics(economics),
    )
}

@Suppress("LongParameterList")
internal fun EventLogger.viewabilityStart(
    adUnitId: String,
    adType: AdType,
    adSubtype: AdSubtype,
    apiType: ApiType,
    adUnitCode: String? = null,
    economics: RenderEconomics? = null,
) {
    logEvent(
        EventDomain(
            eventType = VIEWABILITY_START,
            adUnitId = adUnitId,
            adType = adType,
            adSubtype = adSubtype,
            apiType = apiType,
            adUnitCode = adUnitCode,
        ).applyEconomics(economics),
    )
}

@Suppress("LongParameterList")
internal fun EventLogger.viewabilitySuccess(
    adUnitId: String,
    adType: AdType,
    adSubtype: AdSubtype,
    apiType: ApiType,
    adUnitCode: String? = null,
    economics: RenderEconomics? = null,
) {
    logEvent(
        EventDomain(
            eventType = VIEWABILITY_SUCCESS,
            adUnitId = adUnitId,
            adType = adType,
            adSubtype = adSubtype,
            apiType = apiType,
            adUnitCode = adUnitCode,
        ).applyEconomics(economics),
    )
}

@Suppress("LongParameterList")
internal fun EventLogger.adClick(
    adUnitId: String,
    adType: AdType? = null,
    adSubtype: AdSubtype? = null,
    apiType: ApiType? = null,
    adUnitCode: String? = null,
    economics: RenderEconomics? = null,
) {
    logEvent(
        EventDomain(
            eventType = AD_CLICK,
            adUnitId = adUnitId,
            adType = adType,
            adSubtype = adSubtype,
            apiType = apiType,
            adUnitCode = adUnitCode,
        ).applyEconomics(economics),
    )
}
