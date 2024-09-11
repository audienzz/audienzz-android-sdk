package org.audienzz.mobile

import org.prebid.mobile.ResultCode

enum class AudienzzResultCode(private val prebidResultCode: ResultCode) {

    /**
     * The attaching keywords was successful, which means
     * there was demand and the demand was set on the ad object.
     */
    SUCCESS(ResultCode.SUCCESS),

    /**
     * The ad request failed due to empty account id
     */
    INVALID_ACCOUNT_ID(ResultCode.INVALID_ACCOUNT_ID),

    /**
     * The ad request failed due to empty config id on the ad unit
     */
    INVALID_CONFIG_ID(ResultCode.INVALID_CONFIG_ID),

    /**
     * The ad request failed because a CUSTOM host used without providing host url
     */
    INVALID_HOST_URL(ResultCode.INVALID_HOST_URL),

    /**
     * For banner view, we don't support multi-size request
     */
    INVALID_SIZE(ResultCode.INVALID_SIZE),

    /**
     * Unable to obtain the Application Context, check if you have set it through
     * PrebidMobile.setApplicationContext()
     */
    INVALID_CONTEXT(ResultCode.INVALID_CONTEXT),

    /**
     * Currently, we only support Banner, Interstitial, DFP Banner, Interstitial
     */
    INVALID_AD_OBJECT(ResultCode.INVALID_AD_OBJECT),

    /**
     * The ad request failed due to a network error.
     */
    NETWORK_ERROR(ResultCode.NETWORK_ERROR),

    /**
     * The ad request took longer than set time out
     */
    TIMEOUT(ResultCode.TIMEOUT),

    /**
     * No bids available from demand source
     */
    NO_BIDS(ResultCode.NO_BIDS),

    /**
     * Prebid Server responded with some error messages
     */
    SERVER_ERROR(ResultCode.PREBID_SERVER_ERROR),

    /**
     * Missing assets requirement for native ad unit
     */
    INVALID_NATIVE_REQUEST(ResultCode.INVALID_NATIVE_REQUEST),

    /**
     * Check @[org.prebid.mobile.api.original.PrebidRequest] object that you put into fetchDemand().
     */
    INVALID_REQUEST_OBJECT(ResultCode.INVALID_PREBID_REQUEST_OBJECT), ;

    companion object {

        internal fun getResultCode(prebidCode: ResultCode?): AudienzzResultCode? =
            values().firstOrNull { it.prebidResultCode == prebidCode }
    }
}
