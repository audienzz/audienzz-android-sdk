package org.audienzz.mobile.rendering.bidding.data.bid

import org.audienzz.mobile.rendering.models.openrtb.bidRequests.AudienzzMobileSdkPassThrough
import org.prebid.mobile.rendering.bidding.data.bid.Bid

data class AudienzzBid internal constructor(internal val prebidBid: Bid) {

    // Bidder generated bid ID to assist with logging/tracking.
    val id: String? = prebidBid.id

    //  ID of the Imp object in the related bid request.
    val impId: String? = prebidBid.impId

    // Bid price expressed as CPM although the actual transaction is
    // for a unit impression only.
    val price: Double = prebidBid.price

    // Optional means of conveying ad markup in case the bid wins;
    // supersedes the win notice if markup is included in both.
    // Substitution macros (Section 4.4) may be included.
    val adm: String? = prebidBid.adm

    // Creative ID to assist with ad quality checking.
    val crid: String? = prebidBid.crid

    // Width of the creative in device independent pixels (DIPS)
    val width: Int = prebidBid.width

    // Height of the creative in device independent pixels (DIPS).
    val height: Int = prebidBid.height

    // "prebid" object from "ext"
    val prebid: AudienzzPrebid = AudienzzPrebid(prebidBid.prebid)

    // Win notice URL called by the exchange if the bid wins (not  necessarily indicative of
    // a delivered, viewed, or billable ad);
    // optional means of serving ad markup
    val nurl: String? = prebidBid.nurl

    // Billing notice URL called by the exchange when a winning bid
    // becomes billable based on exchange-specific business policy
    val burl: String? = prebidBid.burl

    // Loss notice URL called by the exchange when a bid is known to
    // have been lost
    val lurl: String? = prebidBid.lurl

    // ID of a preloaded ad to be served if the bid wins
    val adid: String? = prebidBid.adid

    // Advertiser domain for block list checking
    val adomain: Array<String>? = prebidBid.adomain

    // A platform-specific application identifier intended to be unique to the app and independent
    // of the exchange.
    val bundle: String? = prebidBid.bundle

    // URL without cache-busting to an image that is representative
    // of the content of the campaign for ad quality/safety checking
    val iurl: String? = prebidBid.iurl

    // Campaign ID to assist with ad quality checking; the collection
    // of creatives for which iurl should be representative
    val cid: String? = prebidBid.cid

    // Bid json string. Used only for CacheManager.
    val jsonString: String? = prebidBid.jsonString

    // Tactic ID to enable buyers to label bids for reporting to the
    // exchange the tactic through which their bid was submitted
    val tactic: String? = prebidBid.tactic

    // IAB content categories of the creative
    val cat: Array<String>? = prebidBid.cat

    // Set of attributes describing the creative
    val attr: IntArray? = prebidBid.attr

    // API required by the markup if applicable
    val api: Int = prebidBid.api

    // Video response protocol of the markup if applicable.
    val protocol: Int = prebidBid.protocol

    // Creative media rating per IQG guidelines
    val qagmediarating: Int = prebidBid.qagmediarating

    // Language of the creative using ISO-639-1-alpha-2
    val language: String? = prebidBid.language

    // Reference to the deal.id from the bid request if this bid
    // pertains to a private marketplace direct deal
    val dealId: String? = prebidBid.dealId

    // Relative width of the creative when expressing size as a ratio.
    // Required for Flex Ads
    val wRatio: Int = prebidBid.wRatio

    // Relative height of the creative when expressing size as a ratio.
    // Required for Flex Ads
    val hRatio: Int = prebidBid.hRatio

    // Advisory as to the number of seconds the bidder is willing to
    // wait between the auction and the actual impression
    val exp: Int = prebidBid.exp

    val events: Map<String, String>? = prebidBid.events

    val mobileSdkPassThrough: AudienzzMobileSdkPassThrough? =
        prebidBid.mobileSdkPassThrough?.let { AudienzzMobileSdkPassThrough(it) }
}
