package org.audienzz.mobile.rendering.bidding.display

import org.audienzz.mobile.rendering.bidding.data.bid.AudienzzBidResponse

/**
 * PrebidMediationDelegate is a delegate of custom mediation platform.
 */
interface AudienzzPrebidMediationDelegate {

    /**
     * Sets keywords into a given mediation ad object
     */
    fun handleKeywordsUpdate(keywords: Map<String, String>?)

    /**
     * Sets response into a given mediation ad object
     */
    fun setResponsetoLocalExtras(response: AudienzzBidResponse?)

    /**
     * Checks if banner view is visible, and it is possible to make refresh.
     */
    fun canPerformRefresh(): Boolean
}
