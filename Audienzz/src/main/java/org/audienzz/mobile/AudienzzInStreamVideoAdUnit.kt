package org.audienzz.mobile

import org.prebid.mobile.InStreamVideoAdUnit

class AudienzzInStreamVideoAdUnit(
    adUnit: InStreamVideoAdUnit,
) : AudienzzVideoBaseAdUnit(adUnit) {

    constructor(configId: String, width: Int, height: Int) : this(
        InStreamVideoAdUnit(
            configId,
            width,
            height,
        ),
    )
}
