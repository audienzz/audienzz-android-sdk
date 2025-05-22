package org.audienzz.mobile

import org.prebid.mobile.NativeImage

data class AudienzzNativeImage internal constructor(internal val nativeImage: NativeImage) {

    val typeNumber = nativeImage.typeNumber
    val url = nativeImage.url
    val type = Type.getTypeFromNumber(typeNumber)

    constructor(typeNumber: Int, url: String) : this(NativeImage(typeNumber, url))

    constructor(type: Type, url: String) : this(type.typeNumber, url) {
        throw IllegalArgumentException("For CUSTOM type use constructor with typeNumber parameter.")
    }

    enum class Type(val typeNumber: Int) {
        ICON(1),
        MAIN_IMAGE(3),
        CUSTOM(0), ;

        companion object {

            fun getTypeFromNumber(typeNumber: Int) =
                Type.entries.find { it.typeNumber == typeNumber } ?: CUSTOM
        }
    }
}
