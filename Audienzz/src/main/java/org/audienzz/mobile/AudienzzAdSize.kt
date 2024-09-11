package org.audienzz.mobile

import org.prebid.mobile.AdSize

data class AudienzzAdSize(
    internal val adSize: AdSize,
) {

    val width: Int = adSize.width
    val height: Int = adSize.height

    constructor(width: Int, height: Int) : this(AdSize(width, height))
}
