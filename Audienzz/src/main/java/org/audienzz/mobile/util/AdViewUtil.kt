package org.audienzz.mobile.util

import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.admanager.AdManagerAdView
import org.audienzz.mobile.AudienzzAdSize

internal val AdManagerAdView.adViewId: String
    get() = id.toString()

internal val Iterable<AdSize>.sizeString
    get() = joinToString { it.sizeString }

internal val AdSize.sizeString: String
    get() = "${width}x$height"

internal val Iterable<org.prebid.mobile.AdSize>.prebidSizeString
    get() = joinToString { it.prebidSizeString }

internal val org.prebid.mobile.AdSize.prebidSizeString: String
    get() = "${width}x$height"

internal val Iterable<AudienzzAdSize>.audienzzSizeString
    get() = joinToString { it.audienzzSizeString }

internal val AudienzzAdSize.audienzzSizeString: String
    get() = "${width}x$height"
