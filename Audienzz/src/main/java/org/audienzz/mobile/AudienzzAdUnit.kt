package org.audienzz.mobile

import androidx.annotation.IntRange
import org.audienzz.mobile.api.data.AudienzzBidInfo
import org.prebid.mobile.AdUnit
import org.prebid.mobile.OnCompleteListener
import org.prebid.mobile.PrebidMobile.AUTO_REFRESH_DELAY_MAX
import org.prebid.mobile.PrebidMobile.AUTO_REFRESH_DELAY_MIN
import org.prebid.mobile.api.original.OnFetchDemandResult

abstract class AudienzzAdUnit internal constructor(
    private val adUnit: AdUnit,
) {
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
