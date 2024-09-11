package org.audienzz.mobile.api.mediation

import androidx.annotation.FloatRange
import org.audienzz.mobile.api.data.AudienzzPosition
import org.prebid.mobile.api.mediation.MediationBaseFullScreenAdUnit

abstract class AudienzzMediationBaseFullScreenAdUnit internal constructor(
    internal val prebidMediationBaseFullScreenAdUnit: MediationBaseFullScreenAdUnit,
) : AudienzzMediationBaseAdUnit(prebidMediationBaseFullScreenAdUnit) {

    /**
     * Sets max video duration. If the ad from server is bigger, it will be rejected.
     */
    fun setMaxVideoDuration(seconds: Int) {
        prebidMediationBaseFullScreenAdUnit.setMaxVideoDuration(seconds)
    }

    /**
     * Sets delay in seconds to show skip or close button.
     */
    fun setSkipDelay(secondsDelay: Int) {
        prebidMediationBaseFullScreenAdUnit.setSkipDelay(secondsDelay)
    }

    /**
     * Sets skip button percentage size in range from 0.05 to 1.
     * If value less than 0.05, size will be default.
     */
    fun setSkipButtonArea(@FloatRange(from = 0.0, to = 1.0) buttonArea: Double) {
        prebidMediationBaseFullScreenAdUnit.setSkipButtonArea(buttonArea)
    }

    /**
     * Sets skip button position on the screen. Suitable values TOP_LEFT and TOP_RIGHT.
     * Default value TOP_RIGHT.
     */
    fun setSkipButtonPosition(skipButtonPosition: AudienzzPosition) {
        prebidMediationBaseFullScreenAdUnit.setSkipButtonPosition(skipButtonPosition.prebidPosition)
    }

    /**
     * Sets close button percentage size in range from 0.05 to 1.
     * If value less than 0.05, size will be default.
     */
    fun setCloseButtonArea(@FloatRange(from = 0.0, to = 1.0) closeButtonArea: Double) {
        prebidMediationBaseFullScreenAdUnit.setCloseButtonArea(closeButtonArea)
    }

    /**
     * Sets close button position on the screen. Suitable values TOP_LEFT and TOP_RIGHT.
     * Default value TOP_RIGHT.
     */
    fun setCloseButtonPosition(closeButtonPosition: AudienzzPosition?) {
        prebidMediationBaseFullScreenAdUnit.setCloseButtonPosition(
            closeButtonPosition?.prebidPosition,
        )
    }

    /**
     * Sets desired is muted property.
     */
    fun setIsMuted(isMuted: Boolean) {
        prebidMediationBaseFullScreenAdUnit.setIsMuted(isMuted)
    }

    /**
     * Makes sound button visible.
     */
    fun setIsSoundButtonVisible(isSoundButtonVisible: Boolean) {
        prebidMediationBaseFullScreenAdUnit.setIsSoundButtonVisible(isSoundButtonVisible)
    }
}
