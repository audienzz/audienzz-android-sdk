package org.audienzz.mobile

import org.json.JSONObject
import org.prebid.mobile.NativeDataAsset

class AudienzzNativeDataAsset internal constructor(
    private val prebidNativeDataAsset: NativeDataAsset,
) : AudienzzNativeAsset(prebidNativeDataAsset) {

    var dataType: DataType?
        get() = DataType.fromPrebidDataType(prebidNativeDataAsset.dataType)
        set(value) {
            prebidNativeDataAsset.dataType = value?.prebidDataType
        }

    var len: Int
        get() = prebidNativeDataAsset.len
        set(value) {
            prebidNativeDataAsset.len = value
        }

    var isRequired: Boolean
        get() = prebidNativeDataAsset.isRequired
        set(value) {
            prebidNativeDataAsset.isRequired = value
        }

    var dataExt: Any?
        get() = prebidNativeDataAsset.dataExt
        set(value) {
            prebidNativeDataAsset.dataExt = value
        }

    var assetExt: Any?
        get() = prebidNativeDataAsset.assetExt
        set(value) {
            prebidNativeDataAsset.assetExt = value
        }

    constructor() : this(NativeDataAsset())

    override fun getJsonObject(idCount: Int): JSONObject =
        prebidNativeAsset.getJsonObject(idCount)

    enum class DataType(internal val prebidDataType: NativeDataAsset.DATA_TYPE) {
        SPONSORED(NativeDataAsset.DATA_TYPE.SPONSORED),
        DESC(NativeDataAsset.DATA_TYPE.DESC),
        RATING(NativeDataAsset.DATA_TYPE.RATING),
        LIKES(NativeDataAsset.DATA_TYPE.LIKES),
        DOWNLOADS(NativeDataAsset.DATA_TYPE.DOWNLOADS),
        PRICE(NativeDataAsset.DATA_TYPE.PRICE),
        SALEPRICE(NativeDataAsset.DATA_TYPE.SALEPRICE),
        PHONE(NativeDataAsset.DATA_TYPE.PHONE),
        ADDRESS(NativeDataAsset.DATA_TYPE.ADDRESS),
        DESC2(NativeDataAsset.DATA_TYPE.DESC2),
        DESPLAYURL(NativeDataAsset.DATA_TYPE.DESPLAYURL),
        CTATEXT(NativeDataAsset.DATA_TYPE.CTATEXT),
        CUSTOM(NativeDataAsset.DATA_TYPE.CUSTOM), ;

        var id: Int
            get() = prebidDataType.id
            set(value) {
                prebidDataType.id = value
            }

        companion object {

            @JvmStatic
            fun fromPrebidDataType(dataType: NativeDataAsset.DATA_TYPE) =
                DataType.entries.find { it.prebidDataType == dataType } ?: SPONSORED
        }
    }
}
