package org.audienzz.mobile

import org.prebid.mobile.VideoBaseAdUnit

abstract class AudienzzVideoBaseAdUnit internal constructor(
    internal val adUnit: VideoBaseAdUnit,
) : AudienzzAdUnit(adUnit) {

    var videoParameters: AudienzzVideoParameters?
        get() = adUnit.videoParameters?.let { AudienzzVideoParameters(it) }
        set(value) {
            adUnit.videoParameters = value?.prebidVideoParameters
        }
}
