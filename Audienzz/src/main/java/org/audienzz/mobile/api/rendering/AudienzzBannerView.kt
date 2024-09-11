package org.audienzz.mobile.api.rendering

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import org.audienzz.mobile.AudienzzAdSize
import org.audienzz.mobile.AudienzzContentObject
import org.audienzz.mobile.AudienzzDataObject
import org.audienzz.mobile.api.data.AudienzzBannerAdPosition
import org.audienzz.mobile.api.data.AudienzzVideoPlacementType
import org.audienzz.mobile.api.exceptions.AudienzzAdException
import org.audienzz.mobile.api.rendering.listeners.AudienzzBannerVideoListener
import org.audienzz.mobile.api.rendering.listeners.AudienzzBannerViewListener
import org.audienzz.mobile.api.rendering.pluginrenderer.AudienzzPluginEventListener
import org.audienzz.mobile.rendering.bidding.data.bid.AudienzzBid
import org.audienzz.mobile.rendering.bidding.data.bid.AudienzzBidResponse
import org.audienzz.mobile.rendering.bidding.listeners.AudienzzBannerEventHandler
import org.audienzz.mobile.rendering.bidding.listeners.AudienzzBannerEventListener
import org.audienzz.mobile.util.addOnBecameVisibleOnScreenListener
import org.prebid.mobile.AdSize
import org.prebid.mobile.api.exceptions.AdException
import org.prebid.mobile.api.rendering.BannerView
import org.prebid.mobile.api.rendering.listeners.BannerVideoListener
import org.prebid.mobile.api.rendering.listeners.BannerViewListener
import org.prebid.mobile.rendering.bidding.data.bid.Bid
import org.prebid.mobile.rendering.bidding.interfaces.BannerEventHandler
import org.prebid.mobile.rendering.bidding.listeners.BannerEventListener

@Suppress("TooManyFunctions")
class AudienzzBannerView internal constructor(
    internal val prebidBannerView: BannerView,
) {

    val view: FrameLayout = prebidBannerView

    val autoRefreshDelayInMs: Int = prebidBannerView.autoRefreshDelayInMs

    val additionalSizes: Set<AudienzzAdSize> =
        prebidBannerView.additionalSizes.map { AudienzzAdSize(it) }.toSet()

    var videoPlacementType: AudienzzVideoPlacementType?
        get() = prebidBannerView.videoPlacementType?.let {
            AudienzzVideoPlacementType.fromPrebidVideoPlacementType(it)
        }
        set(value) {
            prebidBannerView.videoPlacementType = value?.prebidVideoPlacementType
        }

    val extDataDictionary: Map<String, Set<String>> = prebidBannerView.extDataDictionary

    val extKeywordsSet: Set<String> = prebidBannerView.extKeywordsSet

    var adPosition: AudienzzBannerAdPosition
        get() = AudienzzBannerAdPosition.fromPrebidBannerAdPosition(prebidBannerView.adPosition)
        set(value) {
            prebidBannerView.adPosition = value.prebidBannerAdPosition
        }

    var pbAdSlot: String?
        get() = prebidBannerView.pbAdSlot
        set(value) {
            prebidBannerView.pbAdSlot = value
        }

    val userData: List<AudienzzDataObject> =
        prebidBannerView.userData.map { AudienzzDataObject(it) }

    val bidResponse: AudienzzBidResponse? =
        prebidBannerView.bidResponse?.let { AudienzzBidResponse(it) }

    /**
     * Instantiates an BannerView with the ad details as an attribute.
     *
     * @param attrs includes:
     * <p>
     * adUnitID
     * refreshIntervalInSec
     */
    constructor(context: Context, attrs: AttributeSet?) : this(BannerView(context, attrs))

    /**
     * Instantiates an BannerView for the given configId and adSize.
     */
    constructor(
        context: Context,
        configId: String,
        adSize: AudienzzAdSize,
    ) : this(
        BannerView(
            context,
            configId,
            adSize.adSize,
        ),
    )

    /**
     * Instantiates an BannerView for GAM prebid integration.
     */
    constructor(
        context: Context,
        configId: String,
        eventHandler: AudienzzBannerEventHandler,
    ) : this(
        BannerView(
            context,
            configId,
            getBannerEventHandler(eventHandler),
        ),
    )

    /**
     * Executes ad loading if no request is running.
     *
     * @param lazyLoad allows to postpone loadAd call until view is visible
     */
    fun loadAd(lazyLoad: Boolean = false) {
        if (lazyLoad) {
            prebidBannerView.addOnBecameVisibleOnScreenListener {
                prebidBannerView.loadAd()
            }
        } else {
            prebidBannerView.loadAd()
        }
    }

    /**
     * Cancels BidLoader refresh timer.
     */
    fun stopRefresh() {
        prebidBannerView.stopRefresh()
    }

    /**
     * Cleans up resources when destroyed.
     */
    fun destroy() {
        prebidBannerView.destroy()
    }

    fun setAutoRefreshDelay(seconds: Int) {
        prebidBannerView.setAutoRefreshDelay(seconds)
    }

    @Suppress("SpreadOperator")
    fun addAdditionalSizes(vararg sizes: AudienzzAdSize) {
        prebidBannerView.addAdditionalSizes(*sizes.map { it.adSize }.toTypedArray())
    }

    fun setBannerListener(bannerListener: AudienzzBannerViewListener) {
        prebidBannerView.setBannerListener(getBannerViewListener(bannerListener))
    }

    fun setBannerVideoListener(bannerVideoListener: AudienzzBannerVideoListener) {
        prebidBannerView.setBannerVideoListener(getBannerVideoListener(bannerVideoListener))
    }

    fun setPluginEventListener(pluginEventListener: AudienzzPluginEventListener) {
        prebidBannerView.setPluginEventListener { pluginEventListener.getPluginRendererName() }
    }

    /**
     * Sets BannerEventHandler for GAM prebid integration
     *
     * @param eventHandler instance of GamBannerEventHandler
     */
    fun setEventHandler(eventHandler: AudienzzBannerEventHandler) {
        prebidBannerView.setEventHandler(
            getBannerEventHandler(eventHandler),
        )
    }

    fun addExtData(key: String, value: String) {
        prebidBannerView.addExtData(key, value)
    }

    fun updateExtData(key: String, value: Set<String>) {
        prebidBannerView.updateExtData(key, value)
    }

    fun removeExtData(key: String) {
        prebidBannerView.removeExtData(key)
    }

    fun clearExtData() {
        prebidBannerView.clearExtData()
    }

    fun addExtKeyword(keyword: String) {
        prebidBannerView.addExtKeyword(keyword)
    }

    fun addExtKeywords(keywords: Set<String>) {
        prebidBannerView.addExtKeywords(keywords)
    }

    fun removeExtKeyword(keyword: String) {
        prebidBannerView.removeExtKeyword(keyword)
    }

    fun clearExtKeywords() {
        prebidBannerView.clearExtKeywords()
    }

    fun setAppContent(content: AudienzzContentObject) {
        prebidBannerView.setAppContent(content.prebidContentObject)
    }

    fun addUserData(dataObject: AudienzzDataObject) {
        prebidBannerView.addUserData(dataObject.prebidDataObject)
    }

    fun clearUserData() {
        prebidBannerView.clearUserData()
    }

    companion object {

        private fun getBannerEventHandler(eventHandler: AudienzzBannerEventHandler) =
            object : BannerEventHandler {
                override fun getAdSizeArray(): Array<AdSize> =
                    eventHandler.getAdSizeArray().map { it.adSize }.toTypedArray()

                override fun setBannerEventListener(bannerViewListener: BannerEventListener) {
                    eventHandler.setBannerEventListener(
                        getBannerEventListener(bannerViewListener),
                    )
                }

                override fun requestAdWithBid(bid: Bid?) {
                    eventHandler.requestAdWithBid(bid?.let { AudienzzBid(it) })
                }

                override fun trackImpression() {
                    eventHandler.trackImpression()
                }

                override fun destroy() {
                    eventHandler.destroy()
                }
            }

        private fun getBannerEventListener(listener: BannerEventListener) =
            object : AudienzzBannerEventListener {
                override fun onPrebidSdkWin() {
                    listener.onPrebidSdkWin()
                }

                override fun onAdServerWin(view: View?) {
                    listener.onAdServerWin(view)
                }

                override fun onAdFailed(exception: AudienzzAdException?) {
                    listener.onAdFailed(exception?.prebidAdException)
                }

                override fun onAdClicked() {
                    listener.onAdClicked()
                }

                override fun onAdClosed() {
                    listener.onAdClosed()
                }
            }

        private fun getBannerViewListener(listener: AudienzzBannerViewListener) =
            object : BannerViewListener {
                override fun onAdLoaded(bannerView: BannerView?) {
                    listener.onAdLoaded(bannerView?.let { AudienzzBannerView(it) })
                }

                override fun onAdDisplayed(bannerView: BannerView?) {
                    listener.onAdDisplayed(bannerView?.let { AudienzzBannerView(it) })
                }

                override fun onAdFailed(bannerView: BannerView?, exception: AdException?) {
                    listener.onAdFailed(
                        bannerView?.let { AudienzzBannerView(it) },
                        exception?.let { AudienzzAdException(it) },
                    )
                }

                override fun onAdClicked(bannerView: BannerView?) {
                    listener.onAdClicked(bannerView?.let { AudienzzBannerView(it) })
                }

                override fun onAdClosed(bannerView: BannerView?) {
                    listener.onAdClosed(bannerView?.let { AudienzzBannerView(it) })
                }
            }

        private fun getBannerVideoListener(listener: AudienzzBannerVideoListener) =
            object : BannerVideoListener {
                override fun onVideoCompleted(bannerView: BannerView?) {
                    listener.onVideoCompleted(bannerView?.let { AudienzzBannerView(it) })
                }

                override fun onVideoPaused(bannerView: BannerView?) {
                    listener.onVideoPaused(bannerView?.let { AudienzzBannerView(it) })
                }

                override fun onVideoResumed(bannerView: BannerView?) {
                    listener.onVideoResumed(bannerView?.let { AudienzzBannerView(it) })
                }

                override fun onVideoUnMuted(bannerView: BannerView?) {
                    listener.onVideoUnMuted(bannerView?.let { AudienzzBannerView(it) })
                }

                override fun onVideoMuted(bannerView: BannerView?) {
                    listener.onVideoMuted(bannerView?.let { AudienzzBannerView(it) })
                }
            }
    }
}
