package org.audienzz.mobile.api.mediation

import org.audienzz.mobile.AudienzzContentObject
import org.audienzz.mobile.AudienzzDataObject
import org.audienzz.mobile.AudienzzNativeAdUnit
import org.audienzz.mobile.AudienzzNativeAsset
import org.audienzz.mobile.AudienzzNativeEventTracker
import org.audienzz.mobile.api.data.AudienzzFetchDemandResult
import org.prebid.mobile.api.mediation.MediationNativeAdUnit

@Suppress("TooManyFunctions")
class AudienzzMediationNativeAdUnit internal constructor(
    internal val prebidMediationNativeAdUnit: MediationNativeAdUnit,
) {

    var appContent: AudienzzContentObject?
        get() = prebidMediationNativeAdUnit.appContent?.let { AudienzzContentObject(it) }
        set(value) {
            prebidMediationNativeAdUnit.appContent = value?.prebidContentObject
        }

    val userData: List<AudienzzDataObject> =
        prebidMediationNativeAdUnit.userData.map { AudienzzDataObject(it) }

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

    fun addUserData(dataObject: AudienzzDataObject) {
        prebidMediationNativeAdUnit.addUserData(dataObject.prebidDataObject)
    }

    fun clearUserData() {
        prebidMediationNativeAdUnit.clearUserData()
    }

    fun addExtData(key: String, value: String) {
        prebidMediationNativeAdUnit.addExtData(key, value)
    }

    fun updateExtData(key: String, value: Set<String>) {
        prebidMediationNativeAdUnit.updateExtData(key, value)
    }

    fun removeExtData(key: String) {
        prebidMediationNativeAdUnit.removeExtData(key)
    }

    fun clearExtData() {
        prebidMediationNativeAdUnit.clearExtData()
    }

    fun addExtKeyword(keyword: String) {
        prebidMediationNativeAdUnit.addExtKeyword(keyword)
    }

    fun addExtKeywords(keywords: Set<String>) {
        prebidMediationNativeAdUnit.addExtKeywords(keywords)
    }

    fun removeExtKeyword(keyword: String) {
        prebidMediationNativeAdUnit.removeExtKeyword(keyword)
    }

    fun clearExtKeywords() {
        prebidMediationNativeAdUnit.clearExtKeywords()
    }
}
