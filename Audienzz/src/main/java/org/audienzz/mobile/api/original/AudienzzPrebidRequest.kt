package org.audienzz.mobile.api.original

import org.audienzz.mobile.AudienzzAdSize
import org.audienzz.mobile.AudienzzBannerParameters
import org.audienzz.mobile.AudienzzNativeParameters
import org.audienzz.mobile.AudienzzVideoParameters
import org.prebid.mobile.api.original.PrebidRequest

data class AudienzzPrebidRequest internal constructor(internal val prebidRequest: PrebidRequest) {

    internal var bannerParameters: AudienzzBannerParameters? = null
        private set

    internal var videoParameters: AudienzzVideoParameters? = null
        private set

    internal var nativeParameters: AudienzzNativeParameters? = null
        private set

    constructor() : this(PrebidRequest())

    fun setBannerParameters(parameters: AudienzzBannerParameters?) {
        bannerParameters = parameters
        prebidRequest.setBannerParameters(parameters?.prebidBannerParameters)
    }

    fun setVideoParameters(parameters: AudienzzVideoParameters?) {
        videoParameters = parameters
        prebidRequest.setVideoParameters(parameters?.prebidVideoParameters)
    }

    fun setNativeParameters(parameters: AudienzzNativeParameters?) {
        nativeParameters = parameters
        prebidRequest.setNativeParameters(parameters?.prebidNativeParameters)
    }

    fun setIsInterstitial(isInterstitial: Boolean) {
        prebidRequest.setInterstitial(isInterstitial)
    }

    fun setIsRewarded(isRewarded: Boolean) {
        prebidRequest.setRewarded(isRewarded)
    }

    fun setGpid(gpid: String?) {
        prebidRequest.setGpid(gpid)
    }

    internal fun getAdSizes(): List<AudienzzAdSize> = buildList {
        bannerParameters?.adSizes?.let(::addAll)
        videoParameters?.adSize?.let(::add)
    }
}
