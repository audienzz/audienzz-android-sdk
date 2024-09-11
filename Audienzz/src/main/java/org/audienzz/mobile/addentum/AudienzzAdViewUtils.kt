package org.audienzz.mobile.addentum

import android.view.View
import org.audienzz.mobile.AudienzzPrebidNativeAd
import org.audienzz.mobile.AudienzzPrebidNativeAdListener
import org.prebid.mobile.PrebidNativeAd
import org.prebid.mobile.PrebidNativeAdListener
import org.prebid.mobile.addendum.AdViewUtils
import org.prebid.mobile.addendum.AdViewUtils.PbFindSizeListener
import org.prebid.mobile.addendum.PbFindSizeError

object AudienzzAdViewUtils {

    @JvmStatic
    fun findPrebidCreativeSize(
        adView: View?,
        handle: AudienzzPbFindSizeListener,
    ) {
        val prebidHandle = object : PbFindSizeListener {
            override fun success(width: Int, height: Int) {
                handle.success(width, height)
            }

            override fun failure(error: PbFindSizeError) {
                handle.failure(error.code)
            }
        }
        AdViewUtils.findPrebidCreativeSize(adView, prebidHandle)
    }

    /**
     * This API can be used to find if the passed object contains info to retreive valid
     * cached Native response or not,
     * and notifies using the {@link PrebidNativeAdListener}
     *
     * @param object   instances of Google Native Ads
     * @param listener to notify the validity of passed object via @onPrebidNativeLoaded,
     * #onPrebidNativeNotFound, #onPrebidNativeNotValid
     */
    @JvmStatic
    fun findNative(
        o: Any,
        listener: AudienzzPrebidNativeAdListener,
    ) {
        val prebidListener = object : PrebidNativeAdListener {
            override fun onPrebidNativeLoaded(ad: PrebidNativeAd?) {
                listener.onPrebidNativeLoaded(ad?.let { AudienzzPrebidNativeAd(it) })
            }

            override fun onPrebidNativeNotFound() {
                listener.onPrebidNativeNotFound()
            }

            override fun onPrebidNativeNotValid() {
                listener.onPrebidNativeNotValid()
            }
        }

        AdViewUtils.findNative(o, prebidListener)
    }
}

interface AudienzzPbFindSizeListener {

    fun success(width: Int, height: Int)

    fun failure(errorCode: Int)
}
