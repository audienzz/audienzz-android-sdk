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
import org.audienzz.mobile.original.mediaTypesJson
import org.audienzz.mobile.util.sizesJson
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.prebid.mobile.BannerAdUnit
import org.prebid.mobile.api.data.AdFormat
import org.prebid.mobile.rendering.networking.parameters.AdRequestInput
import org.prebid.mobile.rendering.networking.parameters.BasicParameterBuilder
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import java.util.EnumSet

/**
 * Native in a banner slot, styled in GAM: the slot stays a GAM banner view and the ad config's
 * `nativeConfig` only adds native to the Prebid auction (and optionally GAM's fluid size).
 */
@RunWith(RobolectricTestRunner::class)
class NativeInBannerTest {

    private lateinit var activity: Activity
    private lateinit var banner: AudienzzRemoteBannerView

    @Before fun setup() {
        activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        banner = AudienzzRemoteBannerView(activity, "native")
        activity.setContentView(banner)
        mockkConstructor(AudienzzAdViewHandler::class)
        every { anyConstructed<AudienzzAdViewHandler>().load(any(), any(), any(), any()) } just Runs
        every { anyConstructed<AudienzzAdViewHandler>().enableSmartRefresh() } just Runs
    }

    @After fun cleanup() {
        banner.destroy()
        unmockkAll()
    }

    // --- The bid request Prebid actually sends ---

    private fun bidRequest(adUnit: BannerAdUnit): JSONObject {
        val input = AdRequestInput()
        BasicParameterBuilder(adUnit.configuration, activity.resources, false)
            .appendBuilderParameters(input)
        return input.bidRequest.getJsonObject()
    }

    private fun bidRequestImp(adUnit: BannerAdUnit): JSONObject =
        bidRequest(adUnit).getJSONArray("imp").getJSONObject(0)

    /** Prebid Server adds `hb_format` to the GAM keys only when asked to; GAM line items need it. */
    private fun includesFormat(adUnit: BannerAdUnit): Boolean =
        bidRequest(adUnit).optJSONObject("ext")?.optJSONObject("prebid")
            ?.optJSONObject("targeting")
            ?.optString("includeformat") == "true"

    @Test fun `native enabled - one impression carries both banner and native`() {
        val prebid = BannerAdUnit("placement", 300, 250)
        AudienzzBannerAdUnit(prebid).enableNativeFormat()

        val imp = bidRequestImp(prebid)
        assertTrue("banner must still be bid on", imp.has("banner"))
        assertTrue("native must be added to the same impression", imp.has("native"))

        val native = JSONObject(imp.getJSONObject("native").getString("request"))
        val assets = native.getJSONArray("assets")
        assertEquals(6, assets.length())
        val required = (0 until assets.length())
            .count { assets.getJSONObject(it).optInt("required") == 1 }
        assertEquals("title, main image and sponsored-by only", 3, required)
        assertEquals(1, native.getJSONArray("eventtrackers").length())
        assertTrue("GAM must be able to tell a native win from a banner win", includesFormat(prebid))
    }

    @Test fun `control - a plain banner sends no native`() {
        val prebid = BannerAdUnit("placement", 300, 250)
        AudienzzBannerAdUnit(prebid)

        val imp = bidRequestImp(prebid)
        assertTrue(imp.has("banner"))
        assertFalse(imp.has("native"))
        assertFalse(includesFormat(prebid))
    }

    @Test fun `custom native parameters replace the defaults`() {
        val prebid = BannerAdUnit("placement", 300, 250)
        val title = AudienzzNativeTitleAsset().apply {
            len = 25
            isRequired = true
        }
        AudienzzBannerAdUnit(prebid).enableNativeFormat(AudienzzNativeParameters(listOf(title)))

        val native = JSONObject(bidRequestImp(prebid).getJSONObject("native").getString("request"))
        assertEquals(1, native.getJSONArray("assets").length())
    }

    // --- Remote banner view wiring ---

    private fun create(nativeConfig: String?, prebidSizes: String = "\"300x250\""): AdManagerAdView {
        val field = nativeConfig?.let { ""","nativeConfig":$it""" }.orEmpty()
        val json = """{"id":9,"config":{"adType":"banner","lazyLoad":false$field},""" +
            """"gamConfig":{"adUnitPath":"/1/unit","adSizes":["300x250","320x50"]},""" +
            """"prebidConfig":{"placementId":"p","adSizes":[$prebidSizes]}}"""
        val config = Json.decodeFromString(RemoteAdUnitConfig.serializer(), json)
        AudienzzRemoteBannerView::class.java
            .getDeclaredMethod("createAdFromConfig", RemoteAdUnitConfig::class.java)
            .apply { isAccessible = true }
            .invoke(banner, config)
        return banner.getChildAt(0) as AdManagerAdView
    }

    private fun adUnit(): AudienzzBannerAdUnit? =
        AudienzzRemoteBannerView::class.java.getDeclaredField("adUnit")
            .apply { isAccessible = true }
            .get(banner) as AudienzzBannerAdUnit?

    @Test fun `no nativeConfig - banner only, sizes and layout unchanged`() {
        val google = create(null)
        assertFalse(adUnit()!!.isNativeFormatEnabled)
        assertEquals(listOf(AdSize(300, 250), AdSize(320, 50)), google.adSizes!!.toList())
        assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, google.layoutParams.width)
    }

    @Test fun `native enabled - auction adds native, GAM request unchanged`() {
        val google = create("""{"enabled":true}""")
        assertTrue(adUnit()!!.isNativeFormatEnabled)
        assertEquals(listOf(AdSize(300, 250), AdSize(320, 50)), google.adSizes!!.toList())
        assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, google.layoutParams.width)
    }

    @Test fun `native fluid - GAM also gets the fluid size and the view spans the host`() {
        val google = create("""{"enabled":true,"fluid":true}""")
        assertTrue(adUnit()!!.isNativeFormatEnabled)
        val sizes = google.adSizes!!.toList()
        assertEquals("the slot's own primary size stays first", AdSize(300, 250), sizes.first())
        assertTrue(AdSize.FLUID in sizes)
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, google.layoutParams.width)
    }

    @Test fun `GAM-only slot - no auction, so no native format`() {
        create("""{"enabled":true}""", prebidSizes = "")
        assertFalse(adUnit()!!.isNativeFormatEnabled)
    }

    // --- Analytics ---

    @Test fun `media types follow the formats the auction asks for`() {
        assertEquals("[\"banner\"]", mediaTypesJson(EnumSet.of(AdFormat.BANNER)))
        assertEquals(
            "[\"banner\",\"video\"]",
            mediaTypesJson(EnumSet.of(AdFormat.BANNER, AdFormat.VAST)),
        )
        assertEquals("[\"video\"]", mediaTypesJson(EnumSet.of(AdFormat.VAST)))
        assertEquals(
            "[\"banner\",\"native\"]",
            mediaTypesJson(EnumSet.of(AdFormat.BANNER, AdFormat.NATIVE)),
        )
        assertEquals("[\"banner\"]", mediaTypesJson(null as Iterable<AdFormat>?))
    }

    @Test fun `fluid is reported as fluid, not as its negative sentinels`() {
        assertEquals("[\"300x250\",\"fluid\"]", listOf(AdSize(300, 250), AdSize.FLUID).sizesJson)
    }
}
