package org.audienzz.mobile.original

import com.google.android.gms.ads.admanager.AdManagerAdRequest
import org.audienzz.mobile.AudienzzPrebidMobile
import org.audienzz.mobile.AudienzzResultCode
import org.audienzz.mobile.AudienzzTargetingParams
import org.audienzz.mobile.api.data.AudienzzBidInfo
import org.audienzz.mobile.api.original.AudienzzPrebidAdUnit
import org.audienzz.mobile.api.original.AudienzzPrebidRequest
import org.audienzz.mobile.event.bidRequest
import org.audienzz.mobile.event.bidResponse
import org.audienzz.mobile.event.RenderEconomics
import org.audienzz.mobile.event.bidWon
import org.audienzz.mobile.event.entity.AdSubtype
import org.audienzz.mobile.event.entity.AdType
import org.audienzz.mobile.event.entity.ApiType
import org.audienzz.mobile.event.eventLogger
import org.audienzz.mobile.event.noBid
import org.audienzz.mobile.util.HB_BIDDER_KEY
import org.audienzz.mobile.util.HB_FORMAT_KEY
import org.audienzz.mobile.util.HB_PB_KEY
import org.audienzz.mobile.util.HB_SIZE_KEY
import org.audienzz.mobile.util.audienzzSizesJson
import org.audienzz.mobile.util.noBidResultCode
import java.util.UUID

class AudienzzMultiformatAdHandler(
    private val adUnit: AudienzzPrebidAdUnit,
    private val adUnitId: String,
) {

    private var isFirstDemandFetch = true
    // SDK-generated auction id, minted at auction start and reused across the auction's events.
    private var currentAuctionId: String? = null

    @JvmOverloads fun load(
        gamRequestBuilder: AdManagerAdRequest.Builder = AdManagerAdRequest.Builder(),
        prebidRequest: AudienzzPrebidRequest,
        callback: (AudienzzBidInfo) -> Unit,
    ) {
        val isAutorefresh = adUnit.autoRefreshTime > 0
        val autorefreshTime = adUnit.autoRefreshTime.toLong()
        val isRefresh = !isFirstDemandFetch
        isFirstDemandFetch = false
        // Mint the auction id up front so bidRequest and every later event of this auction share it.
        currentAuctionId = UUID.randomUUID().toString()

        eventLogger?.bidRequest(
            adUnitId = adUnitId,
            sizes = prebidRequest.getAdSizes().audienzzSizesJson,
            adType = AdType.BANNER,
            adSubtype = AdSubtype.MULTIFORMAT,
            apiType = ApiType.ORIGINAL,
            autorefreshTime = autorefreshTime,
            isAutorefresh = isAutorefresh,
            isRefresh = isRefresh,
            auctionId = currentAuctionId,
        )
        val ppid = AudienzzPrebidMobile.ppidManager?.getPpid()
        if (ppid != null) {
            gamRequestBuilder.setPublisherProvidedId(ppid)
        }

        val request = AudienzzTargetingParams.CUSTOM_TARGETING_MANAGER.applyToGamRequestBuilder(
            gamRequestBuilder,
        )
            .build()
        adUnit.fetchDemand(request, prebidRequest) { bidInfo ->
            callback.invoke(bidInfo)
            eventLogger?.bidResponse(
                adUnitId = adUnitId,
                sizes = prebidRequest.getAdSizes().audienzzSizesJson,
                adType = AdType.BANNER,
                adSubtype = AdSubtype.MULTIFORMAT,
                apiType = ApiType.ORIGINAL,
                autorefreshTime = autorefreshTime,
                isAutorefresh = isAutorefresh,
                isRefresh = isRefresh,
                resultCode = bidInfo.resultCode.toString(),
            )
            // Prebid reports SUCCESS even for an empty/error response (e.g. STORED_REQUEST_NOT_FOUND).
            // A real Prebid win always carries hb_bidder, so gate the win on it; otherwise it's a no-bid.
            val winningBidder = bidInfo.targetingKeywords?.get(HB_BIDDER_KEY)
            if (bidInfo.resultCode == AudienzzResultCode.SUCCESS && winningBidder != null) {
                eventLogger?.bidWon(
                    adUnitId = adUnitId,
                    sizes = prebidRequest.getAdSizes().audienzzSizesJson,
                    adType = AdType.BANNER,
                    adSubtype = AdSubtype.MULTIFORMAT,
                    apiType = ApiType.ORIGINAL,
                    autorefreshTime = autorefreshTime,
                    isAutorefresh = isAutorefresh,
                    isRefresh = isRefresh,
                    economics = RenderEconomics(
                        bidderCode = winningBidder,
                        winnerBidderCode = winningBidder,
                        winnerType = WINNER_TYPE_RTB,
                        priceBucket = bidInfo.targetingKeywords?.get(HB_PB_KEY),
                        hbSize = bidInfo.targetingKeywords?.get(HB_SIZE_KEY),
                        hbFormat = bidInfo.targetingKeywords?.get(HB_FORMAT_KEY),
                        mediaType = bidInfo.targetingKeywords?.get(HB_FORMAT_KEY),
                        size = bidInfo.targetingKeywords?.get(HB_SIZE_KEY),
                        auctionId = currentAuctionId,
                    ),
                )
            } else {
                eventLogger?.noBid(
                    adUnitId = adUnitId,
                    sizes = prebidRequest.getAdSizes().audienzzSizesJson,
                    adType = AdType.BANNER,
                    adSubtype = AdSubtype.MULTIFORMAT,
                    apiType = ApiType.ORIGINAL,
                    autorefreshTime = autorefreshTime,
                    isAutorefresh = isAutorefresh,
                    isRefresh = isRefresh,
                    resultCode = noBidResultCode(bidInfo.resultCode),
                    mediaTypes = mediaTypesJson(AdSubtype.MULTIFORMAT),
                    auctionId = currentAuctionId,
                )
            }
        }
    }
}
