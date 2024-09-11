package org.audienzz.mobile.api.mediation

import android.content.Context
import androidx.annotation.IntRange
import org.audienzz.mobile.AudienzzAdSize
import org.audienzz.mobile.api.data.AudienzzAdUnitFormat
import org.audienzz.mobile.api.data.AudienzzFetchDemandResult
import org.audienzz.mobile.rendering.bidding.display.AudienzzPrebidMediationDelegate
import org.prebid.mobile.api.mediation.MediationInterstitialAdUnit
import java.util.EnumSet

class AudienzzMediationInterstitialAdUnit internal constructor(
    internal val prebidMediationInterstitialAdUnit: MediationInterstitialAdUnit,
) : AudienzzMediationBaseFullScreenAdUnit(prebidMediationInterstitialAdUnit) {

    /**
     * Constructor to fetch demand for a display interstitial ad with specified minHeightPercentage
     * and minWidthPercentage
     */
    constructor(
        context: Context,
        configId: String,
        minSizePercentage: AudienzzAdSize,
        mediationDelegate: AudienzzPrebidMediationDelegate,
    ) : this(
        MediationInterstitialAdUnit(
            context,
            configId,
            minSizePercentage.adSize,
            getPrebidMediationDelegate(mediationDelegate),
        ),
    )

    /**
     * Constructor to fetch demand for either display or video interstitial ads
     */
    constructor(
        context: Context,
        configId: String,
        adUnitFormats: EnumSet<AudienzzAdUnitFormat>,
        mediationDelegate: AudienzzPrebidMediationDelegate,
    ) : this(
        MediationInterstitialAdUnit(
            context,
            configId,
            EnumSet.copyOf(adUnitFormats.map { it.prebidAdUnitFormat }),
            getPrebidMediationDelegate(mediationDelegate),
        ),
    )

    override fun fetchDemand(listener: (AudienzzFetchDemandResult?) -> Unit) {
        prebidMediationInterstitialAdUnit.fetchDemand { result ->
            listener.invoke(
                result?.let { AudienzzFetchDemandResult.fromPrebidFetchDemandResult(it) },
            )
        }
    }

    /**
     * Sets min width and height in percentage. Range from 0 to 100.
     */
    fun setMinSizePercentage(
        @IntRange(from = 0, to = 100) width: Int,
        @IntRange(from = 0, to = 100) height: Int,
    ) {
        prebidMediationInterstitialAdUnit.setMinSizePercentage(width, height)
    }
}
