package org.audienzz.mobile

import CustomTargetingManager
import android.util.Pair
import org.audienzz.BuildConfig
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
    internal val CUSTOM_TARGETING_MANAGER = CustomTargetingManager(
        sdkPlatform = "android",
        sdkVersion = BuildConfig.AUDIENZZ_SDK_VERSION,
    )

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

    // M5: these consent/COPPA setters passthrough to Prebid statics that error-log and DROP the
    // value if called before Prebid init — so consent set pre-init could be missing from bid
    // requests. Buffer pre-init writes and replay them in the init listener (see
    // [onPrebidInitialized]); getters prefer the buffered value until init completes.
    @Volatile
    private var prebidInitialized = false

    private class PendingValue<T> {
        var isSet = false
            private set
        var value: T? = null
            private set

        fun set(newValue: T?) {
            value = newValue
            isSet = true
        }
    }

    private val pendingCoppa = PendingValue<Boolean>()
    private val pendingGdpr = PendingValue<Boolean>()
    private val pendingGdprConsent = PendingValue<String>()
    private val pendingPurposeConsents = PendingValue<String>()

    /**
     * M5: called by [AudienzzPrebidMobile] once Prebid finishes initializing. Flushes any
     * consent/COPPA values the publisher set before init so they reach Prebid's statics.
     */
    @JvmStatic
    internal fun onPrebidInitialized() {
        prebidInitialized = true
        if (pendingCoppa.isSet) TargetingParams.setSubjectToCOPPA(pendingCoppa.value)
        if (pendingGdpr.isSet) TargetingParams.setSubjectToGDPR(pendingGdpr.value)
        if (pendingGdprConsent.isSet) TargetingParams.setGDPRConsentString(pendingGdprConsent.value)
        if (pendingPurposeConsents.isSet) TargetingParams.setPurposeConsents(pendingPurposeConsents.value)
    }

    /**
     * Sets subject to COPPA. Null to set undefined. <br><br>
     * <p>
     * Values set before SDK init are buffered and applied once init completes.
     */
    @JvmStatic
    var isSubjectToCOPPA: Boolean?
        get() = if (!prebidInitialized && pendingCoppa.isSet) pendingCoppa.value else TargetingParams.isSubjectToCOPPA()
        set(value) {
            if (prebidInitialized) TargetingParams.setSubjectToCOPPA(value) else pendingCoppa.set(value)
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
        get() = if (!prebidInitialized && pendingGdpr.isSet) pendingGdpr.value else TargetingParams.isSubjectToGDPR()
        set(value) {
            if (prebidInitialized) TargetingParams.setSubjectToGDPR(value) else pendingGdpr.set(value)
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
        get() = if (!prebidInitialized && pendingGdprConsent.isSet) pendingGdprConsent.value else TargetingParams.getGDPRConsentString()
        set(value) {
            if (prebidInitialized) TargetingParams.setGDPRConsentString(value) else pendingGdprConsent.set(value)
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
        get() = if (!prebidInitialized && pendingPurposeConsents.isSet) pendingPurposeConsents.value else TargetingParams.getPurposeConsents()
        set(value) {
            if (prebidInitialized) TargetingParams.setPurposeConsents(value) else pendingPurposeConsents.set(value)
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

    /** The publisher's own global ORTB, kept separately so SDK-managed sources never wipe it. */
    private var publisherOrtbConfig: JSONObject? = null

    /**
     * Sets global OpenRTB JSON string for merging with the original request.
     * Expected format: "{"new_field": "value"}".
     * Params:
     * ortbConfig – JSONObject containing OpenRTB string.
     *
     * H6: the publisher's ORTB is now stored and folded into the single canonical global ORTB
     * (see [rebuildGlobalOrtb]) rather than replacing it — so a later targeting or schain change
     * no longer discards it, and this call no longer discards accumulated keywords/schain.
     */
    @JvmStatic
    fun setGlobalOrtbConfig(ortbConfig: JSONObject) {
        publisherOrtbConfig = ortbConfig
        rebuildGlobalOrtb()
    }

    /**
     * H6: rebuild the one canonical global ORTB from every source the SDK owns — the publisher's
     * own ORTB, the schain, the accumulated custom-targeting keywords, and the SDK identity — and
     * push it to Prebid in a single call. Every targeting/schain mutation funnels through here, so
     * no source can wipe another (previously each mutation replaced Prebid's global ORTB with only
     * its own slice). SDK-managed sources are merged last so their reserved namespaces win.
     */
    @JvmStatic
    internal fun rebuildGlobalOrtb() {
        var config = JSONObject()
        publisherOrtbConfig?.let { config = AudienzzUtil.mergeJsonObjects(config, it) }
        AudienzzPrebidMobile.schainObject?.let { config = AudienzzUtil.mergeJsonObjects(config, it) }
        config = AudienzzUtil.mergeJsonObjects(config, CUSTOM_TARGETING_MANAGER.buildOrtbCustomTargeting())
        config = AudienzzUtil.mergeJsonObjects(config, SDK_META_ORTB)
        TargetingParams.setGlobalOrtbConfig(config.toString())
    }

    /** Extra fields contributed by a bridge SDK (e.g. "rn_v" → "0.4.1"). */
    private val bridgeOrtbFields = mutableMapOf<String, String>()

    /** Always-present ORTB metadata — native SDK identity + any bridge version. */
    private val SDK_META_ORTB: JSONObject
        get() {
            val audienzz = JSONObject().apply {
                put("sdk", "android")
                put("v", BuildConfig.AUDIENZZ_SDK_VERSION)
                bridgeOrtbFields.forEach { (k, v) -> put(k, v) }
            }
            return JSONObject().apply {
                put("app", JSONObject().apply {
                    put("ext", JSONObject().apply {
                        put("audienzz", audienzz)
                    })
                })
            }
        }

    /**
     * Set a bridge-layer SDK identity key (e.g. "au_rn_v", "au_flutter_v").
     *
     * The key is stored as a reserved GAM targeting entry (wins over any
     * publisher-set value and cannot be removed via removeGlobalTargeting /
     * clearGlobalTargeting) and is also embedded in app.ext.audienzz in every
     * Prebid bid request.
     *
     * The ORTB sub-key is derived by stripping the "au_" prefix:
     *   "au_rn_v" → ext.audienzz.rn_v
     */
    @JvmStatic
    fun setBridgeTargeting(key: String, value: String) {
        CUSTOM_TARGETING_MANAGER.setReservedTargeting(key, value)
        val ortbKey = if (key.startsWith("au_")) key.removePrefix("au_") else key
        bridgeOrtbFields[ortbKey] = value
        rebuildGlobalOrtb()
    }

    /** Add a key-value global targeting */
    @JvmStatic
    fun addGlobalTargeting(key: String, value: String) {
        CUSTOM_TARGETING_MANAGER.addCustomTargeting(key, value)
        rebuildGlobalOrtb()
    }

    /** Add a key values global targeting */
    @JvmStatic
    fun addGlobalTargeting(key: String, value: Set<String>) {
        CUSTOM_TARGETING_MANAGER.addCustomTargeting(key, value)
        rebuildGlobalOrtb()
    }

    /** Replace the value set for an existing key */
    @JvmStatic
    fun updateGlobalTargeting(key: String, value: Set<String>) {
        CUSTOM_TARGETING_MANAGER.removeCustomTargeting(key)
        CUSTOM_TARGETING_MANAGER.addCustomTargeting(key, value)
        rebuildGlobalOrtb()
    }

    /** Remove targeting for a key — silently skips SDK-reserved keys. */
    @JvmStatic
    fun removeGlobalTargeting(key: String) {
        // CustomTargetingManager.removeCustomTargeting already skips reserved keys
        CUSTOM_TARGETING_MANAGER.removeCustomTargeting(key)
        rebuildGlobalOrtb()
    }

    /** Clear global targeting */
    @JvmStatic
    fun clearGlobalTargeting() {
        // H6: clear only the custom-targeting keywords, then rebuild the canonical ORTB. Publisher
        // ORTB, schain and SDK identity are preserved because they are separate sources, not
        // surgically carved out of the flattened Prebid config as before.
        CUSTOM_TARGETING_MANAGER.clearCustomTargeting()
        rebuildGlobalOrtb()
    }
}
