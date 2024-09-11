package org.audienzz.mobile.api.rendering.listeners

import org.audienzz.mobile.api.exceptions.AudienzzAdException
import org.audienzz.mobile.api.rendering.AudienzzRewardedAdUnit

/**
 * Listener interface representing RewardedAdUnit events.
 * All methods will be invoked on the main thread.
 */
interface AudienzzRewardedAdUnitListener {

    /**
     * Executed when the ad is loaded and is ready for display.
     *
     * @param rewardedAdUnit view of the corresponding event. Contains reward instance inside.
     * Prebid reward is always null.
     */
    fun onAdLoaded(rewardedAdUnit: AudienzzRewardedAdUnit?) {}

    /**
     * Executed when the ad is displayed on screen.
     *
     * @param rewardedAdUnit view of the corresponding event.
     */
    fun onAdDisplayed(rewardedAdUnit: AudienzzRewardedAdUnit?) {}

    /**
     * Executed when an error is encountered on initialization / loading or display step.
     *
     * @param rewardedAdUnit view of the corresponding event.
     * @param exception  exception containing detailed message and error type.
     */
    fun onAdFailed(rewardedAdUnit: AudienzzRewardedAdUnit?, exception: AudienzzAdException?) {}

    /**
     * Executed when rewardedAdUnit is clicked.
     *
     * @param rewardedAdUnit view of the corresponding event.
     */
    fun onAdClicked(rewardedAdUnit: AudienzzRewardedAdUnit?) {}

    /**
     * Executed when rewardedAdUnit is closed.
     *
     * @param rewardedAdUnit view of the corresponding event.
     */
    fun onAdClosed(rewardedAdUnit: AudienzzRewardedAdUnit?) {}

    /**
     * Executed when user receives reward.
     *
     * @param rewardedAdUnit view of the corresponding event. Contains reward instance inside.
     * Prebid reward is always null.
     */
    fun onUserEarnedReward(rewardedAdUnit: AudienzzRewardedAdUnit?) {}
}
