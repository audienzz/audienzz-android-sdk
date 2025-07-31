package org.audienzz.mobile

import CustomTargetingManager
import android.util.Pair
import org.audienzz.mobile.rendering.models.openrtb.bidRequests.AudienzzExt
import org.json.JSONObject
import org.prebid.mobile.ExternalUserId
import org.prebid.mobile.ExternalUserId.UniqueId
import org.prebid.mobile.TargetingParams

/**
 * AudienzzTargetingParams class sets the Targeting parameters like yob, gender, location
 * and other custom parameters for the adUnits to be made available in the auction.
 */
@Suppress("TooManyFunctions")
object AudienzzTargetingParams {
    internal val CUSTOM_TARGETING_MANAGER = CustomTargetingManager()

    /**
     * User latitude and longitude
     *
     * @param latitude  User latitude
     * @param longitude User longitude
     */
    @JvmStatic
    var userLatLng: Pair<Float, Float>?
        get() = TargetingParams.getUserLatLng()
        set(value) {
            TargetingParams.setUserLatLng(value?.first, value?.second)
        }

    @JvmStatic
    val userKeywords: String?
        get() = TargetingParams.getUserKeywords()

    @JvmStatic
    val keywordSet: Set<String>
        get() = TargetingParams.getUserKeywordsSet()

    @JvmStatic
    var publisherName: String?
        get() = TargetingParams.getPublisherName()
        set(value) {
            TargetingParams.setPublisherName(value)
        }

    @JvmStatic
    var domain: String
        get() = TargetingParams.getDomain()
        set(value) {
            TargetingParams.setDomain(value)
        }

    @JvmStatic
    var storeUrl: String
        get() = TargetingParams.getStoreUrl()
        set(value) {
            TargetingParams.setStoreUrl(value)
        }

    @JvmStatic
    val accessControlList: Set<String>
        get() = TargetingParams.getAccessControlList()

    @JvmStatic
    var omidPartnerName: String?
        get() = TargetingParams.getOmidPartnerName()
        set(value) {
            TargetingParams.setOmidPartnerName(value)
        }

    @JvmStatic
    var omidPartnerVersion: String?
        get() = TargetingParams.getOmidPartnerVersion()
        set(value) {
            TargetingParams.setOmidPartnerVersion(value)
        }

    /**
     * Sets subject to COPPA. Null to set undefined. <br><br>
     * <p>
     * Must be called only after
     * {@link PrebidMobile#initializeSdk(Context, SdkInitializationListener)}.
     */
    @JvmStatic
    var isSubjectToCOPPA: Boolean?
        get() = TargetingParams.isSubjectToCOPPA()
        set(value) {
            TargetingParams.setSubjectToCOPPA(value)
        }

    /**
     * Subject to GDPR for Prebid. It uses custom static field, not IAB. <br><br>
     * 1) Prebid subject to GDPR custom value, if present. <br>
     * 2) IAB subject to GDPR TCF 2.0. <br>
     * Otherwise, null. <br><br>
     * <p>
     * Must be called only after
     * {@link AudienzzPrebidMobile#initializeSdk(Context, AudienzzSdkInitializationListener)}.
     */
    @JvmStatic
    var isSubjectToGDPR: Boolean?
        get() = TargetingParams.isSubjectToGDPR()
        set(value) {
            TargetingParams.setSubjectToGDPR(value)
        }

    /**
     * GDPR consent for Prebid. It uses custom static field, not IAB. <br><br>
     * 1) Prebid GDPR consent custom value, if present. <br>
     * 2) IAB GDPR consent TCF 2.0. <br>
     * Otherwise, null. <br><br>
     * <p>
     * Must be called only after
     * {@link AudienzzPrebidMobile#initializeSdk(Context, AudienzzSdkInitializationListener)}.
     */
    @JvmStatic
    var gdprConsentString: String?
        get() = TargetingParams.getGDPRConsentString()
        set(value) {
            TargetingParams.setGDPRConsentString(value)
        }

    /**
     * Sets Prebid custom GDPR purpose consents (device access consent). <br><br>
     * 1) Prebid GDPR purpose consent custom value, if present. <br>
     * 2) IAB GDPR TCF 2.0 purpose consent. <br>
     * null if purpose consent isn't set or index is out of bounds. <br><br>
     * <p>
     * Must be called only after
     * {@link AudienzzPrebidMobile#initializeSdk(Context, AudienzzSdkInitializationListener)}.
     */
    @JvmStatic
    var purposeConsents: String?
        get() = TargetingParams.getPurposeConsents()
        set(value) {
            TargetingParams.setPurposeConsents(value)
        }

    /**
     * Platform-specific identifier for targeting purpose. Should be bundle/package name
     */
    @JvmStatic
    var bundleName: String?
        get() = TargetingParams.getBundleName()
        set(value) {
            TargetingParams.setBundleName(value)
        }

    @JvmStatic
    val extDataDictionary: Map<String, Set<String>>
        get() = TargetingParams.getExtDataDictionary()

    /**
     * Gets the device access consent set by the publisher.<br><br>
     * If custom Prebid subject and purpose consent set, gets device access from them.
     * Otherwise, from IAB standard.
     * <p>
     * Must be called only after
     * {@link AudienzzPrebidMobile#initializeSdk(Context, AudienzzSdkInitializationListener)}.
     */
    @JvmStatic
    val isDeviceAccessConsent: Boolean?
        get() = TargetingParams.getDeviceAccessConsent()

    /**
     * Placeholder for exchange-specific extensions to OpenRTB
     */
    @JvmStatic
    var userExt: AudienzzExt?
        get() = TargetingParams.getUserExt()?.let { AudienzzExt(it) }
        set(value) {
            TargetingParams.setUserExt(value?.prebidExt)
        }

    /**
     * This method obtains the user keyword for global user targeting
     * Inserts the given element in the set if it is not already present.
     */
    @JvmStatic
    fun addUserKeyword(keyword: String) {
        TargetingParams.addUserKeyword(keyword)
    }

    /**
     * This method obtains the user keyword set for global user targeting
     * Adds the elements of the given set to the set.
     */
    @JvmStatic
    fun addUserKeywords(keywords: Set<String>) {
        TargetingParams.addUserKeywords(keywords)
    }

    /**
     * This method allows to remove specific user keyword from global user targeting
     */
    @JvmStatic
    fun removeUserKeyword(keyword: String) {
        TargetingParams.removeUserKeyword(keyword)
    }

    /**
     * This method allows to remove all keywords from the set of global user targeting
     */
    @JvmStatic
    fun clearUserKeywords() {
        TargetingParams.clearUserKeywords()
    }

    /**
     * Use this API for setting the externalUserId in the SharedPreference.
     * Prebid server provide them participating server-side bid adapters.
     *
     * @param externalUserIds the externalUserIds objects to be stored in the SharedPreference
     */
    @JvmStatic
    fun setExternalUserIds(externalUserIds: List<AudienzzExternalUserId>?) {
        TargetingParams.setExternalUserIds(
            externalUserIds?.map {
                ExternalUserId(
                    it.source,
                    it.uniqueIds.map { uniqueId ->
                        UniqueId(uniqueId.id, uniqueId.atype).apply {
                            setExt(uniqueId.ext)
                        }
                    },
                )
            },
        )
    }

    /**
     * Returns stored ExternalUserIds.
     * Note: ext parameter is not returned
     */
    @JvmStatic
    fun getExternalUserIds(): List<AudienzzExternalUserId>? =
        TargetingParams.getExternalUserIds()?.map {
            AudienzzExternalUserId(
                it,
                it.uniqueIds.map { uniqueId ->
                    AudienzzExternalUserId.AudienzzUniqueId(
                        uniqueId.id,
                        uniqueId.atype,
                    )
                },
            )
        }

    /**
     * This method obtains the context data keyword & value context for global context targeting
     * if the key already exists the value will be appended to the list. No duplicates will be added
     * (app.ext.data)
     */
    @JvmStatic
    fun addExtData(key: String, value: String) {
        TargetingParams.addExtData(key, value)
    }

    /**
     * This method obtains the context data keyword & values set for global context targeting.
     * the values if the key already exist will be replaced with the new set of values
     */
    @JvmStatic
    fun updateExtData(key: String, value: Set<String>) {
        TargetingParams.updateExtData(key, value)
    }

    /**
     * This method allows to remove specific context data keyword & values set from
     * global context targeting
     */
    @JvmStatic
    fun removeExtData(key: String) {
        TargetingParams.removeExtData(key)
    }

    /**
     * This method allows to remove all context data set from global context targeting
     */
    @JvmStatic
    fun clearExtData() {
        TargetingParams.clearExtData()
    }

    /**
     * This method obtains a bidder name allowed to receive global targeting
     * (ext.prebid.data)
     */
    @JvmStatic
    fun addBidderToAccessControlList(bidderName: String) {
        TargetingParams.addBidderToAccessControlList(bidderName)
    }

    /**
     * This method allows to remove specific bidder name
     */
    @JvmStatic
    fun removeBidderFromAccessControlList(bidderName: String) {
        TargetingParams.removeBidderFromAccessControlList(bidderName)
    }

    /**
     * This method allows to remove all the bidder name set
     */
    @JvmStatic
    fun clearAccessControlList() {
        TargetingParams.clearAccessControlList()
    }

    /**
     * Gets any given purpose consent for set index in that order. <br>
     * 1) Prebid GDPR purpose consent custom value, if present. <br>
     * 2) IAB GDPR TCF 2.0 purpose consent. <br>
     * Returns null if purpose consent isn't set or index is out of bounds. <br><br>
     * <p>
     * Must be called only after
     * {@link AudienzzPrebidMobile#initializeSdk(Context, AudienzzSdkInitializationListener)}.
     */
    @JvmStatic
    fun getPurposeConsent(index: Int): Boolean? = TargetingParams.getPurposeConsent(index)

    @JvmStatic
    fun getGlobalOrtbConfig(): String? = TargetingParams.getGlobalOrtbConfig()

    /**
     * Sets global OpenRTB JSON string for merging with the original request.
     * Expected format: "{"new_field": "value"}".
     * Params:
     * ortbConfig – JSON OpenRTB string.
     */
    @JvmStatic
    fun setGlobalOrtbConfig(ortbConfig: JSONObject) =
        TargetingParams.setGlobalOrtbConfig(
            AudienzzUtil.mergeJsonObjects(
                AudienzzPrebidMobile.AUDIENZZ_SCHAIN_OBJECT_CONFIG,
                ortbConfig,
            ).toString(),
        )

    /** Add a key-value global targeting */
    @JvmStatic
    fun addGlobalTargeting(key: String, value: String) {
        CUSTOM_TARGETING_MANAGER.addCustomTargeting(key, value)
        setGlobalOrtbConfig(CUSTOM_TARGETING_MANAGER.buildOrtbCustomTargeting())
    }

    /** Add a key values global targeting */
    @JvmStatic
    fun addGlobalTargeting(key: String, value: Set<String>) {
        CUSTOM_TARGETING_MANAGER.addCustomTargeting(key, value)
        setGlobalOrtbConfig(CUSTOM_TARGETING_MANAGER.buildOrtbCustomTargeting())
    }

    /** Remove targeting for a key */
    @JvmStatic
    fun removeGlobalTargeting(key: String) {
        CUSTOM_TARGETING_MANAGER.removeCustomTargeting(key)
        setGlobalOrtbConfig(CUSTOM_TARGETING_MANAGER.buildOrtbCustomTargeting())
    }

    /** Clear global targeting */
    @JvmStatic
    fun clearGlobalTargeting() {
        CUSTOM_TARGETING_MANAGER.clearCustomTargeting()
        val currentConfig = getGlobalOrtbConfig()
        if (currentConfig != null) {
            val configJson = JSONObject(currentConfig)

            val appObj = configJson.optJSONObject("app") ?: return
            val contentObj = appObj.optJSONObject("content") ?: return

            contentObj.remove("keywords")

            if (contentObj.length() == 0) {
                appObj.remove("content")
            }

            if (appObj.length() == 0) {
                configJson.remove("app")
            }

            TargetingParams.setGlobalOrtbConfig(configJson.toString())
        }
    }
}
