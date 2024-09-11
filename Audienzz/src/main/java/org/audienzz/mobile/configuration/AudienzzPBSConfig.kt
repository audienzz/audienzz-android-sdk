package org.audienzz.mobile.configuration

import org.prebid.mobile.configuration.PBSConfig

data class AudienzzPBSConfig internal constructor(internal val prebidPBSConfig: PBSConfig) {

    var bannerTimeout: Int
        get() = prebidPBSConfig.bannerTimeout
        set(value) {
            prebidPBSConfig.bannerTimeout = value
        }

    var preRenderTimeout: Int
        get() = prebidPBSConfig.preRenderTimeout
        set(value) {
            prebidPBSConfig.preRenderTimeout = value
        }

    constructor(bannerTimeout: Int, preRenderTimeout: Int) : this(
        PBSConfig(bannerTimeout, preRenderTimeout),
    )
}
