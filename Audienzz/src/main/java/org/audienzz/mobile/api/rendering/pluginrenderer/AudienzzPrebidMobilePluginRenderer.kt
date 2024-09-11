package org.audienzz.mobile.api.rendering.pluginrenderer

import android.content.Context
import android.view.View
import org.audienzz.mobile.api.rendering.AudienzzPrebidMobileInterstitialControllerInterface
import org.audienzz.mobile.configuration.AudienzzAdUnitConfiguration
import org.audienzz.mobile.rendering.bidding.data.bid.AudienzzBidResponse
import org.audienzz.mobile.rendering.bidding.interfaces.AudienzzInterstitialControllerListener
import org.audienzz.mobile.rendering.bidding.listeners.AudienzzDisplayVideoListener
import org.audienzz.mobile.rendering.bidding.listeners.AudienzzDisplayViewListener
import org.json.JSONObject

interface AudienzzPrebidMobilePluginRenderer {

    fun getName(): String

    fun getVersion(): String

    fun getData(): JSONObject?

    /**
     * Register a listener related to a specific ad unit config fingerprint in order to
     * dispatch specific ad events
     */
    fun registerEventListener(
        pluginEventListener: AudienzzPluginEventListener,
        listenerKey: String?,
    )

    /**
     * Unregister a listener based on an ad unit config fingerprint
     */
    fun unregisterEventListener(listenerKey: String?)

    /**
     * Creates and returns Banner View for a given Bid Response.
     * Returns nil in the case of an internal error.
     * <br>
     * Don't forget to clean resources in {@link android.view.View#onDetachedFromWindow()}.
     */
    fun createBannerAdView(
        context: Context,
        displayViewListener: AudienzzDisplayViewListener,
        displayVideoListener: AudienzzDisplayVideoListener?,
        adUnitConfiguration: AudienzzAdUnitConfiguration,
        bidResponse: AudienzzBidResponse,
    ): View

    /**
     * Creates and returns an implementation of PrebidMobileInterstitialControllerInterface for
     * a given bid response. Returns nil in the case of an internal error
     */
    fun createInterstitialController(
        context: Context,
        interstitialControllerListener: AudienzzInterstitialControllerListener,
        adUnitConfiguration: AudienzzAdUnitConfiguration,
        bidResponse: AudienzzBidResponse,
    ): AudienzzPrebidMobileInterstitialControllerInterface

    /**
     * Returns true only if the given ad unit could be renderer by the plugin
     */
    fun isSupportRenderingFor(adUnitConfiguration: AudienzzAdUnitConfiguration?): Boolean
}
