package org.audienzz.mobile.rendering.bidding.data.bid

import org.json.JSONObject
import org.prebid.mobile.rendering.bidding.data.bid.Bids

data class AudienzzBids internal constructor(private val bids: Bids) {

    val url: String? = bids.url
    val cacheId: String? = bids.cacheId

    companion object {

        @JvmStatic
        fun fromJSONObject(jsonObject: JSONObject): AudienzzBids =
            AudienzzBids(Bids.fromJSONObject(jsonObject))
    }
}
