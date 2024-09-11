package org.audienzz.mobile

import org.prebid.mobile.NativeData

data class AudienzzNativeData internal constructor(internal val nativeData: NativeData) {

    val typeNumber = nativeData.typeNumber
    val value = nativeData.value
    val type = Type.getFromNativeDataType(nativeData.type)

    constructor(typeNumber: Int, value: String) : this(NativeData(typeNumber, value))

    constructor(type: Type, value: String) : this(NativeData(type.nativeDataType, value)) {
        require(type != Type.CUSTOM)
    }

    enum class Type(internal val nativeDataType: NativeData.Type) {
        SPONSORED_BY(NativeData.Type.SPONSORED_BY),
        DESCRIPTION(NativeData.Type.DESCRIPTION),
        CALL_TO_ACTION(NativeData.Type.CALL_TO_ACTION),
        RATING(NativeData.Type.RATING),
        CUSTOM(NativeData.Type.CUSTOM), ;

        companion object {

            fun getFromNativeDataType(nativeDataType: NativeData.Type) =
                values().find { it.nativeDataType == nativeDataType } ?: CUSTOM
        }
    }
}
