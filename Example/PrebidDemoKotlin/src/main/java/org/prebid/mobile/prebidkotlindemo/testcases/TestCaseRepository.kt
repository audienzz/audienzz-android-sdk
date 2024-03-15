/**
 * This file was mentioned as modified in the NOTICE file by Apache 2.0 License.
 * <p>
 * Changelog:
 * - Removed all inapp, admob and applovin test cases
 * - Added test cases for lazy rendering and original banners
 */

package org.prebid.mobile.prebidkotlindemo.testcases

import org.prebid.mobile.prebidkotlindemo.R
import org.prebid.mobile.prebidkotlindemo.activities.ads.gam.original.*
import org.prebid.mobile.prebidkotlindemo.activities.ads.gam.rendering.*

object TestCaseRepository {

    lateinit var lastTestCase: TestCase

    fun getList() = arrayListOf(
        /* GAM Original API */
        TestCase(
            R.string.gam_original_display_banner_320x50,
            AdFormat.DISPLAY_BANNER,
            IntegrationKind.GAM_ORIGINAL,
            GamOriginalApiDisplayBanner320x50Activity::class.java,
        ),
        TestCase(
            R.string.gam_original_display_banner_320x50_lazy,
            AdFormat.DISPLAY_BANNER,
            IntegrationKind.GAM_ORIGINAL,
            GamOriginalApiDisplayBanner320x50LazyActivity::class.java,
        ),
        TestCase(
            R.string.gam_original_display_banner_300x250,
            AdFormat.DISPLAY_BANNER,
            IntegrationKind.GAM_ORIGINAL,
            GamOriginalApiDisplayBanner300x250Activity::class.java,
        ),
        TestCase(
            R.string.gam_original_display_banner_multi_size,
            AdFormat.DISPLAY_BANNER,
            IntegrationKind.GAM_ORIGINAL,
            GamOriginalApiDisplayBannerMultiSizeActivity::class.java,
        ),
        TestCase(
            R.string.gam_original_video_banner,
            AdFormat.VIDEO_BANNER,
            IntegrationKind.GAM_ORIGINAL,
            GamOriginalApiVideoBannerActivity::class.java,
        ),
        TestCase(
            R.string.gam_original_multiformat_banner,
            AdFormat.MULTIFORMAT,
            IntegrationKind.GAM_ORIGINAL,
            GamOriginalApiMultiformatBannerActivity::class.java,
        ),
        TestCase(
            R.string.gam_original_multiformat_banner_video_native_in_app,
            AdFormat.MULTIFORMAT,
            IntegrationKind.GAM_ORIGINAL,
            GamOriginalApiMultiformatBannerVideoNativeInAppActivity::class.java,
        ),
        TestCase(
            R.string.gam_original_multiformat_banner_video_native_styles,
            AdFormat.MULTIFORMAT,
            IntegrationKind.GAM_ORIGINAL,
            GamOriginalApiMultiformatBannerVideoNativeStylesActivity::class.java,
        ),
        TestCase(
            R.string.gam_original_display_interstitial,
            AdFormat.DISPLAY_INTERSTITIAL,
            IntegrationKind.GAM_ORIGINAL,
            GamOriginalApiDisplayInterstitialActivity::class.java,
        ),
        TestCase(
            R.string.gam_original_video_interstitial,
            AdFormat.VIDEO_INTERSTITIAL,
            IntegrationKind.GAM_ORIGINAL,
            GamOriginalApiVideoInterstitialActivity::class.java,
        ),
        TestCase(
            R.string.gam_original_multiformat_interstitial,
            AdFormat.MULTIFORMAT,
            IntegrationKind.GAM_ORIGINAL,
            GamOriginalApiMultiformatInterstitialActivity::class.java,
        ),
        TestCase(
            R.string.gam_original_video_rewarded,
            AdFormat.VIDEO_REWARDED,
            IntegrationKind.GAM_ORIGINAL,
            GamOriginalApiVideoRewardedActivity::class.java,
        ),
        TestCase(
            R.string.gam_original_video_in_stream,
            AdFormat.IN_STREAM_VIDEO,
            IntegrationKind.GAM_ORIGINAL,
            GamOriginalApiInStreamActivity::class.java,
        ),
        TestCase(
            R.string.gam_original_native_in_app,
            AdFormat.NATIVE,
            IntegrationKind.GAM_ORIGINAL,
            GamOriginalApiNativeInAppActivity::class.java,
        ),
        TestCase(
            R.string.gam_original_native_styles,
            AdFormat.NATIVE,
            IntegrationKind.GAM_ORIGINAL,
            GamOriginalApiNativeStylesActivity::class.java,
        ),

        /* GAM Rendering API */
        TestCase(
            R.string.gam_rendering_display_banner_320x50,
            AdFormat.DISPLAY_BANNER,
            IntegrationKind.GAM_RENDERING,
            GamRenderingApiDisplayBanner320x50Activity::class.java,
        ),
        TestCase(
            R.string.gam_rendering_display_banner_320x50_lazy,
            AdFormat.DISPLAY_BANNER,
            IntegrationKind.GAM_RENDERING,
            GamRenderingApiDisplayBanner320x50LazyActivity::class.java,
        ),
        TestCase(
            R.string.gam_rendering_video_banner,
            AdFormat.VIDEO_BANNER,
            IntegrationKind.GAM_RENDERING,
            GamRenderingApiVideoBannerActivity::class.java,
        ),
        TestCase(
            R.string.gam_rendering_display_interstitial,
            AdFormat.DISPLAY_INTERSTITIAL,
            IntegrationKind.GAM_RENDERING,
            GamRenderingApiDisplayInterstitialActivity::class.java,
        ),
        TestCase(
            R.string.gam_rendering_video_interstitial,
            AdFormat.VIDEO_INTERSTITIAL,
            IntegrationKind.GAM_RENDERING,
            GamRenderingApiVideoInterstitialActivity::class.java,
        ),
        TestCase(
            R.string.gam_rendering_video_rewarded,
            AdFormat.VIDEO_REWARDED,
            IntegrationKind.GAM_RENDERING,
            GamRenderingApiVideoRewardedActivity::class.java,
        ),
        TestCase(
            R.string.gam_rendering_native,
            AdFormat.NATIVE,
            IntegrationKind.GAM_RENDERING,
            GamRenderingApiNativeActivity::class.java,
        ),
    )

}