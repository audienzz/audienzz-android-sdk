package org.audienzz.mobile

import androidx.annotation.IntRange
import org.audienzz.mobile.api.data.AudienzzBidInfo
import org.prebid.mobile.AdUnit
import org.prebid.mobile.AudienzzBidResponseAccessor
import org.prebid.mobile.OnCompleteListener
import org.prebid.mobile.PrebidMobile.AUTO_REFRESH_DELAY_MAX
import org.prebid.mobile.PrebidMobile.AUTO_REFRESH_DELAY_MIN
import org.prebid.mobile.api.original.OnFetchDemandResult

/**
 * Economics of the Prebid auction winner, read from the retained `BidResponse` after a successful
 * `fetchDemand`. All nullable — populated only when there was a winning bid.
 */
internal data class AudienzzWinningBid(
    val cpm: Double?,
    val currency: String?,
    val creativeId: String?,
    val auctionId: String?,
    val adId: String?,
)

abstract class AudienzzAdUnit internal constructor(
    private val adUnit: AdUnit,
) {

    /**
     * Returns the Prebid winning-bid economics (cpm, currency, creative id, auction id, ad id) from
     * the `BidResponse` Prebid retains on the original-API `AdUnit`, or null if there is none.
     * Call after a `fetchDemand` SUCCESS.
     */
    internal fun getWinningBid(): AudienzzWinningBid? {
        val response = AudienzzBidResponseAccessor.getBidResponse(adUnit) ?: return null
        // No actual winning bid (e.g. an empty/error response Prebid still reports as SUCCESS).
        val bid = response.winningBid ?: return null
        return AudienzzWinningBid(
            cpm = bid.price,
            currency = response.cur,
            creativeId = bid.crid,
            auctionId = response.id,
            adId = bid.id,
        )
    }
    var pbAdSlot: String?
        get() = adUnit.pbAdSlot
        set(value) {
            adUnit.pbAdSlot = value
        }

    var gpid: String?
        get() = adUnit.gpid
        set(value) {
            adUnit.gpid = value
        }

    var impOrtbConfig: String?
        get() = adUnit.impOrtbConfig
        set(value) {
            adUnit.impOrtbConfig = value
        }

    internal val autoRefreshTime get() = adUnit.configuration.autoRefreshDelay

    internal val adFormats get() = adUnit.configuration.adFormats

    fun setAutoRefreshInterval(
        @IntRange(
            from = AUTO_REFRESH_DELAY_MIN / 1000L,
            to = AUTO_REFRESH_DELAY_MAX / 1000L,
        ) seconds: Int,
    ) {
        adUnit.setAutoRefreshInterval(seconds)
    }

    fun resumeAutoRefresh() {
        adUnit.resumeAutoRefresh()
    }

    fun stopAutoRefresh() {
        adUnit.stopAutoRefresh()
    }

    fun destroy() {
        adUnit.destroy()
    }

    fun fetchDemand(
        adObj: Any,
        listener: (AudienzzResultCode?) -> Unit,
    ) {
        val onCompleteListener = OnCompleteListener { resultCode ->
            listener(AudienzzResultCode.getResultCode(resultCode))
        }
        adUnit.fetchDemand(adObj, onCompleteListener)
    }

    fun fetchDemand(listener: (AudienzzBidInfo) -> Unit) {
        val onFetchDemandResult =
            OnFetchDemandResult { bidInfo -> listener(AudienzzBidInfo(bidInfo)) }
        adUnit.fetchDemand(onFetchDemandResult)
    }

}
