package org.audienzz.mobile.api.rendering

import androidx.annotation.FloatRange
import org.audienzz.mobile.api.data.AudienzzPosition
import org.audienzz.mobile.rendering.bidding.data.bid.AudienzzBidResponse
import org.prebid.mobile.api.rendering.BaseInterstitialAdUnit

@Suppress("TooManyFunctions")
abstract class AudienzzBaseInterstitialAdUnit internal constructor(
    private val adUnit: BaseInterstitialAdUnit,
) {

    var pbAdSlot: String?
        get() = adUnit.pbAdSlot
        set(value) {
            adUnit.pbAdSlot = value
        }

    /**
     * Executes ad loading if no request is running.
     */
    fun loadAd() {
        adUnit.loadAd()
    }

    /**
     * @return true if auction winner was defined, false otherwise
     */
    fun isLoaded(): Boolean = adUnit.isLoaded

    /**
     * Executes interstitial display if auction winner is defined.
     */
    fun show() {
        adUnit.show()
    }

    /**
     * Sets delay in seconds to show skip or close button.
     */
    fun setSkipDelay(secondsDelay: Int) {
        adUnit.setSkipDelay(secondsDelay)
    }

    /**
     * Sets skip button percentage size in range from 0.05 to 1.
     * If value less than 0.05, size will be default.
     */
    fun setSkipButtonArea(@FloatRange(from = 0.0, to = 1.0) buttonArea: Double) {
        adUnit.setSkipButtonArea(buttonArea)
    }

    /**
     * Sets skip button position on the screen. Suitable values TOP_LEFT and TOP_RIGHT.
     * Default value TOP_RIGHT.
     */
    fun setSkipButtonPosition(skipButtonPosition: AudienzzPosition) {
        adUnit.setSkipButtonPosition(skipButtonPosition.prebidPosition)
    }

    fun setIsMuted(isMuted: Boolean) {
        adUnit.setIsMuted(isMuted)
    }

    fun setIsSoundButtonVisible(isSoundButtonVisible: Boolean) {
        adUnit.setIsSoundButtonVisible(isSoundButtonVisible)
    }

    fun setMaxVideoDuration(seconds: Int) {
        adUnit.setMaxVideoDuration(seconds)
    }

    /**
     * Sets close button percentage size in range from 0.05 to 1.
     * If value less than 0.05, size will be default.
     */
    fun setCloseButtonArea(@FloatRange(from = 0.0, to = 1.0) closeButtonArea: Double) {
        adUnit.setCloseButtonArea(closeButtonArea)
    }

    /**
     * Sets close button position on the screen. Suitable values TOP_LEFT and TOP_RIGHT.
     * Default value TOP_RIGHT.
     */
    fun setCloseButtonPosition(closeButtonPosition: AudienzzPosition?) {
        adUnit.setCloseButtonPosition(closeButtonPosition?.prebidPosition)
    }

    /**
     * Cleans up resources when destroyed.
     */
    fun destroy() {
        adUnit.destroy()
    }

    fun getBidResponse(): AudienzzBidResponse? =
        adUnit.bidResponse?.let { AudienzzBidResponse(it) }
}
