package org.audienzz.mobile.util

import com.google.android.gms.ads.admanager.AdManagerAdRequest

/** Prebid targeting keys describing the winning bid. */
internal const val HB_BIDDER_KEY = "hb_bidder"
internal const val HB_PB_KEY = "hb_pb"
internal const val HB_SIZE_KEY = "hb_size"
internal const val HB_FORMAT_KEY = "hb_format"

/** `bidder_code` reported when the ad server (Google/AdX/direct) rendered instead of Prebid. */
internal const val AD_SERVER_BIDDER = "google"

/**
 * Reads a Prebid targeting keyword (e.g. `hb_bidder`, `hb_pb`, `hb_size`, `hb_format`) from a GAM
 * request after `fetchDemand` has applied the auction targeting.
 *
 * Prebid Mobile places the keywords in the request's `customTargeting` bundle on the GAM original
 * API; older paths may use the `keywords` set as `"key:value"` strings, so both are checked.
 */
internal fun AdManagerAdRequest.prebidKeyword(key: String): String? {
    customTargeting?.getString(key)?.let { return it }
    return keywords.firstOrNull { it.startsWith("$key:") }?.substringAfter(":")
}
