package org.audienzz.mobile.event.entity

internal data class EventDomain(
    val localId: Int? = null,
    val uuid: String? = null,
    val visitorId: String? = null,
    val companyId: String? = null,
    val sessionId: String? = null,
    val deviceId: String? = null,
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
)

internal enum class EventType(val nameString: String) {
    BID_WINNER("mobile.bid_winner"),
    AD_CLICK("mobile.ad_click"),
    BID_REQUEST("mobile.bid_request"),
    AD_CREATION("mobile.ad_creation"),
    CLOSE_AD("mobile.close_ad"),
    AD_FAILED_TO_LOAD("mobile.ad_failed_to_load"),
    SCREEN_IMPRESSION("mobile.screen_impression"),
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
