package org.audienzz.mobile

import org.prebid.mobile.Host

enum class AudienzzHost(internal val prebidHost: Host) {

    /**
     * URL <a href=https://ib.adnxs.com/openrtb2/prebid>https://ib.adnxs.com/openrtb2/prebid</a>
     */
    APPNEXUS(Host.APPNEXUS),

    RUBICON(Host.RUBICON),

    CUSTOM(Host.CUSTOM), ;

    var hostUrl: String
        get() = prebidHost.hostUrl
        set(value) {
            prebidHost.hostUrl = value
        }

    companion object {

        @JvmStatic
        internal fun fromPrebidHost(host: Host) =
            values().find { it.prebidHost == host } ?: APPNEXUS

        @JvmStatic
        fun createCustomHost(url: String) = fromPrebidHost(Host.createCustomHost(url))
    }
}
