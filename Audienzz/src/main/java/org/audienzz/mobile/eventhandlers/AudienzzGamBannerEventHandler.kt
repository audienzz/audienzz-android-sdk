package org.audienzz.mobile.eventhandlers

import android.content.Context
import android.view.View
import com.google.android.gms.ads.AdSize
import org.audienzz.mobile.AudienzzAdSize
import org.audienzz.mobile.api.exceptions.AudienzzAdException
import org.audienzz.mobile.event.adClick
import org.audienzz.mobile.event.adCreation
import org.audienzz.mobile.event.adFailedToLoad
import org.audienzz.mobile.event.entity.AdSubtype
import org.audienzz.mobile.event.entity.AdType
import org.audienzz.mobile.event.entity.ApiType
import org.audienzz.mobile.event.eventLogger
import org.audienzz.mobile.rendering.bidding.data.bid.AudienzzBid
import org.audienzz.mobile.rendering.bidding.listeners.AudienzzBannerEventHandler
import org.audienzz.mobile.rendering.bidding.listeners.AudienzzBannerEventListener
import org.audienzz.mobile.util.prebidSizeString
import org.prebid.mobile.api.exceptions.AdException
import org.prebid.mobile.eventhandlers.GamBannerEventHandler
import org.prebid.mobile.rendering.bidding.listeners.BannerEventListener

/**
 * This class is compatible with Prebid Rendering SDK v1.10.
 * This class implements the communication between the Prebid Rendering SDK and the GAM SDK for a
 * given ad unit. It implements the Prebid Rendering SDK EventHandler interface.
 * Prebid Rendering SDK notifies (using EventHandler interface) to make a request to GAM SDK and
 * pass the targeting parameters. This class also creates the GAM's PublisherAdViews, initialize
 * them and listens for the callback methods. And pass the GAM ad event to Prebid Rendering SDK
 * via BannerEventListener.
 */
class AudienzzGamBannerEventHandler internal constructor(
    internal val prebidGamBannerEventHandler: GamBannerEventHandler,
) : AudienzzBannerEventHandler, AudienzzGamAdEventListener {

    private var adUnitId: String = ""

    /**
     * @param context     activity or application context.
     * @param gamAdUnitId GAM AdUnitId.
     * @param adSizes     ad sizes for banner.
     */
    @Suppress("SpreadOperator")
    constructor(
        context: Context,
        gamAdUnitId: String,
        vararg adSizes: AudienzzAdSize,
    ) : this(
        GamBannerEventHandler(
            context,
            gamAdUnitId,
            *adSizes.map { it.adSize }.toTypedArray(),
        ),
    ) {
        this.adUnitId = gamAdUnitId
        setBannerEventListener(object : AudienzzBannerEventListener {})
        eventLogger?.adCreation(
            adUnitId = adUnitId,
            sizes = prebidGamBannerEventHandler.adSizeArray.asIterable().prebidSizeString,
            adType = AdType.BANNER,
            adSubtype = AdSubtype.MULTIFORMAT,
            apiType = ApiType.RENDER,
        )
    }

    override fun onEvent(adEvent: AudienzzAdEvent) {
        prebidGamBannerEventHandler.onEvent(adEvent.prebidAdEvent)
    }

    override fun getAdSizeArray(): List<AudienzzAdSize> =
        prebidGamBannerEventHandler.adSizeArray.map { AudienzzAdSize(it) }

    override fun setBannerEventListener(bannerViewListener: AudienzzBannerEventListener) {
        prebidGamBannerEventHandler.setBannerEventListener(
            getBannerEventListener(bannerViewListener),
        )
    }

    override fun requestAdWithBid(bid: AudienzzBid?) {
        prebidGamBannerEventHandler.requestAdWithBid(bid?.prebidBid)
    }

    override fun trackImpression() {
        prebidGamBannerEventHandler.trackImpression()
    }

    override fun destroy() {
        prebidGamBannerEventHandler.destroy()
    }

    private fun getBannerEventListener(listener: AudienzzBannerEventListener) =
        object : BannerEventListener {
            override fun onPrebidSdkWin() {
                listener.onPrebidSdkWin()
            }

            override fun onAdServerWin(view: View?) {
                listener.onAdServerWin(view)
            }

            override fun onAdFailed(exception: AdException?) {
                listener.onAdFailed(exception?.let { AudienzzAdException(it) })
                eventLogger?.adFailedToLoad(
                    adUnitId = adUnitId,
                    errorMessage = exception?.message,
                )
            }

            override fun onAdClicked() {
                listener.onAdClicked()
                eventLogger?.adClick(adUnitId = adUnitId)
            }

            override fun onAdClosed() {
                listener.onAdClosed()
            }
        }

    companion object {

        @JvmStatic
        fun convertGamAdSize(vararg sizes: AdSize): List<AudienzzAdSize> =
            GamBannerEventHandler.convertGamAdSize(*sizes).map { AudienzzAdSize(it) }
    }
}
