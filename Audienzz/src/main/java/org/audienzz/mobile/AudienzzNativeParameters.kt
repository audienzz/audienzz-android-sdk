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

    companion object {

        private const val TITLE_LENGTH = 90
        private const val MAIN_IMAGE_SIZE = 200
        private const val ICON_SIZE = 20

        /**
         * The native request a banner slot sends when its ad config enables native (see
         * [AudienzzBannerAdUnit.enableNativeFormat]). The winning ad is drawn by an HTML template
         * in GAM, so this list is a contract with that template: it may rely on title, main image and
         * sponsored-by, and must cope with icon, description and call-to-action being absent.
         * Only those three are required, so a bid lacking an optional asset still competes.
         *
         * Returns a new instance each call; adjust it before passing it on if a placement needs to.
         */
        @JvmStatic
        fun inBannerDefaults(): AudienzzNativeParameters {
            val title = AudienzzNativeTitleAsset().apply {
                len = TITLE_LENGTH
                isRequired = true
            }
            val mainImage = AudienzzNativeImageAsset(
                MAIN_IMAGE_SIZE,
                MAIN_IMAGE_SIZE,
                MAIN_IMAGE_SIZE,
                MAIN_IMAGE_SIZE,
            ).apply {
                imageType = AudienzzNativeImageAsset.ImageType.MAIN
                isRequired = true
            }
            val icon = AudienzzNativeImageAsset(ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE).apply {
                imageType = AudienzzNativeImageAsset.ImageType.ICON
                isRequired = false
            }
            val sponsored = dataAsset(AudienzzNativeDataAsset.DataType.SPONSORED, required = true)
            val description = dataAsset(AudienzzNativeDataAsset.DataType.DESC, required = false)
            val callToAction = dataAsset(AudienzzNativeDataAsset.DataType.CTATEXT, required = false)

            return AudienzzNativeParameters(
                listOf(title, mainImage, icon, sponsored, description, callToAction),
            ).apply {
                setContextType(AudienzzNativeAdUnit.ContextType.CONTENT_CENTRIC)
                setContextSubType(AudienzzNativeAdUnit.ContextSubtype.GENERAL)
                setPlacementType(AudienzzNativeAdUnit.PlacementType.CONTENT_ATOMIC_UNIT)
                addEventTracker(
                    AudienzzNativeEventTracker(
                        AudienzzNativeEventTracker.EventType.IMPRESSION,
                        listOf(AudienzzNativeEventTracker.EventTrackingMethod.IMAGE),
                    ),
                )
            }
        }

        private fun dataAsset(type: AudienzzNativeDataAsset.DataType, required: Boolean) =
            AudienzzNativeDataAsset().apply {
                dataType = type
                if (type != AudienzzNativeDataAsset.DataType.DESC) len = TITLE_LENGTH
                isRequired = required
            }
    }
}
