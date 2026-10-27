package org.audienzz.mobile.original

import android.app.Activity
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.admanager.AdManagerAdRequest
import com.google.android.gms.ads.admanager.AdManagerAdView
import com.google.android.gms.ads.admanager.AppEventListener
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.spyk
import org.audienzz.mobile.AudienzzAdUnit
import org.audienzz.mobile.AudienzzPrebidMobile
import org.audienzz.mobile.AudienzzRemoteBannerView
import org.audienzz.mobile.AudienzzResultCode
import org.audienzz.mobile.api.config.RemoteAdUnitConfig
import org.audienzz.mobile.screen.ScreenAdCoordinator
import org.audienzz.mobile.screen.screenAdCoordinatorOverride
import org.audienzz.mobile.util.AppForegroundMonitor
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

/**
 * The handler drives [PrebidCreativeSizer] from the real GAM callbacks it installs: hb_size from
 * the auction, GAM's `Prebid` app event, onAdLoaded. GAM's view is real so its sizes can be read
 * back; only its network load is stubbed.
 */
@RunWith(RobolectricTestRunner::class)
class PrebidCreativeResizeTest {

    private lateinit var adView: AdManagerAdView
    private lateinit var adUnit: AudienzzAdUnit
    private lateinit var handler: AudienzzAdViewHandler
    private val responses = mutableListOf<(AudienzzResultCode?) -> Unit>()
    private val gamLoads = mutableListOf<AdManagerAdRequest>()

    /** The Prebid targeting the next auction answers with. */
    private var targeting: Map<String, String> = mapOf("hb_bidder" to "nexx360", "hb_size" to "300x250")

    @Before
    fun setUp() {
        AudienzzPrebidMobile.sdkInitializedOverride = true
        AppForegroundMonitor.resetForTesting()
        screenAdCoordinatorOverride = ScreenAdCoordinator()
        AudienzzPrebidMobile.pageImpression("prebid-resize")

        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        adView = spyk(AdManagerAdView(activity))
        every { adView.isAttachedToWindow } returns true
        adView.setAdSizes(AdSize(300, 600), AdSize(300, 250))

        adUnit = mockk(relaxed = true)
        val listener = slot<(AudienzzResultCode?) -> Unit>()
        val request = slot<AdManagerAdRequest>()
        every { adUnit.fetchDemand(capture(request), capture(listener)) } answers {
            targeting.forEach { (key, value) -> request.captured.customTargeting.putString(key, value) }
            responses.add(listener.captured)
        }
        every { adUnit.autoRefreshTime } returns 0
        every { adUnit.audienzzRefreshIntervalMillis } returns 0L

        handler = AudienzzAdViewHandler(adView, adUnit)
        handler.setScreen("prebid-resize")
    }

    @After
    fun tearDown() {
        AudienzzPrebidMobile.sdkInitializedOverride = null
        handler.destroy()
        screenAdCoordinatorOverride = null
        AppForegroundMonitor.resetForTesting()
    }

    private val googleListener: AdListener get() = adView.adListener
    private val appEvents: AppEventListener get() = adView.appEventListener!!

    private fun auction() {
        handler.load(withLazyLoading = false, prefetchMarginDp = 0) { request, _ -> gamLoads.add(request) }
        responses.last()(AudienzzResultCode.SUCCESS)
    }

    private fun shown(): String = adView.adSize!!.let { "${it.width}x${it.height}" }

    @Test
    fun `the Prebid creative's size is shown when GAM served it in a larger creative`() {
        auction()
        appEvents.onAppEvent("Prebid", "")
        googleListener.onAdLoaded()
        assertEquals("300x250", shown())
    }

    @Test
    fun `the publisher's onAdLoaded already sees the corrected size`() {
        var seen: String? = null
        adView.adListener = object : AdListener() {
            override fun onAdLoaded() { seen = shown() }
        }
        auction()
        appEvents.onAppEvent("prebid", "")
        googleListener.onAdLoaded()
        assertEquals("300x250", seen)
    }

    @Test
    fun `a GAM-served creative after a Prebid loss keeps GAM's size`() {
        targeting = emptyMap()
        auction()
        appEvents.onAppEvent("Prebid", "")
        googleListener.onAdLoaded()
        assertEquals("300x600", shown())
    }

    @Test
    fun `a remote ad config can turn it off`() {
        handler.resizeToPrebidCreative = false
        auction()
        appEvents.onAppEvent("Prebid", "")
        googleListener.onAdLoaded()
        assertEquals("300x600", shown())
    }

    @Test
    fun `the remote banner takes the switch from the ad config, on when absent`() {
        fun remoteHandler(config: String): AudienzzAdViewHandler {
            val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
            val banner = AudienzzRemoteBannerView(activity, "48")
            val json = """{"id":48,"config":{"adType":"banner","lazyLoad":true$config},
              "gamConfig":{"adUnitPath":"/test/banner","adSizes":["300x600","300x250"]},
              "prebidConfig":{"placementId":"test","adSizes":["300x250"]}}"""
            val parsed = kotlinx.serialization.json.Json.decodeFromString(RemoteAdUnitConfig.serializer(), json)
            AudienzzRemoteBannerView::class.java
                .getDeclaredMethod("createAdFromConfig", RemoteAdUnitConfig::class.java)
                .apply { isAccessible = true }
                .invoke(banner, parsed)
            val field = AudienzzRemoteBannerView::class.java.getDeclaredField("adViewHandler")
            field.isAccessible = true
            return (field.get(banner) as AudienzzAdViewHandler).also { it.destroy() }
        }
        assertTrue(remoteHandler("").resizeToPrebidCreative)
        assertFalse(remoteHandler(""","resizeToPrebidCreative":false""").resizeToPrebidCreative)
    }
}
