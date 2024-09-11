package org.audienzz.mobile.configuration

import org.audienzz.mobile.AudienzzNativeAdUnit
import org.audienzz.mobile.AudienzzNativeAsset
import org.audienzz.mobile.AudienzzNativeEventTracker
import org.json.JSONObject
import org.prebid.mobile.configuration.NativeAdUnitConfiguration

class AudienzzNativeAdUnitConfiguration internal constructor(
    internal val prebidNativeAdUnitConfiguration: NativeAdUnitConfiguration,
) {

    var contextType: AudienzzNativeAdUnit.ContextType?
        get() = prebidNativeAdUnitConfiguration.contextType?.let {
            AudienzzNativeAdUnit.ContextType.fromPrebidContextType(it)
        }
        set(value) {
            prebidNativeAdUnitConfiguration.contextType = value?.prebidContextType
        }

    var contextSubtype: AudienzzNativeAdUnit.ContextSubtype?
        get() = prebidNativeAdUnitConfiguration.contextSubtype?.let {
            AudienzzNativeAdUnit.ContextSubtype.fromPrebidContextSubtype(it)
        }
        set(value) {
            prebidNativeAdUnitConfiguration.contextSubtype = value?.prebidContextSubtype
        }

    var placementType: AudienzzNativeAdUnit.PlacementType?
        get() = prebidNativeAdUnitConfiguration.placementType?.let {
            AudienzzNativeAdUnit.PlacementType.fromPrebidPlacementType(it)
        }
        set(value) {
            prebidNativeAdUnitConfiguration.placementType = value?.prebidPlacementType
        }

    var placementCount: Int
        get() = prebidNativeAdUnitConfiguration.placementCount
        set(value) {
            prebidNativeAdUnitConfiguration.placementCount = value
        }

    var seq: Int
        get() = prebidNativeAdUnitConfiguration.seq
        set(value) {
            prebidNativeAdUnitConfiguration.seq = value
        }

    var isAUrlSupport: Boolean
        get() = prebidNativeAdUnitConfiguration.aUrlSupport
        set(value) {
            prebidNativeAdUnitConfiguration.aUrlSupport = value
        }

    var isDUrlSupport: Boolean
        get() = prebidNativeAdUnitConfiguration.dUrlSupport
        set(value) {
            prebidNativeAdUnitConfiguration.dUrlSupport = value
        }

    var isPrivacy: Boolean
        get() = prebidNativeAdUnitConfiguration.privacy
        set(value) {
            prebidNativeAdUnitConfiguration.privacy = value
        }

    var ext: JSONObject?
        get() = prebidNativeAdUnitConfiguration.ext
        set(value) {
            prebidNativeAdUnitConfiguration.ext = value
        }

    constructor() : this(NativeAdUnitConfiguration())

    fun addEventTracker(tracker: AudienzzNativeEventTracker) {
        prebidNativeAdUnitConfiguration.addEventTracker(tracker.prebidNativeEventTracker)
    }

    fun getEventTrackers(): List<AudienzzNativeEventTracker> =
        prebidNativeAdUnitConfiguration.eventTrackers.map { AudienzzNativeEventTracker(it) }

    fun clearEventTrackers() {
        prebidNativeAdUnitConfiguration.clearEventTrackers()
    }

    fun addAsset(tracker: AudienzzNativeAsset) {
        prebidNativeAdUnitConfiguration.addAsset(tracker.prebidNativeAsset)
    }

    fun getAssets(): List<AudienzzNativeAsset> =
        prebidNativeAdUnitConfiguration.assets.map { AudienzzNativeAsset.fromPrebidNativeAsset(it) }

    fun clearAssets() {
        prebidNativeAdUnitConfiguration.clearAssets()
    }
}
