package org.audienzz.mobile

import org.json.JSONObject
import org.prebid.mobile.NativeAsset
import org.prebid.mobile.NativeDataAsset
import org.prebid.mobile.NativeImageAsset
import org.prebid.mobile.NativeTitleAsset

abstract class AudienzzNativeAsset internal constructor(
    internal val prebidNativeAsset: NativeAsset,
) {

    abstract fun getJsonObject(idCount: Int): JSONObject

    companion object {

        @JvmStatic
        fun fromPrebidNativeAsset(nativeAsset: NativeAsset): AudienzzNativeAsset =
            when (nativeAsset) {
                is NativeDataAsset -> AudienzzNativeDataAsset(nativeAsset)
                is NativeImageAsset -> AudienzzNativeImageAsset(nativeAsset)
                is NativeTitleAsset -> AudienzzNativeTitleAsset(nativeAsset)
                else -> throw IllegalArgumentException("Unknown subclass")
            }
    }
}
