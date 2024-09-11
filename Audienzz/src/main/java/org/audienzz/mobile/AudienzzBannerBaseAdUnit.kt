package org.audienzz.mobile

import org.prebid.mobile.BannerBaseAdUnit

abstract class AudienzzBannerBaseAdUnit(
    private val adUnit: BannerBaseAdUnit,
) : AudienzzAdUnit(adUnit) {

    var bannerParameters: AudienzzBannerParameters?
        get() = adUnit.bannerParameters?.let { AudienzzBannerParameters(it) }
        set(value) {
            adUnit.bannerParameters = value?.prebidBannerParameters
        }

    var videoParameters: AudienzzVideoParameters?
        get() = adUnit.videoParameters?.let { AudienzzVideoParameters(it) }
        set(value) {
            adUnit.videoParameters = value?.prebidVideoParameters
        }
}
