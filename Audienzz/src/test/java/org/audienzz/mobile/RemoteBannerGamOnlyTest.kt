package org.audienzz.mobile

import android.app.Activity
import android.os.Looper
import io.mockk.*
import org.audienzz.mobile.api.config.*
import org.audienzz.mobile.di.MainComponent
import org.audienzz.mobile.manager.RemoteConfigManager
import org.audienzz.mobile.original.AudienzzAdViewHandler
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * A remote banner whose config lists no Prebid sizes serves GAM-only.
 *
 * It used to refuse to load at all ("No valid sizes in remote config"), while iOS sent Prebid a 0x0
 * request that could never fill. Every platform now serves such a slot from GAM without asking
 * Prebid. GAM sizes stay required: without them nothing can render.
 */
@RunWith(RobolectricTestRunner::class)
class RemoteBannerGamOnlyTest {

    private lateinit var activity: Activity
    private var loaded: AudienzzAdViewHandler? = null
    /** What the banner set the switch to — null if it never set it. */
    private var headerBiddingSetTo: Boolean? = null

    private fun config(gam: List<String>, prebid: List<String>) = RemoteAdUnitConfig(
        49,
        RemoteConfig(adType = "banner", refreshTimeSeconds = 30, lazyLoad = false),
        RemoteGamConfig("/1234/unit", gam),
        RemotePrebidConfig("placement", prebid),
    )

    private fun seed(config: RemoteAdUnitConfig) {
        val manager = mockk<RemoteConfigManager>()
        coEvery { manager.getAdUnitConfig(any()) } returns config
        mockkObject(MainComponent.Companion)
        every { MainComponent.remoteConfigManager } returns manager
    }

    @Before fun setup() {
        activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        loaded = null
        headerBiddingSetTo = null
        mockkConstructor(AudienzzAdViewHandler::class)
        every { anyConstructed<AudienzzAdViewHandler>().load(any(), any(), any(), any()) } answers {
            loaded = self as AudienzzAdViewHandler
        }
        // Captured at the setter. Reading the property back off a constructor mock returns a
        // default, not what was set — which made the first version of these tests pass vacuously.
        every {
            anyConstructed<AudienzzAdViewHandler>() setProperty "headerBiddingEnabled" value any<Boolean>()
        } answers { headerBiddingSetTo = firstArg() }
        every { anyConstructed<AudienzzAdViewHandler>().enableSmartRefresh() } just Runs
    }

    @After fun cleanup() = unmockkAll()

    /** The config fetch hops to IO and back, so pump until the handler loads or time runs out. */
    private fun pump(maxMs: Long = 3_000) {
        val deadline = System.currentTimeMillis() + maxMs
        while (System.currentTimeMillis() < deadline && loaded == null) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(5)
        }
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test fun `no Prebid sizes still loads, with header bidding off`() {
        // Live prod config 49 has exactly this shape.
        seed(config(gam = listOf("300x250"), prebid = emptyList()))
        AudienzzRemoteBannerView(activity, "49").loadAd()
        pump()

        assertNotNull("a slot with no Prebid sizes must still serve (it used to refuse)", loaded)
        assertEquals("and must not ask Prebid", false, headerBiddingSetTo)
    }

    @Test fun `control - with Prebid sizes, header bidding stays on`() {
        seed(config(gam = listOf("300x250"), prebid = listOf("300x250")))
        AudienzzRemoteBannerView(activity, "46").loadAd()
        pump()

        assertNotNull(loaded)
        assertEquals(true, headerBiddingSetTo)
    }

    @Test fun `no GAM sizes still refuses to load`() {
        // Nothing can render without them, so this is the one case that still gives up.
        seed(config(gam = emptyList(), prebid = listOf("300x250")))
        AudienzzRemoteBannerView(activity, "bad").loadAd()
        pump(maxMs = 500)

        assertNull(loaded)
    }
}
