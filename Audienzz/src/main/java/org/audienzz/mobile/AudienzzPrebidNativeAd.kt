package org.audienzz.mobile

import android.view.View
import org.prebid.mobile.PrebidNativeAd
import org.prebid.mobile.PrebidNativeAdEventListener

class AudienzzPrebidNativeAd(private val prebidNativeAd: PrebidNativeAd) {

    /**
     * @return First description data value or empty string if it doesn't exist
     */
    val description = prebidNativeAd.description

    /**
     * @return First icon url or empty string if it doesn't exist
     */
    val iconUrl = prebidNativeAd.iconUrl

    /**
     * @return First main image url or empty string if it doesn't exist
     */
    val imageUrl = prebidNativeAd.imageUrl

    /**
     * @return First call to action data value or empty string if it doesn't exist
     */
    val callToAction = prebidNativeAd.callToAction

    /**
     * @return First sponsored by data value or empty string if it doesn't exist
     */
    val sponsoredBy = prebidNativeAd.sponsoredBy

    val winEvent: String? = prebidNativeAd.winEvent

    val impEvent: String? = prebidNativeAd.impEvent

    /**
     * @return First title or empty string if it doesn't exist
     */
    val title = prebidNativeAd.title

    fun addTitle(title: AudienzzNativeTitle) {
        prebidNativeAd.addTitle(title.nativeTitle)
    }

    fun getTitles(): List<AudienzzNativeTitle> =
        prebidNativeAd.titles.map { AudienzzNativeTitle(it) }

    fun addData(data: AudienzzNativeData) {
        prebidNativeAd.addData(data.nativeData)
    }

    fun getDataLists(): List<AudienzzNativeData> =
        prebidNativeAd.dataList.map { AudienzzNativeData(it) }

    fun addImage(image: AudienzzNativeImage) {
        prebidNativeAd.addImage(image.nativeImage)
    }

    fun getImages(): List<AudienzzNativeImage> =
        prebidNativeAd.images.map { AudienzzNativeImage(it) }

    fun registerView(
        container: View?,
        clickableViews: List<View>?,
        listener: AudienzzPrebidNativeAdEventListener?,
    ): Boolean {
        return if (listener == null) {
            prebidNativeAd.registerView(container, clickableViews, null)
        } else {
            val prebidListener = object : PrebidNativeAdEventListener {

                override fun onAdClicked() {
                    listener.onAdClicked()
                }

                override fun onAdImpression() {
                    listener.onAdImpression()
                }

                override fun onAdExpired() {
                    listener.onAdExpired()
                }
            }

            prebidNativeAd.registerView(container, clickableViews, prebidListener)
        }
    }

    companion object {

        @JvmStatic
        fun create(cacheId: String): AudienzzPrebidNativeAd =
            AudienzzPrebidNativeAd(PrebidNativeAd.create(cacheId))
    }
}
