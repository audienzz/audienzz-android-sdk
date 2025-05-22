package org.audienzz.mobile

enum class AudienzzHost(var hostUrl: String) {

    /**
     * URL <a href=https://ib.adnxs.com/openrtb2/prebid>https://ib.adnxs.com/openrtb2/prebid</a>
     */
    APPNEXUS("https://ib.adnxs.com/openrtb2/prebid"),

    RUBICON("https://prebid-server.rubiconproject.com/openrtb2/auction"),

    CUSTOM(""),
}
