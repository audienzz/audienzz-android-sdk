package org.audienzz.mobile

interface AudienzzPrebidNativeAdListener {

    /**
     * A successful Prebid Native ad is returned
     *
     * @param ad use this instance for displaying
     */
    fun onPrebidNativeLoaded(ad: AudienzzPrebidNativeAd?)

    /**
     * Prebid Native was not found in the server returned response,
     * Please display the ad as regular ways
     */
    fun onPrebidNativeNotFound()

    /**
     * Prebid Native ad was returned, however, the bid is not valid for displaying
     * Should be treated as on ad load failed
     */
    fun onPrebidNativeNotValid()
}
