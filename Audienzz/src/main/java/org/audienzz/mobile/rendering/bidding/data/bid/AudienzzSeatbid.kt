package org.audienzz.mobile.rendering.bidding.data.bid

import org.audienzz.mobile.rendering.models.openrtb.bidRequests.AudienzzExt
import org.json.JSONObject
import org.prebid.mobile.rendering.bidding.data.bid.Seatbid

data class AudienzzSeatbid internal constructor(
    internal val prebidSeatbid: Seatbid,
) {

    val bids: List<AudienzzBid> = prebidSeatbid.bids.map { AudienzzBid(it) }

    val seat: String? = prebidSeatbid.seat

    val group: Int = prebidSeatbid.group

    val ext: AudienzzExt = AudienzzExt(prebidSeatbid.ext)

    companion object {

        @JvmStatic
        fun fromJSONObject(jsonObject: JSONObject): AudienzzSeatbid =
            AudienzzSeatbid(Seatbid.fromJSONObject(jsonObject))
    }
}
