package org.audienzz.mobile

import org.prebid.mobile.RewardedVideoAdUnit

class AudienzzRewardedVideoAdUnit(
    configId: String,
) : AudienzzVideoBaseAdUnit(RewardedVideoAdUnit(configId))
