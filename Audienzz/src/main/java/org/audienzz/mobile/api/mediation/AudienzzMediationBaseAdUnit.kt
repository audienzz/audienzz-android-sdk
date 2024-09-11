package org.audienzz.mobile.api.mediation

import org.audienzz.mobile.AudienzzContentObject
import org.audienzz.mobile.AudienzzDataObject
import org.audienzz.mobile.api.data.AudienzzFetchDemandResult
import org.audienzz.mobile.rendering.bidding.data.bid.AudienzzBidResponse
import org.audienzz.mobile.rendering.bidding.display.AudienzzPrebidMediationDelegate
import org.prebid.mobile.api.mediation.MediationBaseAdUnit
import org.prebid.mobile.rendering.bidding.data.bid.BidResponse
import org.prebid.mobile.rendering.bidding.display.PrebidMediationDelegate
import java.util.HashMap

abstract class AudienzzMediationBaseAdUnit internal constructor(
    internal val prebidMediationBaseAdUnit: MediationBaseAdUnit,
) {

    val extDataDictionary: Map<String, Set<String>>
        get() = prebidMediationBaseAdUnit.extDataDictionary

    val extKeywordSet: Set<String>
        get() = prebidMediationBaseAdUnit.extKeywordsSet

    var pbAdSlot: String?
        get() = prebidMediationBaseAdUnit.pbAdSlot
        set(value) {
            prebidMediationBaseAdUnit.pbAdSlot = value
        }

    var appContent: AudienzzContentObject?
        get() = prebidMediationBaseAdUnit.appContent?.let { AudienzzContentObject(it) }
        set(value) {
            prebidMediationBaseAdUnit.appContent = value?.prebidContentObject
        }

    val userData: List<AudienzzDataObject>
        get() = prebidMediationBaseAdUnit.userData.map { AudienzzDataObject(it) }

    protected abstract fun fetchDemand(listener: (AudienzzFetchDemandResult?) -> Unit)

    fun addExtData(key: String, value: String) {
        prebidMediationBaseAdUnit.addExtData(key, value)
    }

    fun updateExtData(key: String, value: Set<String>) {
        prebidMediationBaseAdUnit.updateExtData(key, value)
    }

    fun removeExtData(key: String) {
        prebidMediationBaseAdUnit.removeExtData(key)
    }

    fun clearExtData() {
        prebidMediationBaseAdUnit.clearExtData()
    }

    fun addExtKeyword(keyword: String) {
        prebidMediationBaseAdUnit.addExtKeyword(keyword)
    }

    fun addExtKeywords(keywords: Set<String>) {
        prebidMediationBaseAdUnit.addExtKeywords(keywords)
    }

    fun removeExtKeyword(keyword: String) {
        prebidMediationBaseAdUnit.removeExtKeyword(keyword)
    }

    fun clearExtKeywords() {
        prebidMediationBaseAdUnit.clearExtKeywords()
    }

    fun addUserData(dataObject: AudienzzDataObject) {
        prebidMediationBaseAdUnit.addUserData(dataObject.prebidDataObject)
    }

    fun clearUserData() {
        prebidMediationBaseAdUnit.clearUserData()
    }

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
                mediationDelegate.setResponsetoLocalExtras(
                    response?.let { AudienzzBidResponse(it) },
                )
            }

            override fun canPerformRefresh(): Boolean = mediationDelegate.canPerformRefresh()
        }
    }
}
