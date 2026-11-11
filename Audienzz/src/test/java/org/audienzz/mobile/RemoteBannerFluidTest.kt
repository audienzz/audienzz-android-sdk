package org.audienzz.mobile

import android.app.Activity
import android.view.ViewGroup
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.admanager.AdManagerAdView
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockkConstructor
import io.mockk.unmockkAll
import kotlinx.serialization.json.Json
import org.audienzz.mobile.api.config.RemoteAdUnitConfig
import org.audienzz.mobile.original.AudienzzAdViewHandler
import org.audienzz.mobile.util.sizesJson
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.prebid.mobile.BannerAdUnit
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

/**
 * `gamConfig.includeFluid` adds GAM's fluid size next to the slot's fixed sizes (e.g. for fluid
 * GAM native styles), while Prebid keeps bidding on its fixed sizes only.
 */
@RunWith(RobolectricTestRunner::class)
class RemoteBannerFluidTest {

    private lateinit var activity: Activity
    private lateinit var banner: AudienzzRemoteBannerView

    @Before fun setup() {
        activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        banner = AudienzzRemoteBannerView(activity, "fluid")
        activity.setContentView(banner)
        mockkConstructor(AudienzzAdViewHandler::class)
        every { anyConstructed<AudienzzAdViewHandler>().load(any(), any(), any(), any()) } just Runs
        every { anyConstructed<AudienzzAdViewHandler>().enableSmartRefresh() } just Runs
    }

    @After fun cleanup() {
        banner.destroy()
        unmockkAll()
    }

    private fun parse(
        includeFluid: String?,
        prebidSizes: String = "\"300x250\"",
    ): RemoteAdUnitConfig {
        val field = includeFluid?.let { ""","includeFluid":$it""" }.orEmpty()
        val json = """{"id":9,"config":{"adType":"banner","lazyLoad":false},""" +
            """"gamConfig":{"adUnitPath":"/1/unit","adSizes":["300x250","320x50"]$field},""" +
            """"prebidConfig":{"placementId":"p","adSizes":[$prebidSizes]}}"""
        return Json.decodeFromString(RemoteAdUnitConfig.serializer(), json)
    }

    private fun create(config: RemoteAdUnitConfig): AdManagerAdView {
        AudienzzRemoteBannerView::class.java
            .getDeclaredMethod("createAdFromConfig", RemoteAdUnitConfig::class.java)
            .apply { isAccessible = true }
            .invoke(banner, config)
        return banner.getChildAt(0) as AdManagerAdView
    }

    private fun prebidSizes(): List<String> {
        val unit = AudienzzRemoteBannerView::class.java.getDeclaredField("adUnit")
            .apply { isAccessible = true }
            .get(banner) as AudienzzBannerAdUnit
        val prebid = AudienzzBannerAdUnit::class.java.getDeclaredField("adUnit")
            .apply { isAccessible = true }
            .get(unit) as BannerAdUnit
        return prebid.configuration.sizes.map { "${it.width}x${it.height}" }
    }

    // --- Config parsing ---

    @Test fun `absent is off`() {
        assertFalse(parse(null).gamConfig.includeFluid)
    }

    @Test fun `true is on`() {
        assertTrue(parse("true").gamConfig.includeFluid)
    }

    @Test fun `malformed values are off and the rest of the config still parses`() {
        listOf("false", "\"true\"", "1", "null", "{}", "[]").forEach { raw ->
            val config = parse(raw)
            assertFalse(raw, config.gamConfig.includeFluid)
            assertEquals(raw, "/1/unit", config.gamConfig.adUnitPath)
        }
    }

    // --- GAM request ---

    @Test fun `off - GAM sizes and layout unchanged`() {
        val google = create(parse(null))
        assertEquals(listOf(AdSize(300, 250), AdSize(320, 50)), google.adSizes!!.toList())
        assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, google.layoutParams.width)
    }

    @Test fun `on - fluid follows the fixed sizes and the GAM view spans the host`() {
        val google = create(parse("true"))
        assertEquals(
            listOf(AdSize(300, 250), AdSize(320, 50), AdSize.FLUID),
            google.adSizes!!.toList(),
        )
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, google.layoutParams.width)
    }

    @Test fun `on - Prebid gets no fluid size`() {
        create(parse("true", prebidSizes = "\"300x250\",\"320x50\""))
        val sizes = prebidSizes()
        assertTrue(sizes.isNotEmpty())
        assertTrue("Prebid sizes must stay fixed: $sizes", sizes.none { it.startsWith("-") })
    }

    @Test fun `on - a GAM-only slot also requests fluid`() {
        val google = create(parse("true", prebidSizes = ""))
        assertTrue(AdSize.FLUID in google.adSizes!!.toList())
    }

    @Test fun `fluid is reported as fluid, not as its negative sentinels`() {
        assertEquals("[\"300x250\",\"fluid\"]", listOf(AdSize(300, 250), AdSize.FLUID).sizesJson)
    }
}
