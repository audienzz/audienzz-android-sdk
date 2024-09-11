package org.audienzz.mobile.event.util

import org.audienzz.mobile.api.data.AudienzzAdUnitFormat
import org.audienzz.mobile.event.entity.AdSubtype
import org.prebid.mobile.api.data.AdFormat

internal val Iterable<AdFormat>?.adSubtype: AdSubtype
    get() = when {
        this?.singleOrNull() == AdFormat.BANNER -> AdSubtype.HTML
        this?.singleOrNull() == AdFormat.VAST -> AdSubtype.VIDEO
        else -> AdSubtype.MULTIFORMAT
    }

internal val Iterable<AudienzzAdUnitFormat>?.adSubtypeFromAudienzz: AdSubtype
    get() = when {
        this?.singleOrNull() == AudienzzAdUnitFormat.BANNER -> AdSubtype.HTML
        this?.singleOrNull() == AudienzzAdUnitFormat.VIDEO -> AdSubtype.VIDEO
        else -> AdSubtype.MULTIFORMAT
    }
