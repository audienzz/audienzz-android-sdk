package org.audienzz.mobile.api.mediation

import android.content.Context
import org.audienzz.mobile.AudienzzAdSize
import org.audienzz.mobile.api.data.AudienzzBannerAdPosition
import org.audienzz.mobile.api.data.AudienzzFetchDemandResult
import org.audienzz.mobile.rendering.bidding.display.AudienzzPrebidMediationDelegate
import org.prebid.mobile.api.mediation.MediationBannerAdUnit

class AudienzzMediationBannerAdUnit internal constructor(
    internal val prebidMediationBannerAdUnit: MediationBannerAdUnit,
) : AudienzzMediationBaseAdUnit(prebidMediationBannerAdUnit) {

    var adPosition: AudienzzBannerAdPosition?
        get() = prebidMediationBannerAdUnit.adPosition?.let {
            AudienzzBannerAdPosition.fromPrebidBannerAdPosition(it)
        }
        set(value) {
            prebidMediationBannerAdUnit.adPosition = value?.prebidBannerAdPosition
        }

    constructor(
        context: Context,
        configId: String,
        size: AudienzzAdSize,
        mediationDelegate: AudienzzPrebidMediationDelegate,
    ) : this(
        MediationBannerAdUnit(
            context,
            configId,
            size.adSize,
            getPrebidMediationDelegate(mediationDelegate),
        ),
    )

    override fun fetchDemand(listener: (AudienzzFetchDemandResult?) -> Unit) {
        prebidMediationBannerAdUnit.fetchDemand { result ->
            listener.invoke(
                result?.let { AudienzzFetchDemandResult.fromPrebidFetchDemandResult(it) },
            )
        }
    }

    @Suppress("SpreadOperator")
    fun addAdditionalSizes(vararg sizes: AudienzzAdSize) {
        prebidMediationBannerAdUnit.addAdditionalSizes(*sizes.map { it.adSize }.toTypedArray())
    }

    fun setRefreshInterval(seconds: Int) {
        prebidMediationBannerAdUnit.setRefreshInterval(seconds)
    }

    fun stopRefresh() {
        prebidMediationBannerAdUnit.stopRefresh()
    }

    fun onAdFailed() {
        prebidMediationBannerAdUnit.onAdFailed()
    }
}
