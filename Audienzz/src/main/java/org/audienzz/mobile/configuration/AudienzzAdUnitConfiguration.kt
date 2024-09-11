package org.audienzz.mobile.configuration

import androidx.annotation.FloatRange
import org.audienzz.mobile.AudienzzAdSize
import org.audienzz.mobile.AudienzzBannerParameters
import org.audienzz.mobile.AudienzzContentObject
import org.audienzz.mobile.AudienzzDataObject
import org.audienzz.mobile.AudienzzVideoParameters
import org.audienzz.mobile.api.data.AudienzzAdFormat
import org.audienzz.mobile.api.data.AudienzzAdUnitFormat
import org.audienzz.mobile.api.data.AudienzzPosition
import org.audienzz.mobile.rendering.bidding.data.bid.AudienzzBidResponse
import org.audienzz.mobile.rendering.interstitial.AudienzzInterstitialSizes
import org.audienzz.mobile.rendering.models.AudienzzAdPosition
import org.audienzz.mobile.rendering.models.AudienzzPlacementType
import org.prebid.mobile.DataObject
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

    var appContent: AudienzzContentObject?
        get() = prebidAdUnitConfiguration.appContent?.let { AudienzzContentObject(it) }
        set(value) {
            prebidAdUnitConfiguration.appContent = value?.prebidContentObject
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

    fun addUserData(dataObject: AudienzzDataObject) {
        prebidAdUnitConfiguration.addUserData(dataObject.prebidDataObject)
    }

    fun getUserData(): List<AudienzzDataObject> =
        prebidAdUnitConfiguration.userData.map { AudienzzDataObject(it) }

    fun clearUserData() {
        prebidAdUnitConfiguration.clearUserData()
    }

    fun setUserData(userData: List<AudienzzDataObject>) {
        prebidAdUnitConfiguration.setUserData(
            arrayListOf<DataObject>().apply {
                addAll(userData.map { it.prebidDataObject })
            },
        )
    }

    fun addExtData(key: String, value: String) {
        prebidAdUnitConfiguration.addExtData(key, value)
    }

    fun addExtData(key: String, value: Set<String>) {
        prebidAdUnitConfiguration.addExtData(key, value)
    }

    fun removeExtData(key: String) {
        prebidAdUnitConfiguration.removeExtData(key)
    }

    fun getExtDataDictionary(): Map<String, Set<String>> =
        prebidAdUnitConfiguration.extDataDictionary

    fun clearExtData() {
        prebidAdUnitConfiguration.clearExtData()
    }

    fun setExtData(extData: Map<String, Set<String>>?) {
        prebidAdUnitConfiguration.setExtData(extData)
    }

    fun addExtKeyword(keyword: String) {
        prebidAdUnitConfiguration.addExtKeyword(keyword)
    }

    fun addExtKeywords(keywords: Set<String>) {
        prebidAdUnitConfiguration.addExtKeywords(keywords)
    }

    fun removeExtKeyword(key: String) {
        prebidAdUnitConfiguration.removeExtKeyword(key)
    }

    fun setExtKeywords(extKeywords: Set<String>?) {
        prebidAdUnitConfiguration.setExtKeywords(extKeywords)
    }

    fun getExtKeywordsSet(): Set<String> = prebidAdUnitConfiguration.extKeywordsSet

    fun clearExtKeywords() {
        prebidAdUnitConfiguration.clearExtKeywords()
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
