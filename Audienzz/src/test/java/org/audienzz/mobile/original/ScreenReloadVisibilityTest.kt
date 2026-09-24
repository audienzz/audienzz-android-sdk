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
        // Robolectric never really initializes Prebid, and an uninitialized Prebid now
        // defers every auction — see AudienzzPrebidMobile.sdkInitializedOverride.
        AudienzzPrebidMobile.sdkInitializedOverride = true
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
        AudienzzPrebidMobile.sdkInitializedOverride = null
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
        if (blank) {
            assertEquals(
                "an off-screen slot must be blanked by the return too — its replacement is deferred, " +
                    "not cancelled, so showing the previous visit's creative until it is scrolled to " +
                    "is exactly what blanking on release exists to prevent",
                View.INVISIBLE,
                creativeVisibility,
            )
        }

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

    // ── header bidding off (no Prebid sizes configured) ─────────────────────

    /**
     * A slot with no Prebid sizes serves GAM-only: no Prebid request, straight to the GAM load.
     *
     * Before, this platform refused to load such a slot at all, and iOS sent Prebid a 0x0 request
     * that could never fill — a wasted round trip on every auction, recorded as a bidRequest and a
     * noBid for a slot that was never in header bidding.
     */
    private fun loadGamOnly() {
        AudienzzPrebidMobile.blankOnScreenReload = false
        handler.headerBiddingEnabled = false
        handler.load(withLazyLoading = false) { _, resultCode ->
            googleLoads++
            gamOnlyResultCodes += resultCode
        }
    }
    private val gamOnlyResultCodes = mutableListOf<AudienzzResultCode?>()

    @Test
    fun `with header bidding off, an auction never reaches Prebid and goes straight to GAM`() {
        loadGamOnly()

        assertEquals("Prebid must not be asked", 0, responses.size)
        assertEquals("GAM must still load", 1, googleLoads)
        assertEquals("there was no Prebid result to report", listOf<AudienzzResultCode?>(null), gamOnlyResultCodes)
    }

    @Test
    fun `a GAM-only slot still reloads on its next page impression, without Prebid`() {
        // Page ownership and refresh run on the same path; only the Prebid step is skipped.
        loadGamOnly()
        listener.onAdLoaded()

        AudienzzPrebidMobile.pageImpression("legacy")
        AudienzzPrebidMobile.pageImpression("remote")

        assertEquals(2, googleLoads)
        assertEquals(0, responses.size)
    }

    @Test
    fun `a GAM-only auction reports no header-bidding analytics`() {
        // A bidRequest with no response — or a noBid for a slot that never bid — would put an
        // auction that never happened into the header-bidding funnel.
        val logged = mutableListOf<org.audienzz.mobile.event.entity.EventDomain>()
        val logger = mockk<org.audienzz.mobile.event.EventLogger>(relaxed = true)
        every { logger.logEvent(capture(logged)) } just Runs
        mockkObject(org.audienzz.mobile.di.MainComponent.Companion)
        every { org.audienzz.mobile.di.MainComponent.eventLogger } returns logger

        loadGamOnly()

        val hbEvents = setOf(
            org.audienzz.mobile.event.entity.EventType.BID_REQUEST,
            org.audienzz.mobile.event.entity.EventType.BID_RESPONSE,
            org.audienzz.mobile.event.entity.EventType.BID_WON,
            org.audienzz.mobile.event.entity.EventType.NO_BID,
        )
        assertEquals(emptyList<Any>(), logged.filter { it.eventType in hbEvents }.map { it.eventType })
    }

    @Test
    fun `control - with header bidding on, the same auction does go to Prebid and reports it`() {
        // Proves the analytics assertion above can fail: the logger IS wired in this harness.
        val logged = mutableListOf<org.audienzz.mobile.event.entity.EventDomain>()
        val logger = mockk<org.audienzz.mobile.event.EventLogger>(relaxed = true)
        every { logger.logEvent(capture(logged)) } just Runs
        mockkObject(org.audienzz.mobile.di.MainComponent.Companion)
        every { org.audienzz.mobile.di.MainComponent.eventLogger } returns logger

        AudienzzPrebidMobile.blankOnScreenReload = false
        handler.load(withLazyLoading = false) { _, _ -> googleLoads++ }

        assertEquals(1, responses.size)
        assertTrue(logged.any { it.eventType == org.audienzz.mobile.event.entity.EventType.BID_REQUEST })
    }

    // ── Prebid initialization race ──────────────────────────────────────────

    /**
     * A banner created before Prebid has initialized must wait for it, not burn its one auction.
     *
     * Prebid does not fail politely when it is not ready: it logs "SDK wasn't initialized. Context
     * is null." and never calls back. The request therefore stayed in flight forever, and
     * `rearmInitialLoad()` refused to re-arm because a request was in flight — so an above-the-fold
     * banner, which fires its first load immediately on launch, stayed empty for the whole session
     * while slots further down the page (which only fire when scrolled to) loaded normally.
     */
    @Test
    fun `an auction is deferred while Prebid is still initializing`() {
        AudienzzPrebidMobile.sdkInitializedOverride = false

        handler.load(withLazyLoading = false) { _, _ -> googleLoads++ }

        assertEquals("nothing may reach Prebid before it is ready", 0, responses.size)
    }

    @Test
    fun `the deferred auction runs once Prebid finishes initializing`() {
        AudienzzPrebidMobile.sdkInitializedOverride = false
        handler.load(withLazyLoading = false) { _, _ -> googleLoads++ }
        assertEquals(0, responses.size)

        AudienzzPrebidMobile.sdkInitializedOverride = true
        screenAdCoordinatorOverride!!.resumeAllAfterSdkInit()

        assertEquals("the deferred first load must be taken, not dropped", 1, responses.size)
        responses[0](AudienzzResultCode.NO_BIDS)
        listener.onAdLoaded()
        assertEquals(1, googleLoads)
    }

    @Test
    fun `initializing does not start a second auction for a banner that already loaded`() {
        // The broadcast reaches every live banner, including ones that never waited on it.
        loadVisibleBanner(blank = false, v2 = true)

        screenAdCoordinatorOverride!!.resumeAllAfterSdkInit()

        assertEquals(1, responses.size)
        assertEquals(1, googleLoads)
    }

    @Test
    fun `a destroyed banner is not resumed by initialization`() {
        AudienzzPrebidMobile.sdkInitializedOverride = false
        handler.load(withLazyLoading = false) { _, _ -> googleLoads++ }
        handler.destroy()

        AudienzzPrebidMobile.sdkInitializedOverride = true
        screenAdCoordinatorOverride!!.resumeAllAfterSdkInit()

        assertEquals("a banner torn down while waiting must stay torn down", 0, responses.size)
    }

    /**
     * Returning to a page blanks EVERY slot on it, not only the ones in the viewport.
     *
     * A slot below the fold is refused on return (NOT_VISIBLE) and defers its replacement until it
     * is scrolled to. Blanking only where the auction actually starts therefore left every
     * off-screen slot displaying the previous visit's creative — the user scrolled down after
     * coming back and saw stale ads.
     */
    @Test
    fun `an off-screen slot is blanked by the return, not only the on-screen ones`() {
        loadVisibleBanner(blank = true, v2 = true)
        inViewport = false
        observer.dispatchOnPreDraw()

        AudienzzPrebidMobile.pageImpression("legacy")
        AudienzzPrebidMobile.pageImpression("remote")

        assertEquals("its replacement is deferred, not cancelled", 1, responses.size)
        assertEquals(View.INVISIBLE, creativeVisibility)
        assertEquals("and still without touching the ad view", View.VISIBLE, viewVisibility)
    }

    @Test
    fun `the off-screen slot reveals only once its deferred replacement lands`() {
        loadVisibleBanner(blank = true, v2 = true)
        inViewport = false
        observer.dispatchOnPreDraw()
        AudienzzPrebidMobile.pageImpression("legacy")
        AudienzzPrebidMobile.pageImpression("remote")
        assertEquals(View.INVISIBLE, creativeVisibility)

        // Scrolled into view: the deferred replacement runs.
        inViewport = true
        observer.dispatchOnPreDraw()
        assertEquals(2, responses.size)
        assertEquals("still blank while the replacement is in flight", View.INVISIBLE, creativeVisibility)

        responses[1](AudienzzResultCode.NO_BIDS)
        listener.onAdLoaded()

        assertEquals(View.VISIBLE, creativeVisibility)
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
