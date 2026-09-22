package org.audienzz.mobile.original

import android.graphics.Rect
import android.view.View
import android.view.ViewTreeObserver
import org.robolectric.RuntimeEnvironment
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.admanager.AdManagerAdView
import io.mockk.*
import org.audienzz.mobile.AudienzzAdUnit
import org.audienzz.mobile.AudienzzPrebidMobile
import org.audienzz.mobile.AudienzzResultCode
import org.audienzz.mobile.refresh.RefreshBlockReason
import org.audienzz.mobile.screen.ScreenAdCoordinator
import org.audienzz.mobile.screen.screenAdCoordinatorOverride
import org.audienzz.mobile.util.AppForegroundMonitor
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Real handler, coordinator, visibility predicates and pre-draw listener; controlled Google geometry/results. */
@RunWith(RobolectricTestRunner::class)
class ScreenReloadVisibilityTest {
    private lateinit var handler: AudienzzAdViewHandler
    private lateinit var view: AdManagerAdView
    private lateinit var observer: ViewTreeObserver
    private var listener: AdListener = object : AdListener() {}
    private val responses = mutableListOf<(AudienzzResultCode?) -> Unit>()
    private var googleLoads = 0
    private var inViewport = true
    private var viewVisibility = View.VISIBLE
    /**
     * The rendered creative. Blanking hides the ad view's CHILDREN, never the ad view itself —
     * [org.audienzz.mobile.util.isRefreshEligible] rejects anything whose own visibility is not
     * VISIBLE, so blanking the ad view made the slot ineligible for the auction meant to refill it.
     */
    private var creativeVisibility = View.VISIBLE
    private var oldBlank = false
    private var oldV2: Boolean? = null

    @Before fun setup() {
        oldBlank = AudienzzPrebidMobile.blankOnScreenReload
        oldV2 = AudienzzPrebidMobile.smartRefreshV2Override
        AppForegroundMonitor.resetForTesting()
        screenAdCoordinatorOverride = ScreenAdCoordinator()
        AudienzzPrebidMobile.observeForegroundReimpression()
        AudienzzPrebidMobile.pageImpression("remote")
        observer = View(RuntimeEnvironment.getApplication()).viewTreeObserver
        view = mockk(relaxed = true)
        every { view.viewTreeObserver } returns observer
        every { view.visibility } answers { viewVisibility }
        every { view.visibility = any() } answers { viewVisibility = firstArg() }
        every { view.isAttachedToWindow } returns true
        every { view.measuredHeight } returns 50
        every { view.getGlobalVisibleRect(any<Rect>()) } answers {
            if (inViewport) firstArg<Rect>().set(0, 0, 320, 50)
            inViewport
        }
        every { view.getLocationInWindow(any()) } answers {
            firstArg<IntArray>()[0] = 0
            firstArg<IntArray>()[1] = 0
        }
        val creative: View = mockk(relaxed = true)
        every { creative.visibility } answers { creativeVisibility }
        every { creative.visibility = any() } answers { creativeVisibility = firstArg() }
        every { view.childCount } returns 1
        every { view.getChildAt(0) } returns creative
        every { view.adListener } answers { listener }
        every { view.adListener = any() } answers { listener = firstArg() }
        val unit = mockk<AudienzzAdUnit>(relaxed = true)
        every { unit.audienzzRefreshIntervalMillis } returns 30_000L
        every { unit.autoRefreshTime } returns 30_000
        every { unit.fetchDemand(any(), any()) } answers { responses.add(secondArg()) }
        handler = AudienzzAdViewHandler(view, unit)
        handler.setScreen("remote")
    }

    @After fun cleanup() {
        handler.destroy()
        AudienzzPrebidMobile.pageImpression("cleanup")
        screenAdCoordinatorOverride = null
        AppForegroundMonitor.resetForTesting()
        AudienzzPrebidMobile.blankOnScreenReload = oldBlank
        AudienzzPrebidMobile.smartRefreshV2Override = oldV2
        unmockkAll()
    }

    private fun loadVisibleBanner(blank: Boolean, v2: Boolean) {
        AudienzzPrebidMobile.blankOnScreenReload = blank
        AudienzzPrebidMobile.smartRefreshV2Override = v2
        handler.load(withLazyLoading = false) { _, _ -> googleLoads++ }
        handler.enableSmartRefresh()
        assertEquals(1, responses.size)
        responses[0](AudienzzResultCode.NO_BIDS)
        listener.onAdLoaded()
        assertEquals("control: the initial creative completed", 1, googleLoads)
        assertEquals(View.VISIBLE, viewVisibility)
        assertEquals(View.VISIBLE, creativeVisibility)
    }

    private fun leaveAndReturnWhileScrolledDown(blank: Boolean, v2: Boolean) {
        loadVisibleBanner(blank, v2)
        inViewport = false
        observer.dispatchOnPreDraw()
        assertTrue(handler.refreshController.blockReasons.contains(RefreshBlockReason.NOT_VISIBLE))
        AudienzzPrebidMobile.pageImpression("legacy")
        AudienzzPrebidMobile.pageImpression("remote")
        observer.dispatchOnPreDraw()
        assertEquals("an off-screen replacement must wait", 1, responses.size)
        assertEquals("a deferred request must not hide its own viewport trigger", View.VISIBLE, viewVisibility)
        assertEquals(
            "nothing is coming to refill the slot, so the previous creative beats a permanent blank",
            View.VISIBLE,
            creativeVisibility,
        )

        // User scrolls back to banner #1. No manual resume: the installed native listener must recover it.
        inViewport = true
        observer.dispatchOnPreDraw()
        assertEquals("the visible returning slot must request its pending page replacement", 2, responses.size)
        responses[1](AudienzzResultCode.NO_BIDS)
        listener.onAdLoaded()
        assertEquals(2, googleLoads)
        assertEquals(View.VISIBLE, viewVisibility)
        assertEquals("the fresh creative is revealed", View.VISIBLE, creativeVisibility)
    }

    @Test fun `v2 returning offscreen banner recovers when demo blanking is enabled`() =
        leaveAndReturnWhileScrolledDown(blank = true, v2 = true)

    @Test fun `v1 returning offscreen banner recovers when demo blanking is enabled`() =
        leaveAndReturnWhileScrolledDown(blank = true, v2 = false)

    @Test fun `control v2 returning offscreen banner recovers without demo blanking`() =
        leaveAndReturnWhileScrolledDown(blank = false, v2 = true)

    @Test fun `control v1 returning offscreen banner recovers without demo blanking`() =
        leaveAndReturnWhileScrolledDown(blank = false, v2 = false)
    private fun cancelledReplacementRecovers(v2: Boolean) {
        loadVisibleBanner(blank = true, v2 = v2)
        AudienzzPrebidMobile.pageImpression("legacy")
        AudienzzPrebidMobile.pageImpression("remote")
        assertEquals(2, responses.size)
        assertEquals("control: an accepted replacement is blanked", View.INVISIBLE, creativeVisibility)
        assertEquals("but the ad view itself is never hidden", View.VISIBLE, viewVisibility)
        observer.dispatchOnPreDraw()
        assertFalse(
            "a blanked slot must stay eligible — hiding the ad view itself is what used to mark it " +
                "NOT_VISIBLE and stall the very replacement the blank was waiting for",
            handler.refreshController.blockReasons.contains(RefreshBlockReason.NOT_VISIBLE),
        )

        // Leave again before the replacement's Prebid response. No Google callback can restore it.
        AudienzzPrebidMobile.pageImpression("legacy")
        assertEquals(
            "leaving blanks the slot: that is what stops the stale creative being on screen when " +
                "the page comes back",
            View.INVISIBLE,
            creativeVisibility,
        )
        assertEquals("and still without touching the ad view", View.VISIBLE, viewVisibility)
        responses[1](AudienzzResultCode.NO_BIDS)
        assertEquals("the retired auction must not load Google", 1, googleLoads)
        AudienzzPrebidMobile.pageImpression("remote")
        observer.dispatchOnPreDraw()
        assertEquals("the next visit must not inherit the cancelled request's invisibility", 3, responses.size)
        responses[2](AudienzzResultCode.NO_BIDS)
        listener.onAdLoaded()
        assertEquals(2, googleLoads)
        assertEquals(View.VISIBLE, viewVisibility)
        assertEquals("the fresh creative is revealed", View.VISIBLE, creativeVisibility)
    }

    /**
     * The headline behaviour: leaving a page clears its creative, so returning never shows the
     * previous ad. Blanking used to start when the replacement auction started, which left the
     * outgoing creative on screen for the whole transition — the user saw the old ad, then a blank,
     * then the new one.
     */
    @Test
    fun `leaving a page blanks its creative immediately, so the return never shows the old ad`() {
        loadVisibleBanner(blank = true, v2 = true)

        AudienzzPrebidMobile.pageImpression("legacy")

        assertEquals(View.INVISIBLE, creativeVisibility)
        assertEquals("without ever hiding the ad view the gate reads", View.VISIBLE, viewVisibility)
    }

    @Test
    fun `the slot is still blank when the page comes back, until the fresh creative arrives`() {
        loadVisibleBanner(blank = true, v2 = true)

        AudienzzPrebidMobile.pageImpression("legacy")
        AudienzzPrebidMobile.pageImpression("remote")

        assertEquals("still blank while the replacement is in flight", View.INVISIBLE, creativeVisibility)
        assertEquals(2, responses.size)

        responses[1](AudienzzResultCode.NO_BIDS)
        listener.onAdLoaded()

        assertEquals("revealed only once the fresh creative has rendered", View.VISIBLE, creativeVisibility)
    }

    @Test
    fun `blanking stays off when the publisher has not asked for it`() {
        loadVisibleBanner(blank = false, v2 = true)

        AudienzzPrebidMobile.pageImpression("legacy")
        AudienzzPrebidMobile.pageImpression("remote")

        assertEquals(View.VISIBLE, creativeVisibility)
    }

    @Test
    fun `a publisher-hidden slot is left alone`() {
        // Taking ownership of visibility the publisher set would reveal an ad they hid.
        loadVisibleBanner(blank = true, v2 = true)
        viewVisibility = View.GONE

        AudienzzPrebidMobile.pageImpression("legacy")
        AudienzzPrebidMobile.pageImpression("remote")
        listener.onAdLoaded()

        assertEquals(View.GONE, viewVisibility)
    }

    @Test fun `v2 a cancelled replacement releases its temporary blank`() = cancelledReplacementRecovers(v2 = true)

    @Test fun `v1 a cancelled replacement releases its temporary blank`() = cancelledReplacementRecovers(v2 = false)

}
