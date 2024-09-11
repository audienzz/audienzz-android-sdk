package org.audienzz.mobile

import org.prebid.mobile.NativeTitle

data class AudienzzNativeTitle internal constructor(internal val nativeTitle: NativeTitle) {

    val title = nativeTitle.text

    constructor(text: String) : this(NativeTitle(text))
}
