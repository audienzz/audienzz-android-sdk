package org.audienzz.mobile

import org.json.JSONObject
import org.prebid.mobile.rendering.interstitial.rewarded.Reward

data class AudienzzReward(
    val type: String,
    val count: Int,
    val ext: JSONObject? = null,
) {

    private val reward = Reward(type, count, ext)

    override fun toString(): String =
        reward.toString()

    override fun equals(other: Any?): Boolean =
        reward == (other as? AudienzzReward)?.reward

    override fun hashCode(): Int =
        reward.hashCode()

    companion object {
        fun fromPrebidReward(reward: Reward): AudienzzReward =
            AudienzzReward(reward.type, reward.count, reward.ext)
    }
}
