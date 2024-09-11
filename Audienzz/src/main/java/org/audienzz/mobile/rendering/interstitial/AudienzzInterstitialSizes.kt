package org.audienzz.mobile.rendering.interstitial

import org.prebid.mobile.rendering.interstitial.InterstitialSizes

/**
 * Contains enums for InterstitialVideo sizes and methods to determine if video should be portrait,
 * landscape or aspect ratio
 */
class AudienzzInterstitialSizes {

    enum class AudienzzInterstitialSize(
        internal val prebidInterstitialSize: InterstitialSizes.InterstitialSize,
    ) {

        LANDSCAPE_480X320(InterstitialSizes.InterstitialSize.LANDSCAPE_480x320),
        LANDSCAPE_480X360(InterstitialSizes.InterstitialSize.LANDSCAPE_480x360),
        LANDSCAPE_768X1024(InterstitialSizes.InterstitialSize.LANDSCAPE_768x1024),
        LANDSCAPE_1024X768(InterstitialSizes.InterstitialSize.LANDSCAPE_1024x768),

        VERTICAL_270X480(InterstitialSizes.InterstitialSize.VERTICAL_270x480),
        VERTICAL_300X1050(InterstitialSizes.InterstitialSize.VERTICAL_300x1050),
        VERTICAL_320X480(InterstitialSizes.InterstitialSize.VERTICAL_320x480),
        VERTICAL_360X480(InterstitialSizes.InterstitialSize.VERTICAL_360x480),
        VERTICAL_360X540(InterstitialSizes.InterstitialSize.VERTICAL_360x540),
        VERTICAL_480X640(InterstitialSizes.InterstitialSize.VERTICAL_480x640),
        VERTICAL_576X1024(InterstitialSizes.InterstitialSize.VERTICAL_576x1024),
        VERTICAL_720X1280(InterstitialSizes.InterstitialSize.VERTICAL_720x1280),
        VERTICAL_768X1024(InterstitialSizes.InterstitialSize.VERTICAL_768x1024),
        VERTICAL_960X1280(InterstitialSizes.InterstitialSize.VERTICAL_960x1280),
        VERTICAL_1080X1920(InterstitialSizes.InterstitialSize.VERTICAL_1080x1920),
        VERTICAL_1440X1920(InterstitialSizes.InterstitialSize.VERTICAL_1440x1920),

        ASPECT_RATIO_300X200(InterstitialSizes.InterstitialSize.ASPECT_RATIO_300x200),
        ASPECT_RATIO_320X240(InterstitialSizes.InterstitialSize.ASPECT_RATIO_320x240),
        ASPECT_RATIO_400X225(InterstitialSizes.InterstitialSize.ASPECT_RATIO_400x225),
        ASPECT_RATIO_400X300(InterstitialSizes.InterstitialSize.ASPECT_RATIO_400x300),
        ASPECT_RATIO_480X270(InterstitialSizes.InterstitialSize.ASPECT_RATIO_480x270),
        ASPECT_RATIO_480X320(InterstitialSizes.InterstitialSize.ASPECT_RATIO_480x320),
        ASPECT_RATIO_640X360(InterstitialSizes.InterstitialSize.ASPECT_RATIO_640x360),
        ASPECT_RATIO_640X480(InterstitialSizes.InterstitialSize.ASPECT_RATIO_640x480),
        ASPECT_RATIO_1024X576(InterstitialSizes.InterstitialSize.ASPECT_RATIO_1024x576),
        ASPECT_RATIO_1280X720(InterstitialSizes.InterstitialSize.ASPECT_RATIO_1280x720),
        ASPECT_RATIO_1280X960(InterstitialSizes.InterstitialSize.ASPECT_RATIO_1280x960),
        ASPECT_RATIO_1920X800(InterstitialSizes.InterstitialSize.ASPECT_RATIO_1920x800),
        ASPECT_RATIO_1920X1080(InterstitialSizes.InterstitialSize.ASPECT_RATIO_1920x1080),
        ASPECT_RATIO_1920X1440(InterstitialSizes.InterstitialSize.ASPECT_RATIO_1920x1440), ;

        val size: String = prebidInterstitialSize.size
    }

    companion object {

        /**
         * @param size - String with video resolution
         * @return true if the given size is defined in Vertical enums
         */
        @JvmStatic
        fun isPortrait(size: String) = InterstitialSizes.isPortrait(size)

        /**
         *
         * @param size - String with video resolution
         * @return true if the given size is defined in Landscape enums
         */
        @JvmStatic
        fun isLandscape(size: String) = InterstitialSizes.isLandscape(size)
    }
}
