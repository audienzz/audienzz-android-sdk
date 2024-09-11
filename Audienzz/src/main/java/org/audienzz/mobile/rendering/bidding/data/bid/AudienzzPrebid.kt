package org.audienzz.mobile.rendering.bidding.data.bid

import org.audienzz.mobile.AudienzzAdSize
import org.audienzz.mobile.configuration.AudienzzAdUnitConfiguration
import org.json.JSONObject
import org.prebid.mobile.rendering.bidding.data.bid.Prebid

data class AudienzzPrebid internal constructor(
    internal val prebid: Prebid,
) {

    val cache: AudienzzCache = AudienzzCache(prebid.cache)

    val targeting: HashMap<String, String> = prebid.targeting

    val meta: HashMap<String, String> = prebid.meta

    val type: String? = prebid.type

    val winEventUrl: String? = prebid.winEventUrl

    val impEventUrl: String? = prebid.impEventUrl

    companion object {

        @JvmStatic
        fun fromJsonObject(jsonObject: JSONObject): AudienzzPrebid =
            AudienzzPrebid(Prebid.fromJSONObject(jsonObject))

        @JvmStatic
        fun getJsonObjectForImp(adUnitConfiguration: AudienzzAdUnitConfiguration): JSONObject =
            Prebid.getJsonObjectForImp(adUnitConfiguration.prebidAdUnitConfiguration)

        @JvmStatic
        fun getJsonObjectForApp(sdkName: String, sdkVersion: String): JSONObject =
            Prebid.getJsonObjectForApp(sdkName, sdkVersion)

        @JvmStatic
        fun getJsonObjectForBidRequest(
            accountId: String,
            isVideo: Boolean,
            config: AudienzzAdUnitConfiguration,
        ): JSONObject = Prebid.getJsonObjectForBidRequest(
            accountId,
            isVideo,
            config.prebidAdUnitConfiguration,
        )

        @JvmStatic
        fun getJsonObjectForDeviceMinSizePerc(minSizePercentage: AudienzzAdSize): JSONObject =
            Prebid.getJsonObjectForDeviceMinSizePerc(minSizePercentage.adSize)
    }
}
