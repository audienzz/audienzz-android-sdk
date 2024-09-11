package org.audienzz.mobile

import org.json.JSONObject
import org.prebid.mobile.NativeImageAsset

class AudienzzNativeImageAsset internal constructor(
    private val prebidNativeImageAsset: NativeImageAsset,
) : AudienzzNativeAsset(prebidNativeImageAsset) {

    var imageType: ImageType?
        get() = prebidNativeImageAsset.imageType?.let { ImageType.fromPrebidImageType(it) }
        set(value) {
            prebidNativeImageAsset.imageType = value?.prebidImageType
        }

    var wMin: Int
        get() = prebidNativeImageAsset.wMin
        set(value) {
            prebidNativeImageAsset.wMin = value
        }

    var hMin: Int
        get() = prebidNativeImageAsset.hMin
        set(value) {
            prebidNativeImageAsset.hMin = value
        }

    var w: Int
        get() = prebidNativeImageAsset.w
        set(value) {
            prebidNativeImageAsset.w = value
        }

    var h: Int
        get() = prebidNativeImageAsset.h
        set(value) {
            prebidNativeImageAsset.h = value
        }

    var isRequired: Boolean
        get() = prebidNativeImageAsset.isRequired
        set(value) {
            prebidNativeImageAsset.isRequired = value
        }

    var assetExt: Any?
        get() = prebidNativeImageAsset.assetExt
        set(value) {
            prebidNativeImageAsset.assetExt = value
        }

    var imageExt: Any?
        get() = prebidNativeImageAsset.imageExt
        set(value) {
            prebidNativeImageAsset.imageExt = value
        }

    constructor(w: Int, h: Int, minWidth: Int, minHeight: Int) : this(
        NativeImageAsset(w, h, minWidth, minHeight),
    )

    constructor(w: Int, h: Int) : this(NativeImageAsset(w, h))

    fun addMime(mime: String) {
        prebidNativeImageAsset.addMime(mime)
    }

    fun getMimes(): List<String> = prebidNativeImageAsset.mimes

    override fun getJsonObject(idCount: Int): JSONObject =
        prebidNativeAsset.getJsonObject(idCount)

    enum class ImageType(internal val prebidImageType: NativeImageAsset.IMAGE_TYPE) {
        ICON(NativeImageAsset.IMAGE_TYPE.ICON),
        MAIN(NativeImageAsset.IMAGE_TYPE.MAIN),
        CUSTOM(NativeImageAsset.IMAGE_TYPE.CUSTOM), ;

        var id: Int
            get() = prebidImageType.id
            set(value) {
                prebidImageType.id = value
            }

        companion object {

            @JvmStatic
            fun fromPrebidImageType(imageAsset: NativeImageAsset.IMAGE_TYPE) =
                values().find { it.prebidImageType == imageAsset } ?: ICON
        }
    }
}
