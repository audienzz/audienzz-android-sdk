package org.audienzz.mobile.api.mediation

import org.audienzz.mobile.AudienzzNativeAdUnit
import org.audienzz.mobile.AudienzzNativeAsset
import org.audienzz.mobile.AudienzzNativeEventTracker
import org.audienzz.mobile.api.data.AudienzzFetchDemandResult
import org.prebid.mobile.api.mediation.MediationNativeAdUnit

@Suppress("TooManyFunctions")
class AudienzzMediationNativeAdUnit internal constructor(
    internal val prebidMediationNativeAdUnit: MediationNativeAdUnit,
) {

    constructor(configId: String, adObject: Any) : this(MediationNativeAdUnit(configId, adObject))

    fun fetchDemand(listener: (AudienzzFetchDemandResult?) -> Unit) {
        prebidMediationNativeAdUnit.fetchDemand { result ->
            listener.invoke(
                result?.let { AudienzzFetchDemandResult.fromPrebidFetchDemandResult(it) },
            )
        }
    }

    fun destroy() {
        prebidMediationNativeAdUnit.destroy()
    }

    fun addAsset(asset: AudienzzNativeAsset) {
        prebidMediationNativeAdUnit.addAsset(asset.prebidNativeAsset)
    }

    fun addEventTracker(tracker: AudienzzNativeEventTracker) {
        prebidMediationNativeAdUnit.addEventTracker(tracker.prebidNativeEventTracker)
    }

    fun setContextType(type: AudienzzNativeAdUnit.ContextType) {
        prebidMediationNativeAdUnit.setContextType(type.prebidContextType)
    }

    fun setContextSubType(type: AudienzzNativeAdUnit.ContextSubtype) {
        prebidMediationNativeAdUnit.setContextSubType(type.prebidContextSubtype)
    }

    fun setExt(jsonObject: Any) {
        prebidMediationNativeAdUnit.setExt(jsonObject)
    }

    fun setSeq(seq: Int) {
        prebidMediationNativeAdUnit.setSeq(seq)
    }

    fun enablePrivacy(privacy: Boolean) {
        prebidMediationNativeAdUnit.setPrivacy(privacy)
    }

    fun setPlacementType(type: AudienzzNativeAdUnit.PlacementType) {
        prebidMediationNativeAdUnit.setPlacementType(type.prebidPlacementType)
    }

    fun setPlacementCount(implementCount: Int) {
        prebidMediationNativeAdUnit.setPlacementCount(implementCount)
    }

    fun enableAUrlSupport(support: Boolean) {
        prebidMediationNativeAdUnit.setAUrlSupport(support)
    }

    fun enableDUrlSupport(support: Boolean) {
        prebidMediationNativeAdUnit.setDUrlSupport(support)
    }
}
