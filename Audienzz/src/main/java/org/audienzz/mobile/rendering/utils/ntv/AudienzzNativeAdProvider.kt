package org.audienzz.mobile.rendering.utils.ntv

import android.os.Bundle
import org.audienzz.mobile.AudienzzPrebidNativeAd
import org.prebid.mobile.rendering.utils.ntv.NativeAdProvider

object AudienzzNativeAdProvider {

    @JvmStatic
    fun getNativeAd(extras: Bundle): AudienzzPrebidNativeAd? {
        val nativeAd = NativeAdProvider.getNativeAd(extras)
        return nativeAd?.let { AudienzzPrebidNativeAd(it) }
    }
}
