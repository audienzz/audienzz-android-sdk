package org.audienzz.mobile.api.mediation

import android.content.Context
import org.audienzz.mobile.api.data.AudienzzFetchDemandResult
import org.audienzz.mobile.rendering.bidding.display.AudienzzPrebidMediationDelegate
import org.prebid.mobile.api.mediation.MediationRewardedVideoAdUnit

class AudienzzMediationRewardedVideoAdUnit internal constructor(
    internal val prebidMediationRewardedVideoAdUnit: MediationRewardedVideoAdUnit,
) : AudienzzMediationBaseFullScreenAdUnit(prebidMediationRewardedVideoAdUnit) {

    constructor(
        context: Context,
        configId: String,
        mediationDelegate: AudienzzPrebidMediationDelegate,
    ) : this(
        MediationRewardedVideoAdUnit(
            context,
            configId,
            getPrebidMediationDelegate(mediationDelegate),
        ),
    )

    override fun fetchDemand(listener: (AudienzzFetchDemandResult?) -> Unit) {
        prebidMediationRewardedVideoAdUnit.fetchDemand { result ->
            listener.invoke(
                result?.let { AudienzzFetchDemandResult.fromPrebidFetchDemandResult(it) },
            )
        }
    }
}
