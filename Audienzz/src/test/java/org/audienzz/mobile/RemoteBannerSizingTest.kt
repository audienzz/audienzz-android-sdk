package org.audienzz.mobile

import android.app.Activity
import android.view.View
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.admanager.AdManagerAdView
import io.mockk.*
import org.audienzz.mobile.api.config.*
import org.audienzz.mobile.original.AudienzzAdViewHandler
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "xxhdpi")
class RemoteBannerSizingTest {
    private lateinit var activity: Activity
    private lateinit var banner: AudienzzRemoteBannerView

    @Before fun setup() {
        activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        banner = AudienzzRemoteBannerView(activity, "48")
        activity.setContentView(banner)
        banner.measure(exact(1080), exact(750))
        banner.layout(0, 0, 1080, 750)
        mockkConstructor(AudienzzAdViewHandler::class)
        every { anyConstructed<AudienzzAdViewHandler>().load(any(), any(), any(), any()) } just Runs
        every { anyConstructed<AudienzzAdViewHandler>().enableSmartRefresh() } just Runs
    }

    @After fun cleanup() { banner.destroy(); unmockkAll() }

    private fun exact(px: Int) = View.MeasureSpec.makeMeasureSpec(px, View.MeasureSpec.EXACTLY)

    private fun create(strategy: String, type: String? = "INLINE", customWidth: Int? = 320): AdManagerAdView {
        // Decode the actual backend shape, including uppercase enum values, then drive the same
        // construction path as the config-fetch callback. Only networking/auctions are stubbed.
        val json = """{
          "id":48,"config":{"adType":"banner","lazyLoad":true,"prefetchDistanceDp":0},
          "gamConfig":{"adUnitPath":"/test/banner","adSizes":["300x250","320x50"],
            "adaptiveBannerConfig":{"enabled":true,"type":${type?.let { "\"$it\"" } ?: "null"},
              "widthStrategy":"$strategy","customWidth":$customWidth}},
          "prebidConfig":{"placementId":"test","adSizes":["300x250","320x50"]}}
        """
        val config = kotlinx.serialization.json.Json.decodeFromString(RemoteAdUnitConfig.serializer(), json)
        val method = AudienzzRemoteBannerView::class.java.getDeclaredMethod("createAdFromConfig", RemoteAdUnitConfig::class.java)
        method.isAccessible = true
        method.invoke(banner, config)
        return banner.getChildAt(0) as AdManagerAdView
    }

    @Test fun `backend CUSTOM 320 is 320 dp on a 3x density device`() {
        assertEquals(3f, activity.resources.displayMetrics.density)
        val google = create("CUSTOM")
        assertEquals(320, google.adSizes!!.first().width)
        assertEquals(960, google.adSizes!!.first().getWidthInPixels(activity))
    }

    @Test fun `legacy lowercase custom width is also already in dp`() {
        assertEquals(320, create("custom").adSizes!!.first().width)
    }

    @Test fun `INLINE without maxHeight creates an inline request and a measurable lazy placeholder`() {
        val google = create("CUSTOM")
        assertEquals("Google inline request descriptors have zero height", 0, google.adSizes!!.first().height)
        banner.measure(exact(1080), exact(750))
        banner.layout(0, 0, 1080, 750)
        assertTrue("the lazy gate needs nonzero geometry even with a zero-height descriptor", google.height > 0)
    }

    @Test fun `FULL_WIDTH uses the available container width in dp`() {
        assertEquals(360, create("FULL_WIDTH").adSizes!!.first().width)
    }

    @Test fun `inline placeholder does not keep a short loaded creative artificially tall`() {
        val google = create("CUSTOM")
        assertTrue(google.minimumHeight > 0)
        // Model Google's selected size, then invoke the actual installed load delegate.
        google.setAdSizes(AdSize(320, 50))
        google.adListener!!.onAdLoaded()
        banner.measure(exact(1080), exact(750))
        banner.layout(0, 0, 1080, 750)
        assertEquals(0, google.minimumHeight)
        assertEquals(150, google.height)
    }

    @Test fun `explicit ANCHORED still creates an anchored descriptor`() {
        assertTrue(create("CUSTOM", type = "ANCHORED").adSizes!!.first().height > 0)
    }
}
