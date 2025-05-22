package org.audienzz.mobile.api.mediation

import org.audienzz.mobile.api.data.AudienzzFetchDemandResult
import org.audienzz.mobile.rendering.bidding.data.bid.AudienzzBidResponse
import org.audienzz.mobile.rendering.bidding.display.AudienzzPrebidMediationDelegate
import org.prebid.mobile.api.mediation.MediationBaseAdUnit
import org.prebid.mobile.rendering.bidding.data.bid.BidResponse
import org.prebid.mobile.rendering.bidding.display.PrebidMediationDelegate

abstract class AudienzzMediationBaseAdUnit internal constructor(
    internal val prebidMediationBaseAdUnit: MediationBaseAdUnit,
) {
    var pbAdSlot: String?
        get() = prebidMediationBaseAdUnit.pbAdSlot
        set(value) {
            prebidMediationBaseAdUnit.pbAdSlot = value
        }

    protected abstract fun fetchDemand(listener: (AudienzzFetchDemandResult?) -> Unit)

    fun destroy() {
        prebidMediationBaseAdUnit.destroy()
    }

    companion object {

        internal fun getPrebidMediationDelegate(
            mediationDelegate: AudienzzPrebidMediationDelegate,
        ) = object : PrebidMediationDelegate {
            override fun handleKeywordsUpdate(keywords: HashMap<String, String>?) {
                mediationDelegate.handleKeywordsUpdate(keywords)
            }

            override fun setResponseToLocalExtras(response: BidResponse?) {
                mediationDelegate.setResponseToLocalExtras(
                    response?.let { AudienzzBidResponse(it) },
                )
            }

            override fun canPerformRefresh(): Boolean = mediationDelegate.canPerformRefresh()
        }
    }
}
