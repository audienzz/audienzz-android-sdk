package org.audienzz.mobile.api.rendering

import android.content.Context
import org.audienzz.mobile.AudienzzAdSize
import org.audienzz.mobile.api.data.AudienzzAdUnitFormat
import org.audienzz.mobile.api.exceptions.AudienzzAdException
import org.audienzz.mobile.api.rendering.listeners.AudienzzInterstitialAdUnitListener
import org.audienzz.mobile.api.rendering.pluginrenderer.AudienzzPluginEventListener
import org.audienzz.mobile.event.adClick
import org.audienzz.mobile.event.closeAd
import org.audienzz.mobile.event.eventLogger
import org.audienzz.mobile.eventhandlers.AudienzzGamInterstitialEventHandler
import org.audienzz.mobile.rendering.bidding.data.bid.AudienzzBid
import org.audienzz.mobile.rendering.bidding.listeners.AudienzzInterstitialEventHandler
import org.audienzz.mobile.rendering.bidding.listeners.AudienzzInterstitialEventListener
import org.prebid.mobile.api.exceptions.AdException
import org.prebid.mobile.api.rendering.InterstitialAdUnit
import org.prebid.mobile.api.rendering.listeners.InterstitialAdUnitListener
import org.prebid.mobile.api.rendering.pluginrenderer.PluginEventListener
import org.prebid.mobile.rendering.bidding.data.bid.Bid
import org.prebid.mobile.rendering.bidding.interfaces.InterstitialEventHandler
import org.prebid.mobile.rendering.bidding.listeners.InterstitialEventListener
import java.util.EnumSet

class AudienzzInterstitialAdUnit(
    private val prebidInterstitialAdUnit: InterstitialAdUnit,
    private val adUnitId: String,
) : AudienzzBaseInterstitialAdUnit(prebidInterstitialAdUnit) {

    internal var adUnitFormats: List<AudienzzAdUnitFormat>? = null

    init {
        setInterstitialAdUnitListener(object : AudienzzInterstitialAdUnitListener {})
    }

    /**
     * Instantiates an HTML InterstitialAdUnit for the given configurationId.
     */
    constructor(context: Context, configId: String, adUnitId: String) : this(
        prebidInterstitialAdUnit = InterstitialAdUnit(context, configId),
        adUnitId = adUnitId,
    )

    /**
     * Instantiates an InterstitialAdUnit for the given configurationId and adUnitType.
     */
    constructor(
        context: Context,
        configId: String,
        adUnitFormats: EnumSet<AudienzzAdUnitFormat>,
        adUnitId: String,
    ) : this(
        prebidInterstitialAdUnit = InterstitialAdUnit(
            context,
            configId,
            EnumSet.copyOf(adUnitFormats.map { it.prebidAdUnitFormat }),
        ),
        adUnitId = adUnitId,
    ) {
        this.adUnitFormats = adUnitFormats.toList()
    }

    /**
     * Instantiates an InterstitialAdUnit for HTML GAM prebid integration.
     */
    constructor(
        context: Context,
        configId: String,
        eventHandler: AudienzzInterstitialEventHandler,
    ) : this(
        prebidInterstitialAdUnit = InterstitialAdUnit(
            context,
            configId,
            getInterstitialEventHandler(eventHandler),
        ),
        adUnitId = (eventHandler as? AudienzzGamInterstitialEventHandler)?.adUnitId.orEmpty(),
    ) {
        eventHandler.setAdUnit(this)
    }

    /**
     * Instantiates an InterstitialAdUnit for GAM prebid integration with given adUnitType.
     */
    constructor(
        context: Context,
        configId: String,
        adUnitFormats: EnumSet<AudienzzAdUnitFormat>,
        eventHandler: AudienzzInterstitialEventHandler,
    ) : this(
        prebidInterstitialAdUnit = InterstitialAdUnit(
            context,
            configId,
            EnumSet.copyOf(adUnitFormats.map { it.prebidAdUnitFormat }),
            getInterstitialEventHandler(eventHandler),
        ),
        adUnitId = (eventHandler as? AudienzzGamInterstitialEventHandler)?.adUnitId.orEmpty(),
    ) {
        this.adUnitFormats = adUnitFormats.toList()
        eventHandler.setAdUnit(this)
    }

    fun setInterstitialAdUnitListener(adUnitEventsListener: AudienzzInterstitialAdUnitListener?) {
        prebidInterstitialAdUnit.setInterstitialAdUnitListener(
            adUnitEventsListener?.let {
                getInterstitialAdUnitListener(
                    adUnitId = adUnitId,
                    adUnitListener = it,
                )
            },
        )
    }

    fun setPluginEventListener(pluginEventListener: AudienzzPluginEventListener?) {
        prebidInterstitialAdUnit.setPluginEventListener(
            pluginEventListener?.let { PluginEventListener { it.getPluginRendererName() } },
        )
    }

    fun setMinSizePercentage(minSizePercentage: AudienzzAdSize) {
        prebidInterstitialAdUnit.setMinSizePercentage(minSizePercentage.adSize)
    }

    companion object {

        private fun getInterstitialAdUnitListener(
            adUnitId: String,
            adUnitListener: AudienzzInterstitialAdUnitListener,
        ): InterstitialAdUnitListener {
            return object : InterstitialAdUnitListener {
                override fun onAdLoaded(interstitialAdUnit: InterstitialAdUnit?) {
                    interstitialAdUnit?.let {
                        adUnitListener.onAdLoaded(AudienzzInterstitialAdUnit(it, adUnitId))
                    }
                }

                override fun onAdDisplayed(interstitialAdUnit: InterstitialAdUnit?) {
                    interstitialAdUnit?.let {
                        adUnitListener.onAdDisplayed(AudienzzInterstitialAdUnit(it, adUnitId))
                    }
                }

                override fun onAdFailed(
                    interstitialAdUnit: InterstitialAdUnit?,
                    exception: AdException?,
                ) {
                    interstitialAdUnit?.let {
                        adUnitListener.onAdFailed(
                            AudienzzInterstitialAdUnit(it, adUnitId),
                            exception?.let { e -> AudienzzAdException(e) },
                        )
                    }
                }

                override fun onAdClicked(interstitialAdUnit: InterstitialAdUnit?) {
                    eventLogger?.adClick(adUnitId = adUnitId)
                    interstitialAdUnit?.let {
                        adUnitListener.onAdClicked(AudienzzInterstitialAdUnit(it, adUnitId))
                    }
                }

                override fun onAdClosed(interstitialAdUnit: InterstitialAdUnit?) {
                    eventLogger?.closeAd(adUnitId = adUnitId)
                    interstitialAdUnit?.let {
                        adUnitListener.onAdClosed(AudienzzInterstitialAdUnit(it, adUnitId))
                    }
                }
            }
        }

        private fun getInterstitialEventHandler(
            eventHandler: AudienzzInterstitialEventHandler,
        ): InterstitialEventHandler {
            return object : InterstitialEventHandler {
                override fun setInterstitialEventListener(
                    interstitialEventListener: InterstitialEventListener?,
                ) {
                    eventHandler.setInterstitialEventListener(
                        getAudienzzInterstitialEventListener(interstitialEventListener),
                    )
                }

                override fun requestAdWithBid(bid: Bid?) {
                    eventHandler.requestAdWithBid(bid?.let { AudienzzBid(it) })
                }

                override fun show() {
                    eventHandler.show()
                }

                override fun trackImpression() {
                    eventHandler.trackImpression()
                }

                override fun destroy() {
                    eventHandler.destroy()
                }
            }
        }

        private fun getAudienzzInterstitialEventListener(
            eventListener: InterstitialEventListener?,
        ): AudienzzInterstitialEventListener? {
            if (eventListener == null) {
                return null
            }

            return object : AudienzzInterstitialEventListener {
                override fun onPrebidSdkWin() {
                    eventListener.onPrebidSdkWin()
                }

                override fun onAdServerWin() {
                    eventListener.onAdServerWin()
                }

                override fun onAdFailed(exception: AudienzzAdException) {
                    eventListener.onAdFailed(exception.prebidAdException)
                }

                override fun onAdClosed() {
                    eventListener.onAdClosed()
                }

                override fun onAdDisplayed() {
                    eventListener.onAdDisplayed()
                }
            }
        }
    }
}
