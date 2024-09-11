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

    /**
     * Content for adunit, content, in which impression will appear
     */
    var appContent: AudienzzContentObject?
        get() = AudienzzContentObject(adUnit.appContent)
        set(value) {
            adUnit.appContent = value?.prebidContentObject
        }

    val userData: List<AudienzzDataObject> = adUnit.userData.map(::AudienzzDataObject)

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

    internal val autoRefreshTime get() = adUnit.configuration.autoRefreshDelay

    internal val adFormats get() = adUnit.configuration.adFormats

    internal val keywords get() = adUnit.configuration.extKeywordsSet

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

    /**
     * This method obtains the context data keyword & value for adunit context targeting
     * if the key already exists the value will be appended to the list. No duplicates will be added
     */
    fun addExtData(key: String, value: String) {
        adUnit.addExtData(key, value)
    }

    /**
     * This method obtains the context data keyword & values for adunit context targeting
     * the values if the key already exist will be replaced with the new set of values
     */
    fun updateExtData(key: String, value: Set<String>) {
        adUnit.updateExtData(key, value)
    }

    /**
     * This method allows to remove specific context data keyword & values set from adunit
     * context targeting
     */
    fun removeExtData(key: String) {
        adUnit.removeExtData(key)
    }

    /**
     * This method allows to remove all context data set from adunit context targeting
     */
    fun clearExtData() {
        adUnit.clearExtData()
    }

    /**
     * This method obtains the context keyword for adunit context targeting
     * Inserts the given element in the set if it is not already present.
     */
    fun addExtKeyword(keyword: String) {
        adUnit.addExtKeyword(keyword)
    }

    /**
     * This method obtains the context keyword set for adunit context targeting
     * Adds the elements of the given set to the set.
     */
    fun addExtKeywords(keywords: Set<String>) {
        adUnit.addExtKeywords(keywords)
    }

    /**
     * This method allows to remove specific context keyword from adunit context targeting
     */
    fun removeExtKeyword(keyword: String) {
        adUnit.removeExtKeyword(keyword)
    }

    /**
     * This method allows to remove all keywords from the set of adunit context targeting
     */
    fun clearExtKeywords() {
        adUnit.clearExtKeywords()
    }

    fun addUserData(dataObject: AudienzzDataObject) {
        adUnit.addUserData(dataObject.prebidDataObject)
    }

    fun clearUserData() {
        adUnit.clearUserData()
    }
}
