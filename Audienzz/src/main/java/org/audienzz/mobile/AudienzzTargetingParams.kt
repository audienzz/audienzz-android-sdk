package org.audienzz.mobile

import android.util.Pair
import org.audienzz.mobile.rendering.models.openrtb.bidRequests.AudienzzExt
import org.prebid.mobile.TargetingParams

/**
 * AudienzzTargetingParams class sets the Targeting parameters like yob, gender, location
 * and other custom parameters for the adUnits to be made available in the auction.
 */
@Suppress("TooManyFunctions")
object AudienzzTargetingParams {

    @JvmStatic val BIDDER_NAME_APP_NEXUS = TargetingParams.BIDDER_NAME_APP_NEXUS

    @JvmStatic val BIDDER_NAME_RUBICON_PROJECT = TargetingParams.BIDDER_NAME_RUBICON_PROJECT

    @JvmStatic
    var userAge: Int?
        get() = TargetingParams.getUserAge()
        set(value) {
            TargetingParams.setUserAge(value)
        }

    @JvmStatic
    var yearOfBirth: Int
        get() = TargetingParams.getYearOfBirth()
        set(value) {
            TargetingParams.setYearOfBirth(value)
        }

    enum class GENDER(internal val prebidTargetingParams: TargetingParams.GENDER) {
        FEMALE(TargetingParams.GENDER.FEMALE),
        MALE(TargetingParams.GENDER.MALE),
        UNKNOWN(TargetingParams.GENDER.UNKNOWN), ;

        val key: String = prebidTargetingParams.key

        companion object {

            @JvmStatic
            internal fun fromPrebidGender(gender: TargetingParams.GENDER) =
                values().find { it.prebidTargetingParams == gender } ?: UNKNOWN

            @JvmStatic fun genderByKey(key: String) =
                fromPrebidGender(TargetingParams.GENDER.genderByKey(key))
        }
    }

    /**
     * The current user's gender, if it's available. The default value is UNKNOWN.
     * This should be set if the user's gender is known, as it can help make buying the ad
     * space more attractive to advertisers.
     */
    @JvmStatic
    var gender: GENDER
        get() = GENDER.fromPrebidGender(TargetingParams.getGender())
        set(value) {
            TargetingParams.setGender(value.prebidTargetingParams)
        }

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
    val userDataDictionary: Map<String, Set<String>>
        get() = TargetingParams.getUserDataDictionary()

    @JvmStatic
    val userKeywords: String?
        get() = TargetingParams.getUserKeywords()

    @JvmStatic
    val keywordSet: Set<String>
        get() = TargetingParams.getUserKeywordsSet()

    /**
     * Optional feature to pass bidder data that was set in the
     * exchange’s cookie. The string must be in base85 cookie safe
     * characters and be in any format. Proper JSON encoding must
     * be used to include “escaped” quotation marks.
     *
     * @param data Custom data to be passed
     */
    @JvmStatic
    var userCustomData: String?
        get() = TargetingParams.getUserCustomData()
        set(value) {
            TargetingParams.setUserCustomData(value)
        }

    @JvmStatic
    var userId: String?
        get() = TargetingParams.getUserId()
        set(value) {
            TargetingParams.setUserId(value)
        }

    /**
     * Buyer-specific ID for the user as mapped by the exchange for
     * the buyer. At least one of buyeruid or id is recommended.
     */
    @JvmStatic
    var buyerId: String?
        get() = TargetingParams.getBuyerId()
        set(value) {
            TargetingParams.setBuyerId(value)
        }

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
    val extKeywordsSet: Set<String>
        get() = TargetingParams.getExtKeywordsSet()

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
     * This method obtains the user data keyword & value for global user targeting
     * if the key already exists the value will be appended to the list. No duplicates will be added
     */
    @JvmStatic
    fun addUserData(key: String, value: String) {
        TargetingParams.addUserData(key, value)
    }

    /**
     * This method obtains the user data keyword & values set for global user targeting
     * the values if the key already exist will be replaced with the new set of values
     */
    @JvmStatic
    fun updateUserData(key: String, value: Set<String>) {
        TargetingParams.updateUserData(key, value)
    }

    /**
     * This method allows to remove specific user data keyword & value set from global user
     * targeting
     */
    @JvmStatic
    fun removeUserData(key: String) {
        TargetingParams.removeUserData(key)
    }

    /**
     * This method allows to remove all user data set from global user targeting
     */
    @JvmStatic fun clearUserData() {
        TargetingParams.clearUserData()
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
     * Use this API for storing the externalUserId in the SharedPreference.
     * Prebid server provide them participating server-side bid adapters.
     *
     * @param externalUserId the externalUserId instance to be stored in the SharedPreference
     */
    @JvmStatic
    fun storeExternalUserId(externalUserId: AudienzzExternalUserId) {
        TargetingParams.storeExternalUserId(externalUserId.prebidExternalUserId)
    }

    /**
     * Returns the stored (in the SharedPreference) ExternalUserId instance for a given source
     */
    @JvmStatic
    fun fetchStoredExternalUserId(source: String): AudienzzExternalUserId? =
        TargetingParams.fetchStoredExternalUserId(source)?.let { AudienzzExternalUserId(it) }

    /**
     * Returns the stored (in the SharedPreferences) External User Id list
     */
    @JvmStatic
    fun fetchStoredExternalUserIds(): List<AudienzzExternalUserId> =
        TargetingParams.fetchStoredExternalUserIds().map { AudienzzExternalUserId(it) }

    /**
     * Removes the stored (in the SharedPreference) ExternalUserId instance for a given source
     */
    @JvmStatic
    fun removeStoredExternalUserId(source: String) {
        TargetingParams.removeStoredExternalUserId(source)
    }

    /**
     * Clear the Stored ExternalUserId list from the SharedPreference
     */
    @JvmStatic
    fun clearStoredExternalUserIds() {
        TargetingParams.clearStoredExternalUserIds()
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
     * This method obtains the context keyword for adunit context targeting
     * Inserts the given element in the set if it is not already present.
     * (imp[].ext.context.keywords)
     */
    @JvmStatic
    fun addExtKeyword(keyword: String) {
        TargetingParams.addExtKeyword(keyword)
    }

    /**
     * This method obtains the context keyword set for adunit context targeting
     * Adds the elements of the given set to the set.
     */
    @JvmStatic
    fun addExtKeywords(keywords: Set<String>) {
        TargetingParams.addExtKeywords(keywords)
    }

    /**
     * This method allows to remove specific context keyword from adunit context targeting
     */
    @JvmStatic
    fun removeExtKeyword(keyword: String) {
        TargetingParams.removeExtKeyword(keyword)
    }

    /**
     * This method allows to remove all keywords from the set of adunit context targeting
     */
    @JvmStatic
    fun clearExtKeywords() {
        TargetingParams.clearExtKeywords()
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
}
