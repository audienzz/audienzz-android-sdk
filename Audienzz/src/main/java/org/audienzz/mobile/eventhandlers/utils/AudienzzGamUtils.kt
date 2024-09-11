package org.audienzz.mobile.eventhandlers.utils

import android.os.Bundle
import com.google.android.gms.ads.admanager.AdManagerAdRequest
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeCustomFormatAd
import org.prebid.mobile.eventhandlers.utils.GamUtils

object AudienzzGamUtils {

    @JvmStatic
    fun prepare(adRequest: AdManagerAdRequest, extras: Bundle) {
        GamUtils.prepare(adRequest, extras)
    }

    @JvmStatic
    fun handleGamCustomTargetingUpdate(
        adRequest: AdManagerAdRequest,
        keywords: Map<String, String>,
    ) {
        GamUtils.handleGamCustomTargetingUpdate(adRequest, keywords)
    }

    @JvmStatic
    fun didPrebidWin(unifiedNativeAd: NativeAd): Boolean =
        GamUtils.didPrebidWin(unifiedNativeAd)

    @JvmStatic
    fun didPrebidWin(ad: NativeCustomFormatAd): Boolean =
        GamUtils.didPrebidWin(ad)
}
