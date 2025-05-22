package org.audienzz.mobile.configuration

import androidx.annotation.FloatRange
import org.audienzz.mobile.AudienzzAdSize
import org.audienzz.mobile.AudienzzBannerParameters
import org.audienzz.mobile.AudienzzVideoParameters
import org.audienzz.mobile.api.data.AudienzzAdFormat
import org.audienzz.mobile.api.data.AudienzzAdUnitFormat
import org.audienzz.mobile.api.data.AudienzzPosition
import org.audienzz.mobile.rendering.bidding.data.bid.AudienzzBidResponse
import org.audienzz.mobile.rendering.interstitial.AudienzzInterstitialSizes
import org.audienzz.mobile.rendering.models.AudienzzAdPosition
import org.audienzz.mobile.rendering.models.AudienzzPlacementType
import org.prebid.mobile.configuration.AdUnitConfiguration
import java.util.EnumSet

@Suppress("TooManyFunctions")
class AudienzzAdUnitConfiguration internal constructor(
    internal val prebidAdUnitConfiguration: AdUnitConfiguration,
) {

    var configId: String?
        get() = prebidAdUnitConfiguration.configId
        set(value) {
            prebidAdUnitConfiguration.configId = value
        }

    var pbAdSlot: String?
        get() = prebidAdUnitConfiguration.pbAdSlot
        set(value) {
            prebidAdUnitConfiguration.pbAdSlot = value
        }

    var minSizePercentage: AudienzzAdSize?
        get() = prebidAdUnitConfiguration.minSizePercentage?.let { AudienzzAdSize(it) }
        set(value) {
            prebidAdUnitConfiguration.minSizePercentage = value?.adSize
        }

    var bannerParameters: AudienzzBannerParameters?
        get() = prebidAdUnitConfiguration.bannerParameters?.let { AudienzzBannerParameters(it) }
        set(value) {
            prebidAdUnitConfiguration.bannerParameters = value?.prebidBannerParameters
        }

    var videoParameters: AudienzzVideoParameters?
        get() = prebidAdUnitConfiguration.videoParameters?.let { AudienzzVideoParameters(it) }
        set(value) {
            prebidAdUnitConfiguration.videoParameters = value?.prebidVideoParameters
        }

    var isBuildInVideo: Boolean
        get() = prebidAdUnitConfiguration.isBuiltInVideo
        set(value) {
            prebidAdUnitConfiguration.isBuiltInVideo = value
        }

    var isMuted: Boolean
        get() = prebidAdUnitConfiguration.isMuted
        set(value) {
            prebidAdUnitConfiguration.setIsMuted(value)
        }

    var isSoundButtonVisible: Boolean
        get() = prebidAdUnitConfiguration.isSoundButtonVisible
        set(value) {
            prebidAdUnitConfiguration.setIsSoundButtonVisible(value)
        }

    var autoRefreshDelay: Int
        get() = prebidAdUnitConfiguration.autoRefreshDelay
        set(value) {
            prebidAdUnitConfiguration.autoRefreshDelay = value
        }

    var videoSkipOffset: Int
        get() = prebidAdUnitConfiguration.videoSkipOffset
        set(value) {
            prebidAdUnitConfiguration.videoSkipOffset = value
        }

    var skipDelay: Int
        get() = prebidAdUnitConfiguration.skipDelay
        set(value) {
            prebidAdUnitConfiguration.skipDelay = value
        }

    var skipButtonArea: Double
        get() = prebidAdUnitConfiguration.skipButtonArea
        set(value) {
            prebidAdUnitConfiguration.skipButtonArea = value
        }

    var skipButtonPosition: AudienzzPosition
        get() = AudienzzPosition.fromPrebidPosition(prebidAdUnitConfiguration.skipButtonPosition)
        set(value) {
            prebidAdUnitConfiguration.setSkipButtonPosition(value.prebidPosition)
        }

    var isRewarded: Boolean
        get() = prebidAdUnitConfiguration.isRewarded
        set(value) {
            prebidAdUnitConfiguration.isRewarded = value
        }

    var closeButtonArea: Double
        get() = prebidAdUnitConfiguration.closeButtonArea
        set(@FloatRange(from = 0.0, to = 1.0) value) {
            prebidAdUnitConfiguration.closeButtonArea = value
        }

    var closeButtonPosition: AudienzzPosition
        get() = AudienzzPosition.fromPrebidPosition(prebidAdUnitConfiguration.closeButtonPosition)
        set(value) {
            prebidAdUnitConfiguration.setCloseButtonPosition(value.prebidPosition)
        }

    var videoInitialVolume: Float
        get() = prebidAdUnitConfiguration.videoInitialVolume
        set(value) {
            prebidAdUnitConfiguration.videoInitialVolume = value
        }

    var placementType: AudienzzPlacementType?
        get() = AudienzzPlacementType.fromPrebidPlacementTypeValue(
            prebidAdUnitConfiguration.placementTypeValue,
        )
        set(value) {
            prebidAdUnitConfiguration.setPlacementType(value?.prebidPlacementType)
        }

    val isPlacementTypeValid: Boolean = prebidAdUnitConfiguration.isPlacementTypeValid

    var adPosition: AudienzzAdPosition?
        get() =
            AudienzzAdPosition.fromPrebidAdPositionValue(prebidAdUnitConfiguration.adPositionValue)
        set(value) {
            prebidAdUnitConfiguration.setAdPosition(value?.prebidAdPosition)
        }

    val isAdPositionValid: Boolean = prebidAdUnitConfiguration.isAdPositionValid

    var nativeConfiguration: AudienzzNativeAdUnitConfiguration?
        get() = prebidAdUnitConfiguration.nativeConfiguration?.let {
            AudienzzNativeAdUnitConfiguration(it)
        }
        set(value) {
            prebidAdUnitConfiguration.nativeConfiguration = value?.prebidNativeAdUnitConfiguration
        }

    val broadcastId: Int = prebidAdUnitConfiguration.broadcastId

    var isOriginalAdUnit: Boolean
        get() = prebidAdUnitConfiguration.isOriginalAdUnit
        set(value) {
            prebidAdUnitConfiguration.setIsOriginalAdUnit(value)
        }

    val impressionUrl: String? = prebidAdUnitConfiguration.impressionUrl

    val fingerprint: String = prebidAdUnitConfiguration.fingerprint

    var gpid: String? = prebidAdUnitConfiguration.gpid

    fun modifyUsingBidResponse(bidResponse: AudienzzBidResponse?) {
        prebidAdUnitConfiguration.modifyUsingBidResponse(bidResponse?.prebidBidResponse)
    }

    fun addAdFormat(adFormat: AudienzzAdFormat?) {
        prebidAdUnitConfiguration.addAdFormat(adFormat?.prebidAdFormat)
    }

    /**
     * Clears ad formats list and adds only one ad format.
     */
    fun setAdFormat(adFormat: AudienzzAdFormat?) {
        prebidAdUnitConfiguration.setAdFormat(adFormat?.prebidAdFormat)
    }

    /**
     * Clears previous ad formats and adds AdFormats corresponding to AdUnitFormat types.
     */
    fun setAdUnitFormats(adUnitFormats: EnumSet<AudienzzAdUnitFormat>?) {
        prebidAdUnitConfiguration.setAdUnitFormats(
            EnumSet.copyOf(adUnitFormats?.map { it.prebidAdUnitFormat }),
        )
    }

    fun getAdFormats(): EnumSet<AudienzzAdFormat> = EnumSet.copyOf(
        prebidAdUnitConfiguration.adFormats.map {
            AudienzzAdFormat.fromPrebidAdFormat(it)
        },
    )

    fun isAdType(type: AudienzzAdFormat): Boolean =
        prebidAdUnitConfiguration.isAdType(type.prebidAdFormat)

    fun setInterstitialSize(size: AudienzzInterstitialSizes.AudienzzInterstitialSize?) {
        prebidAdUnitConfiguration.setInterstitialSize(size?.prebidInterstitialSize)
    }

    fun setInterstitialSize(size: String?) {
        prebidAdUnitConfiguration.interstitialSize = size
    }

    fun setInterstitialSize(width: Int, height: Int) {
        prebidAdUnitConfiguration.setInterstitialSize(width, height)
    }

    fun getIntersitialSize(): String? = prebidAdUnitConfiguration.interstitialSize

    fun getMaxVideoDuration(): Int? = prebidAdUnitConfiguration.maxVideoDuration

    fun setMaxVideoDuration(seconds: Int) {
        prebidAdUnitConfiguration.setMaxVideoDuration(seconds)
    }
}
