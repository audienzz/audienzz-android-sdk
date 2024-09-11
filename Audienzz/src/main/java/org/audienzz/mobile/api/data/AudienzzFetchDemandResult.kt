package org.audienzz.mobile.api.data

import org.prebid.mobile.api.data.FetchDemandResult

enum class AudienzzFetchDemandResult(internal val prebidFetchDemandResult: FetchDemandResult) {

    /**
     * The attaching keywords was successful, which means
     * there was demand and the demand was set on the ad object.
     */
    SUCCESS(FetchDemandResult.SUCCESS),

    /**
     * The ad request failed due to empty account id
     */
    INVALID_ACCOUNT_ID(FetchDemandResult.INVALID_ACCOUNT_ID),

    /**
     * The ad request failed due to empty config id on the ad unit
     */
    INVALID_CONFIG_ID(FetchDemandResult.INVALID_CONFIG_ID),

    /**
     * Size is invalid or missing
     */
    INVALID_SIZE(FetchDemandResult.INVALID_SIZE),

    /**
     * Invalid context passed
     */
    INVALID_CONTEXT(FetchDemandResult.INVALID_CONTEXT),

    /**
     * GAM views supported only
     */
    INVALID_AD_OBJECT(FetchDemandResult.INVALID_AD_OBJECT),

    /**
     * The ad request failed because a CUSTOM host used without providing host url
     */
    INVALID_HOST_URL(FetchDemandResult.INVALID_HOST_URL),

    /**
     * The ad request failed due to a network error.
     */
    NETWORK_ERROR(FetchDemandResult.NETWORK_ERROR),

    /**
     * The ad request took longer than set time out
     */
    TIMEOUT(FetchDemandResult.TIMEOUT),

    /**
     * No bids available from demand source
     */
    NO_BIDS(FetchDemandResult.NO_BIDS),

    /**
     * Server responded with some error messages
     */
    SERVER_ERROR(FetchDemandResult.SERVER_ERROR), ;

    companion object {

        @JvmStatic
        val NO_BIDS_MESSAGE = FetchDemandResult.NO_BIDS_MESSAGE

        @JvmStatic
        fun parseErrorMessage(msg: String): AudienzzFetchDemandResult =
            fromPrebidFetchDemandResult(FetchDemandResult.parseErrorMessage(msg))

        @JvmStatic
        internal fun fromPrebidFetchDemandResult(fetchDemandResult: FetchDemandResult) =
            values().find { it.prebidFetchDemandResult == fetchDemandResult } ?: SUCCESS
    }
}
