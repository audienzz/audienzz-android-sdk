package org.audienzz.mobile.original

import android.os.Looper
import com.google.android.gms.ads.admanager.AdManagerAdRequest
import com.google.android.gms.ads.admanager.AdManagerAdView
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.audienzz.mobile.AudienzzAdUnit
import org.audienzz.mobile.AudienzzResultCode
import org.audienzz.mobile.screen.ScreenAdCoordinator
import org.audienzz.mobile.screen.screenAdCoordinatorOverride
import org.audienzz.mobile.util.AppForegroundMonitor
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.util.concurrent.TimeUnit

/**
 * The handler is a small state machine over {page active, app foreground, auction in flight, first
 * load done}, driven by lifecycle callbacks that arrive in arbitrary orders. Every case below is a
 * transition that shipped broken at some point: an auction leaking past the gate, a superseded
 * response resurrecting a released banner, or a load stranded with nothing left to retry it.
 *
 * A real [ScreenAdCoordinator] drives the page transitions, so these exercise the same path
 * production takes. Banners are matched to pages by route key (as the Flutter and RN bridges do),
 * which keeps the test independent of the view hierarchy.
 */
@RunWith(RobolectricTestRunner::class)
class AudienzzAdViewHandlerTest {

    private lateinit var coordinator: ScreenAdCoordinator
    private lateinit var adView: AdManagerAdView
    private lateinit var adUnit: AudienzzAdUnit
    private lateinit var handler: AudienzzAdViewHandler

    /** GAM loads the handler asked for — one entry per `adView.loadAd(request)`. */
    private lateinit var gamLoads: MutableList<AdManagerAdRequest>

    /** Prebid response callbacks, so a test can answer any auction whenever it likes. */
    private lateinit var responses: MutableList<(AudienzzResultCode?) -> Unit>

    @Before
    fun setUp() {
        AppForegroundMonitor.resetForTesting()
        coordinator = ScreenAdCoordinator()
        screenAdCoordinatorOverride = coordinator

        gamLoads = mutableListOf()
        responses = mutableListOf()

        adView = mockk(relaxed = true)
        adUnit = mockk(relaxed = true)

        val listener = slot<(AudienzzResultCode?) -> Unit>()
        every { adUnit.fetchDemand(any(), capture(listener)) } answers {
            responses.add(listener.captured)
        }
        every { adUnit.autoRefreshTime } returns 30_000

        handler = AudienzzAdViewHandler(adView, adUnit)
    }

    @After
    fun tearDown() {
        screenAdCoordinatorOverride = null
        AppForegroundMonitor.resetForTesting()
    }

    /** Report a page, as `pageImpression` does. */
    private fun openPage(name: String) = coordinator.onScreenResumed(name, name)

    /** Create the banner on [page] and start its load. */
    private fun loadOn(page: String, lazy: Boolean = false) {
        handler.setScreen(page)
        handler.load(withLazyLoading = lazy, prefetchMarginDp = 0) { request, _ ->
            gamLoads.add(request)
        }
    }

    /** Answer the Nth auction started (0-based). */
    private fun respondTo(auction: Int) = responses[auction].invoke(AudienzzResultCode.SUCCESS)

    private fun idle(millis: Long) =
        shadowOf(Looper.getMainLooper()).idleFor(millis, TimeUnit.MILLISECONDS)

    private fun background() {
        val activity = mockk<android.app.Activity>(relaxed = true)
        AppForegroundMonitor.onActivityStarted(activity)
        AppForegroundMonitor.onActivityStopped(activity)
    }

    private fun foreground() = AppForegroundMonitor.onActivityStarted(mockk(relaxed = true))

    // ── The auction gate ────────────────────────────────────────────────────

    @Test
    fun `a first load auctions when its page is the active one`() {
        openPage("A")

        loadOn("A")

        assertEquals(1, responses.size)
    }

    @Test
    fun `a first load does not auction for a page the user has already left`() {
        // Asynchronous setup can finish after the user moved on; auctioning here would burn a
        // request on a screen they are no longer looking at.
        openPage("A")
        openPage("B")

        loadOn("A")

        assertEquals(0, responses.size)
    }

    @Test
    fun `an auction blocked by backgrounding is retried once the app returns`() {
        openPage("A")
        background()

        loadOn("A")
        assertEquals("blocked while backgrounded", 0, responses.size)

        foreground()
        idle(1_000)

        assertEquals("retried on foreground", 1, responses.size)
    }

    @Test
    fun `a deferred retry does not double-auction when a page impression got there first`() {
        // The deferred retry is delayed past the automatic foreground page impression precisely so
        // the impression can claim it. Whichever lands first, the other must be a no-op.
        openPage("A")
        background()
        loadOn("A")
        foreground()

        openPage("A") // the automatic foreground page impression
        idle(2_000)

        assertEquals(1, responses.size)
    }

    // ── Superseded auctions ─────────────────────────────────────────────────

    @Test
    fun `a response arriving after the page was left does not load GAM`() {
        openPage("A")
        loadOn("A")

        openPage("B")
        respondTo(0)

        assertEquals("the creative belongs to a screen the user left", 0, gamLoads.size)
    }

    @Test
    fun `leaving a page retires the Prebid loader`() {
        // Cancelling the refresh timer is not enough: a response in flight re-arms it from both
        // Prebid's success and failure handlers, and that refresh calls load() directly without
        // passing the auction gate. Only destroy() retires the loader.
        //
        // Counted around the transition specifically: every auction also retires its predecessor,
        // so asserting destroy() was called at all would pass even if leaving the page did nothing.
        openPage("A")
        loadOn("A")
        clearMocks(adUnit, answers = false, recordedCalls = true, verificationMarks = true)

        openPage("B")

        verify(exactly = 1) { adUnit.destroy() }
    }

    @Test
    fun `re-reporting the same page supersedes the previous visit's auction`() {
        openPage("A")
        loadOn("A")
        respondTo(0)
        gamLoads.clear()

        openPage("A") // same screen, new visit -> fresh auction
        assertEquals("the new visit auctions", 2, responses.size)

        respondTo(0) // the previous visit's auction finally answers

        assertEquals("a superseded response must not load", 0, gamLoads.size)

        respondTo(1)
        assertEquals("the current visit's response loads", 1, gamLoads.size)
    }

    // ── Paths that used to revive a released banner ─────────────────────────

    @Test
    fun `resumeSmartRefresh cannot auction for a released page, now or later`() {
        // The bridges' visibility layers and AudienzzRemoteBannerView.onResume() both reach this.
        // Idling past the refresh interval catches a resume that merely schedules the auction
        // instead of starting it immediately.
        openPage("A")
        loadOn("A")
        respondTo(0)
        openPage("B")
        val before = responses.size

        handler.resumeSmartRefresh()
        idle(60_000)

        assertEquals(before, responses.size)
    }

    @Test
    fun `reloadAd refuses while the page is released`() {
        openPage("A")
        loadOn("A")
        respondTo(0)
        openPage("B")
        val before = responses.size

        handler.reloadAd()

        assertEquals(before, responses.size)
    }

    @Test
    fun `a reload blocked by backgrounding does not restart Prebid's refresh timer`() {
        // fetchDemand correctly refuses, but resuming regardless restarts the existing loader,
        // whose refresh calls load() directly -- an auction straight past the gate.
        openPage("A")
        loadOn("A")
        respondTo(0)
        background()
        val before = responses.size

        handler.reloadAd()

        assertEquals(before, responses.size)
        verify(exactly = 0) { adUnit.resumeAutoRefresh() }
    }

    // ── First-load recovery ─────────────────────────────────────────────────

    @Test
    fun `a first load deferred for an inactive page runs when that page comes back`() {
        openPage("A")
        openPage("B")
        loadOn("A")
        assertEquals(0, responses.size)

        openPage("A")

        assertEquals("activation must re-arm the first load", 1, responses.size)
    }

    @Test
    fun `returning to a page that never loaded starts exactly one auction`() {
        openPage("A")
        openPage("B")
        loadOn("A")

        openPage("A")
        idle(2_000)

        assertEquals(1, responses.size)
    }
}
