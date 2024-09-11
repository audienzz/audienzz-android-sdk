package org.audienzz.mobile

import org.audienzz.mobile.configuration.AudienzzNativeAdUnitConfiguration
import org.prebid.mobile.NativeParameters

/**
 * For details of the configuration of native parameters, please check this documentation:
 * https://www.iab.com/wp-content/uploads/2018/03/OpenRTB-Native-Ads-Specification-Final-1.2.pdf
 */
class AudienzzNativeParameters internal constructor(
    internal val prebidNativeParameters: NativeParameters,
) {

    val nativeConfiguration: AudienzzNativeAdUnitConfiguration? =
        prebidNativeParameters.nativeConfiguration?.let { AudienzzNativeAdUnitConfiguration(it) }

    constructor(assets: List<AudienzzNativeAsset>) : this(
        NativeParameters(assets.map { it.prebidNativeAsset }),
    )

    fun addEventTracker(tracker: AudienzzNativeEventTracker) {
        prebidNativeParameters.addEventTracker(tracker.prebidNativeEventTracker)
    }

    fun setContextType(type: AudienzzNativeAdUnit.ContextType) {
        prebidNativeParameters.setContextType(type.prebidContextType)
    }

    fun setContextSubType(type: AudienzzNativeAdUnit.ContextSubtype) {
        prebidNativeParameters.setContextSubType(type.prebidContextSubtype)
    }

    fun setPlacementType(placementType: AudienzzNativeAdUnit.PlacementType) {
        prebidNativeParameters.setPlacementType(placementType.prebidPlacementType)
    }

    fun setPlacementCount(placementCount: Int) {
        prebidNativeParameters.setPlacementCount(placementCount)
    }

    fun setSeq(seq: Int) {
        prebidNativeParameters.setSeq(seq)
    }

    fun setAUrlSupport(support: Boolean) {
        prebidNativeParameters.setAUrlSupport(support)
    }

    fun setDUrlSupport(support: Boolean) {
        prebidNativeParameters.setDUrlSupport(support)
    }

    fun setPrivacy(privacy: Boolean) {
        prebidNativeParameters.setPrivacy(privacy)
    }

    fun setExt(jsonObject: Any) {
        prebidNativeParameters.setExt(jsonObject)
    }
}
