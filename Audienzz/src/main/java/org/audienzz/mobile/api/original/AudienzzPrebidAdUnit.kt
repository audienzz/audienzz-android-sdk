package org.audienzz.mobile.api.original

import androidx.annotation.IntRange
import org.audienzz.mobile.api.data.AudienzzBidInfo
import org.prebid.mobile.PrebidMobile.AUTO_REFRESH_DELAY_MAX
import org.prebid.mobile.PrebidMobile.AUTO_REFRESH_DELAY_MIN
import org.prebid.mobile.api.original.PrebidAdUnit

class AudienzzPrebidAdUnit internal constructor(internal val prebidAdUnit: PrebidAdUnit) {

    internal var autoRefreshTime = 0
        private set

    constructor(configId: String) : this(PrebidAdUnit(configId))

    internal fun fetchDemand(
        request: AudienzzPrebidRequest,
        listener: (AudienzzBidInfo) -> Unit,
    ) {
        prebidAdUnit.fetchDemand(
            request.prebidRequest,
        ) { bidInfo -> listener(AudienzzBidInfo(bidInfo)) }
    }

    internal fun fetchDemand(
        o: Any?,
        request: AudienzzPrebidRequest,
        listener: (AudienzzBidInfo) -> Unit,
    ) {
        prebidAdUnit.fetchDemand(
            o,
            request.prebidRequest,
        ) { bidInfo -> listener(AudienzzBidInfo(bidInfo)) }
    }

    fun setAutoRefreshInterval(
        @IntRange(
            from = AUTO_REFRESH_DELAY_MIN / 1000L,
            to = AUTO_REFRESH_DELAY_MAX / 1000L,
        ) seconds: Int,
    ) {
        this.autoRefreshTime = seconds
        prebidAdUnit.setAutoRefreshInterval(seconds)
    }

    fun resumeAutoRefresh() {
        prebidAdUnit.resumeAutoRefresh()
    }

    fun stopAutoRefresh() {
        prebidAdUnit.stopAutoRefresh()
    }

    fun destroy() {
        prebidAdUnit.destroy()
    }
}
