package org.audienzz.mobile.original

import com.google.android.gms.ads.admanager.AdManagerAdRequest
import org.audienzz.mobile.AudienzzTargetingParams
import org.audienzz.mobile.api.data.AudienzzBidInfo
import org.audienzz.mobile.api.original.AudienzzPrebidAdUnit
import org.audienzz.mobile.api.original.AudienzzPrebidRequest
import org.audienzz.mobile.event.adCreation
import org.audienzz.mobile.event.bidRequest
import org.audienzz.mobile.event.bidWinner
import org.audienzz.mobile.event.entity.AdSubtype
import org.audienzz.mobile.event.entity.AdType
import org.audienzz.mobile.event.entity.ApiType
import org.audienzz.mobile.event.eventLogger
import org.audienzz.mobile.util.audienzzSizeString

class AudienzzMultiformatAdHandler(
    private val adUnit: AudienzzPrebidAdUnit,
    private val adUnitId: String,
) {

    private var isFirstDemandFetch = true

    init {
        eventLogger?.adCreation(
            adUnitId = adUnitId,
            adType = AdType.BANNER,
            adSubtype = AdSubtype.MULTIFORMAT,
            apiType = ApiType.ORIGINAL,
        )
    }

    @JvmOverloads fun load(
        gamRequestBuilder: AdManagerAdRequest.Builder = AdManagerAdRequest.Builder(),
        prebidRequest: AudienzzPrebidRequest,
        callback: (AudienzzBidInfo) -> Unit,
    ) {
        val isAutorefresh = adUnit.autoRefreshTime > 0
        val autorefreshTime = adUnit.autoRefreshTime.toLong()
        val isRefresh = !isFirstDemandFetch
        isFirstDemandFetch = false

        eventLogger?.bidRequest(
            adUnitId = adUnitId,
            sizes = prebidRequest.getAdSizes().audienzzSizeString,
            adType = AdType.BANNER,
            adSubtype = AdSubtype.MULTIFORMAT,
            apiType = ApiType.ORIGINAL,
            autorefreshTime = autorefreshTime,
            isAutorefresh = isAutorefresh,
            isRefresh = isRefresh,
        )
        val request = AudienzzTargetingParams.CUSTOM_TARGETING_MANAGER.applyToGamRequestBuilder(
            gamRequestBuilder,
        )
            .build()
        adUnit.fetchDemand(request, prebidRequest) { bidInfo ->
            callback.invoke(bidInfo)
            eventLogger?.bidWinner(
                adUnitId = adUnitId,
                sizes = prebidRequest.getAdSizes().audienzzSizeString,
                adType = AdType.BANNER,
                adSubtype = AdSubtype.MULTIFORMAT,
                apiType = ApiType.ORIGINAL,
                autorefreshTime = autorefreshTime,
                isAutorefresh = isAutorefresh,
                isRefresh = isRefresh,
                resultCode = bidInfo.resultCode.toString(),
                targetKeywords = request.keywords.toList(),
            )
        }
    }
}
