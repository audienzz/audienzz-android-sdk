package org.audienzz.mobile.rendering.bidding.data.bid

import org.json.JSONObject
import org.prebid.mobile.rendering.bidding.data.bid.Cache

class AudienzzCache internal constructor(prebidCache: Cache) {

    val key: String? = prebidCache.key

    val url: String? = prebidCache.url

    val bids: AudienzzBids = AudienzzBids(prebidCache.bids)

    companion object {

        @JvmStatic
        fun fromJSONObject(jsonObject: JSONObject): AudienzzCache =
            AudienzzCache(Cache.fromJSONObject(jsonObject))
    }
}
