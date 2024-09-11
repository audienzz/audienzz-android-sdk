package org.audienzz.mobile.api.data

import org.audienzz.mobile.AudienzzResultCode
import org.prebid.mobile.api.data.BidInfo

data class AudienzzBidInfo internal constructor(
    private val bidInfo: BidInfo,
) {

    val resultCode: AudienzzResultCode
        get() = AudienzzResultCode.getResultCode(bidInfo.resultCode) ?: AudienzzResultCode.NO_BIDS

    val targetingKeywords: Map<String, String>?
        get() = bidInfo.targetingKeywords

    val nativeCacheId: String?
        get() = bidInfo.nativeCacheId

    val exp: Int?
        get() = bidInfo.exp

    val events: Map<String, String>?
        get() = bidInfo.events
}
