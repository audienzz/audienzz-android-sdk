package org.audienzz.mobile.rendering.bidding.data.bid

import android.content.Context
import android.util.Pair
import org.audienzz.mobile.configuration.AudienzzAdUnitConfiguration
import org.audienzz.mobile.rendering.models.openrtb.bidRequests.AudienzzExt
import org.audienzz.mobile.rendering.models.openrtb.bidRequests.AudienzzMobileSdkPassThrough
import org.prebid.mobile.rendering.bidding.data.bid.BidResponse

class AudienzzBidResponse internal constructor(internal val prebidBidResponse: BidResponse) {

    val id: String? = prebidBidResponse.id

    val seatbids: List<AudienzzSeatbid> = prebidBidResponse.seatbids.map { AudienzzSeatbid(it) }

    val cur: String? = prebidBidResponse.cur

    val ext: AudienzzExt = AudienzzExt(prebidBidResponse.ext)

    val hasParseError: Boolean = prebidBidResponse.hasParseError()

    val parseError: String? = prebidBidResponse.parseError

    val bidId: String? = prebidBidResponse.bidId

    val customData: String? = prebidBidResponse.customData

    val nbr: Int = prebidBidResponse.nbr

    val winningBidJson: String? = prebidBidResponse.winningBidJson

    val creationTime: Long = prebidBidResponse.creationTime

    val winningBid: AudienzzBid? = prebidBidResponse.winningBid?.let { AudienzzBid(it) }

    val targeting: HashMap<String, String> = prebidBidResponse.targeting

    val targetingWithCacheId: HashMap<String, String> = prebidBidResponse.targetingWithCacheId

    val isVideo: Boolean = prebidBidResponse.isVideo

    val preferredPluginRenderedName: String? = prebidBidResponse.preferredPluginRendererName

    val preferredPluginRendererVersion: String? = prebidBidResponse.preferredPluginRendererVersion

    val adUnitConfiguration: AudienzzAdUnitConfiguration =
        AudienzzAdUnitConfiguration(prebidBidResponse.adUnitConfiguration)

    var mobileSdKPassThrough: AudienzzMobileSdkPassThrough?
        get() = prebidBidResponse.mobileSdkPassThrough?.let { AudienzzMobileSdkPassThrough(it) }
        set(value) {
            prebidBidResponse.mobileSdkPassThrough = value?.prebidMobileSdkPassThrough
        }

    val impressionEventUrl: String? = prebidBidResponse.impressionEventUrl

    val expirationTimeSeconds: Int? = prebidBidResponse.expirationTimeSeconds

    fun getWinningBidWidthHeightPairDips(context: Context): Pair<Int, Int> =
        prebidBidResponse.getWinningBidWidthHeightPairDips(context)
}
