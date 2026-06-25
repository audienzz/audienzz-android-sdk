package org.audienzz.mobile.event.entity

internal data class EventDomain(
    val localId: Int? = null,
    val uuid: String? = null,
    val visitorId: String? = null,
    val companyId: String? = null,
    val sessionId: String? = null,
    val sessionStartTimestamp: Long? = null,
    val sessionSequence: Int? = null,
    val deviceId: String? = null,
    val pageImpressionId: String? = null,
    val eventType: EventType? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val resultCode: String? = null,
    val adUnitId: String? = null,
    val adViewId: String? = null,
    val targetKeywords: List<String>? = null,
    val isAutorefresh: Boolean? = null,
    val autorefreshTime: Long? = null,
    val isRefresh: Boolean? = null,
    val sizes: String? = null,
    val adType: AdType? = null,
    val adSubtype: AdSubtype? = null,
    val apiType: ApiType? = null,
    val errorMessage: String? = null,
    val screenName: String? = null,
    val bidderCode: String? = null,
    val winnerBidderCode: String? = null,
)

internal enum class EventType(val nameString: String) {
    PAGE_IMPRESSION("pageImpression"),
    BID_REQUEST("bidRequest"),
    BID_RESPONSE("bidResponse"),
    BID_WON("bidWon"),
    NO_BID("noBid"),
    AD_IMPRESSION("adImpression"),
    AD_CLICK("adClick"),
    VIEWABILITY_START("viewability.start"),
    VIEWABILITY_SUCCESS("viewability.success"),
}

internal enum class AdType(val nameString: String) {
    BANNER("BANNER"),
    INTERSTITIAL("INTERSTITIAL"),
    REWARDED("REWARDED"),
}

internal enum class AdSubtype(val nameString: String) {
    HTML("HTML"),
    VIDEO("VIDEO"),
    MULTIFORMAT("MULTIFORMAT"),
}

internal enum class ApiType(val nameString: String) {
    ORIGINAL("ORIGINAL"),
    RENDER("RENDER"),
}
