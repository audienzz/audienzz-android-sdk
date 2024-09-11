package org.audienzz.mobile

import org.json.JSONObject
import org.prebid.mobile.NativeTitleAsset

class AudienzzNativeTitleAsset internal constructor(
    private val prebidNativeTitleAsset: NativeTitleAsset,
) : AudienzzNativeAsset(prebidNativeTitleAsset) {

    var len: Int
        get() = prebidNativeTitleAsset.len
        set(value) {
            prebidNativeTitleAsset.setLength(value)
        }

    var isRequired: Boolean
        get() = prebidNativeTitleAsset.isRequired
        set(value) {
            prebidNativeTitleAsset.isRequired = value
        }

    var titleExt: Any?
        get() = prebidNativeTitleAsset.titleExt
        set(value) {
            prebidNativeTitleAsset.titleExt = value
        }

    var assetExt: Any?
        get() = prebidNativeTitleAsset.assetExt
        set(value) {
            prebidNativeTitleAsset.assetExt = value
        }

    constructor() : this(NativeTitleAsset())

    override fun getJsonObject(idCount: Int): JSONObject =
        prebidNativeAsset.getJsonObject(idCount)
}
