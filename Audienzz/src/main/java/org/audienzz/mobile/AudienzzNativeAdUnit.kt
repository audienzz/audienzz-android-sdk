package org.audienzz.mobile

import org.prebid.mobile.NativeAdUnit

/**
 * For details of the configuration of native imps, please check this documentation:
 * https://www.iab.com/wp-content/uploads/2018/03/OpenRTB-Native-Ads-Specification-Final-1.2.pdf
 */
class AudienzzNativeAdUnit internal constructor(
    internal val prebidNativeAdUnit: NativeAdUnit,
) : AudienzzAdUnit(prebidNativeAdUnit) {

    constructor(configId: String) : this(NativeAdUnit(configId))

    fun setContextType(type: ContextType) {
        prebidNativeAdUnit.setContextType(type.prebidContextType)
    }

    fun setContextSubType(type: ContextSubtype) {
        prebidNativeAdUnit.setContextSubType(type.prebidContextSubtype)
    }

    fun setPlacementType(type: PlacementType) {
        prebidNativeAdUnit.setPlacementType(type.prebidPlacementType)
    }

    fun setPlacementCount(placementCount: Int) {
        prebidNativeAdUnit.setPlacementCount(placementCount)
    }

    fun setSeq(seq: Int) {
        prebidNativeAdUnit.setSeq(seq)
    }

    fun setAUrlSupport(support: Boolean) {
        prebidNativeAdUnit.setAUrlSupport(support)
    }

    fun setDUrlSupport(support: Boolean) {
        prebidNativeAdUnit.setDUrlSupport(support)
    }

    fun setPrivacy(privacy: Boolean) {
        prebidNativeAdUnit.setPrivacy(privacy)
    }

    fun setExt(jsonObject: Any) {
        prebidNativeAdUnit.setExt(jsonObject)
    }

    fun addEventTracker(tracker: AudienzzNativeEventTracker) {
        prebidNativeAdUnit.addEventTracker(tracker.prebidNativeEventTracker)
    }

    fun addAsset(asset: AudienzzNativeAsset) {
        prebidNativeAdUnit.addAsset(asset.prebidNativeAsset)
    }

    enum class ContextType(internal val prebidContextType: NativeAdUnit.CONTEXT_TYPE) {
        CONTENT_CENTRIC(NativeAdUnit.CONTEXT_TYPE.CONTENT_CENTRIC),
        SOCIAL_CENTRIC(NativeAdUnit.CONTEXT_TYPE.SOCIAL_CENTRIC),
        PRODUCT(NativeAdUnit.CONTEXT_TYPE.PRODUCT),
        CUSTOM(NativeAdUnit.CONTEXT_TYPE.CUSTOM), ;

        var id: Int
            get() = prebidContextType.id
            set(value) {
                prebidContextType.id = value
            }

        companion object {

            @JvmStatic
            fun fromPrebidContextType(contextType: NativeAdUnit.CONTEXT_TYPE) =
                values().find { it.prebidContextType == contextType } ?: CONTENT_CENTRIC
        }
    }

    enum class ContextSubtype(internal val prebidContextSubtype: NativeAdUnit.CONTEXTSUBTYPE) {
        GENERAL(NativeAdUnit.CONTEXTSUBTYPE.GENERAL),
        ARTICAL(NativeAdUnit.CONTEXTSUBTYPE.ARTICAL),
        VIDEO(NativeAdUnit.CONTEXTSUBTYPE.VIDEO),
        AUDIO(NativeAdUnit.CONTEXTSUBTYPE.AUDIO),
        IMAGE(NativeAdUnit.CONTEXTSUBTYPE.IMAGE),
        USER_GENERATED(NativeAdUnit.CONTEXTSUBTYPE.USER_GENERATED),
        GENERAL_SOCIAL(NativeAdUnit.CONTEXTSUBTYPE.GENERAL_SOCIAL),
        EMAIL(NativeAdUnit.CONTEXTSUBTYPE.EMAIL),
        CHAT_IM(NativeAdUnit.CONTEXTSUBTYPE.CHAT_IM),
        SELLING(NativeAdUnit.CONTEXTSUBTYPE.SELLING),
        APPLICATION_STORE(NativeAdUnit.CONTEXTSUBTYPE.APPLICATION_STORE),
        PRODUCT_REVIEW_SITES(NativeAdUnit.CONTEXTSUBTYPE.PRODUCT_REVIEW_SITES),
        CUSTOM(NativeAdUnit.CONTEXTSUBTYPE.CUSTOM), ;

        var id: Int
            get() = prebidContextSubtype.id
            set(value) {
                prebidContextSubtype.id = value
            }

        companion object {

            @JvmStatic
            fun fromPrebidContextSubtype(contextSubtype: NativeAdUnit.CONTEXTSUBTYPE) =
                values().find { it.prebidContextSubtype == contextSubtype } ?: GENERAL
        }
    }

    enum class PlacementType(internal val prebidPlacementType: NativeAdUnit.PLACEMENTTYPE) {
        CONTENT_FEED(NativeAdUnit.PLACEMENTTYPE.CONTENT_FEED),
        CONTENT_ATOMIC_UNIT(NativeAdUnit.PLACEMENTTYPE.CONTENT_ATOMIC_UNIT),
        OUTSIDE_CORE_CONTENT(NativeAdUnit.PLACEMENTTYPE.OUTSIDE_CORE_CONTENT),
        RECOMMENDATION_WIDGET(NativeAdUnit.PLACEMENTTYPE.RECOMMENDATION_WIDGET),
        CUSTOM(NativeAdUnit.PLACEMENTTYPE.CUSTOM), ;

        var id: Int
            get() = prebidPlacementType.id
            set(value) {
                prebidPlacementType.id = value
            }

        companion object {

            @JvmStatic
            fun fromPrebidPlacementType(placementType: NativeAdUnit.PLACEMENTTYPE) =
                values().find { it.prebidPlacementType == placementType } ?: CONTENT_FEED
        }
    }
}
