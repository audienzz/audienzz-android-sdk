package org.audienzz.mobile.original

import android.os.Looper
import java.util.concurrent.TimeUnit
import org.robolectric.Shadows.shadowOf
import org.audienzz.mobile.refresh.RefreshBlockReason
import com.google.android.gms.ads.LoadAdError
import android.view.View
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.admanager.AdManagerAdRequest
import com.google.android.gms.ads.admanager.AdManagerAdView
import io.mockk.*
import org.audienzz.mobile.AudienzzAdUnit
import org.audienzz.mobile.AudienzzPrebidMobile
import org.audienzz.mobile.AudienzzResultCode
import org.audienzz.mobile.screen.ScreenAdCoordinator
import org.audienzz.mobile.screen.screenAdCoordinatorOverride
import org.audienzz.mobile.targeting.AdRequestLedger
import org.audienzz.mobile.targeting.AdRequestSnapshot
import org.audienzz.mobile.targeting.AudienzzAdRequestContext
import org.audienzz.mobile.util.AppForegroundMonitor
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class AdRequestContextTest {
    private val handlers = mutableListOf<AudienzzAdViewHandler>()
    private lateinit var coordinator: ScreenAdCoordinator

    @Before fun setup() {
        AppForegroundMonitor.resetForTesting()
        coordinator = ScreenAdCoordinator()
        screenAdCoordinatorOverride = coordinator
        coordinator.onScreenResumed("article")
    }

    @After fun cleanup() {
        handlers.forEach { it.destroy() }
        screenAdCoordinatorOverride = null
        AppForegroundMonitor.resetForTesting()
        unmockkAll()
    }

    private class Slot(val handler: AudienzzAdViewHandler, val requests: MutableList<AdManagerAdRequest>,
                       val handoff: () -> Unit, val googleComplete: () -> Unit, val googleError: () -> Unit, val creative: View) {
        fun complete() { handoff(); googleComplete() }
    }

    private fun slot(context: AudienzzAdRequestContext = AudienzzAdRequestContext(), interval: Long = 0): Slot {
        val view = mockk<AdManagerAdView>(relaxed = true)
        val observer = View(RuntimeEnvironment.getApplication()).viewTreeObserver
        every { view.viewTreeObserver } returns observer
        every { view.isAttachedToWindow } returns true
        val creative = View(RuntimeEnvironment.getApplication())
        every { view.childCount } returns 1
        every { view.getChildAt(0) } returns creative
        var listener: AdListener = object : AdListener() {}
        every { view.adListener } answers { listener }
        every { view.adListener = any() } answers { listener = firstArg() }
        val unit = mockk<AudienzzAdUnit>(relaxed = true)
        every { unit.audienzzRefreshIntervalMillis } returns interval
        val requests = mutableListOf<AdManagerAdRequest>()
        var response: ((AudienzzResultCode?) -> Unit)? = null
        every { unit.fetchDemand(any(), any()) } answers {
            requests.add(firstArg() as AdManagerAdRequest)
            response = secondArg()
        }
        val handler = AudienzzAdViewHandler(view, unit, context)
        handler.setScreen("article")
        handlers.add(handler)
        return Slot(handler, requests, { response!!(AudienzzResultCode.NO_BIDS) }, { listener.onAdLoaded() },
            { listener.onAdFailedToLoad(LoadAdError(2, "transport", "test", null, null)) }, creative)
    }

    private fun assertRequest(request: AdManagerAdRequest, page: Int, slot: Int, refresh: Int) {
        assertEquals(page.toString(), request.customTargeting.getString("au_page_seq"))
        assertEquals(slot.toString(), request.customTargeting.getString("au_slot"))
        assertEquals(refresh.toString(), request.customTargeting.getString("au_refresh"))
    }

    @Test fun `a registered deferred slot keeps its position when the second slot requests first`() {
        val first = slot()
        first.handler.stopAutoRefresh()
        first.handler.load(withLazyLoading = false) { _, _ -> }
        val second = slot()
        second.handler.load(withLazyLoading = false) { _, _ -> }
        assertTrue(first.requests.isEmpty())
        assertEquals(1, second.requests.size)
        assertRequest(second.requests.single(), 1, 2, 0)
        first.handler.resumeAutoRefresh()
        assertEquals(1, first.requests.size)
        assertRequest(first.requests.single(), 1, 1, 0)
    }

    @Test fun `only admitted requests increment and page impressions reset each slot independently`() {
        val first = slot(); val second = slot()
        first.handler.load(withLazyLoading = false) { _, _ -> }
        second.handler.load(withLazyLoading = false) { _, _ -> }
        val original = first.requests.single()
        first.handoff() // Google now owns an outstanding request.
        first.handler.reloadAd() // Defers until Google terminates.
        assertEquals(1, first.requests.size)
        first.googleComplete(); second.complete()
        assertEquals(2, first.requests.size)
        assertRequest(first.requests.last(), 1, 1, 1)
        assertRequest(second.requests.single(), 1, 2, 0)
        first.complete()
        coordinator.onScreenResumed("article")
        assertEquals(3, first.requests.size)
        assertEquals(2, second.requests.size)
        assertRequest(first.requests.last(), 2, 1, 0)
        assertRequest(second.requests.last(), 2, 2, 0)
        assertRequest(original, 1, 1, 0) // Earlier built requests are immutable snapshots.
    }

    @Test fun `ledger preserves creation order through an unordered page sweep`() {
        val ledger = AdRequestLedger()
        val first = AudienzzAdRequestContext(); val second = AudienzzAdRequestContext()
        ledger.beginPage(7, listOf(second, first, first))
        assertEquals(AdRequestSnapshot(7, 2, 0), ledger.nextRequest(second))
        assertEquals(AdRequestSnapshot(7, 1, 0), ledger.nextRequest(first))
        assertEquals(AdRequestSnapshot(7, 2, 1), ledger.nextRequest(second))
        ledger.beginPage(8, listOf(second, first))
        assertEquals(AdRequestSnapshot(8, 2, 0), ledger.nextRequest(second))
    }

    @Test fun `bridge replacements share one slot but different ad objects do not`() {
        val first = AudienzzAdRequestContext.forSlot("flutter-a")
        val replacement = AudienzzAdRequestContext.forSlot("flutter-a")
        val second = AudienzzAdRequestContext.forSlot("flutter-b")
        assertSame(first, replacement)
        assertRequest(first.buildRequest(AdManagerAdRequest.Builder()), 1, 1, 0)
        assertRequest(replacement.buildRequest(AdManagerAdRequest.Builder()), 1, 1, 1)
        assertRequest(second.buildRequest(AdManagerAdRequest.Builder()), 1, 2, 0)
        coordinator.onScreenResumed("next")
        val next = AudienzzAdRequestContext.forSlot("flutter-a")
        assertNotSame(first, next)
        assertRequest(next.buildRequest(AdManagerAdRequest.Builder()), 2, 1, 0)
    }

    @Test fun `legacy unreported page is zero and reserved keys override publisher targeting`() {
        coordinator = ScreenAdCoordinator()
        screenAdCoordinatorOverride = coordinator
        val context = AudienzzAdRequestContext()
        val builder = AdManagerAdRequest.Builder().addCustomTargeting("category", "sports")
            .addCustomTargeting("au_refresh", "999")
        val request = context.buildRequest(builder)
        assertRequest(request, 0, 1, 0)
        assertEquals("sports", request.customTargeting.getString("category"))
    }

    @Test fun `ten timed refreshes then days idle retain final delivery without further requests`() {
        val slot = slot(interval = 30_000)
        var deliveries = 0
        slot.handler.load(withLazyLoading = false) { _, _ -> deliveries++ }
        repeat(10) { index ->
            slot.complete()
            shadowOf(Looper.getMainLooper()).idleFor(30, TimeUnit.SECONDS)
            assertEquals(index + 2, slot.requests.size)
        }
        assertRequest(slot.requests.last(), 1, 1, 10)
        assertTrue(slot.handler.refreshController.hasRequestInFlight)
        val generation = slot.handler.refreshController.generation
        repeat(100) {
            slot.handler.reloadAd()
            slot.handler.stopAutoRefresh(); slot.handler.resumeAutoRefresh()
            slot.handler.pauseSmartRefresh(); slot.handler.resumeSmartRefresh()
        }
        assertEquals("reload at cap cannot cancel the last permitted auction", generation, slot.handler.refreshController.generation)
        slot.complete()
        assertEquals("last allowed response must still reach Google", 11, deliveries)
        assertFalse(slot.handler.refreshController.hasRequestInFlight)
        assertTrue(slot.handler.refreshController.blockReasons.contains(RefreshBlockReason.REFRESH_LIMIT))
        shadowOf(Looper.getMainLooper()).idleFor(7, TimeUnit.DAYS)
        assertEquals(11, slot.requests.size)

        slot.handler.stopAutoRefresh()
        coordinator.onScreenResumed("article")
        assertFalse(slot.handler.refreshController.blockReasons.contains(RefreshBlockReason.REFRESH_LIMIT))
        assertTrue(slot.handler.refreshController.blockReasons.contains(RefreshBlockReason.PUBLISHER))
        assertEquals(11, slot.requests.size)
        slot.handler.resumeAutoRefresh()
        assertEquals(12, slot.requests.size)
        assertRequest(slot.requests.last(), 2, 1, 0)
    }

    @Test fun `reload at the cap leaves the Google creative visible with blank on reload enabled`() {
        val original = AudienzzPrebidMobile.blankOnScreenReload
        AudienzzPrebidMobile.blankOnScreenReload = true
        try {
            val slot = slot()
            slot.handler.load(withLazyLoading = false) { _, _ -> }
            repeat(10) {
                slot.complete()
                slot.handler.reloadAd()
                assertEquals("fixture exercises real creative blanking", View.INVISIBLE, slot.creative.visibility)
            }
            slot.complete()
            assertEquals(View.VISIBLE, slot.creative.visibility)
            repeat(100) { slot.handler.reloadAd() }
            assertEquals(11, slot.requests.size)
            assertEquals(View.VISIBLE, slot.creative.visibility)
        } finally {
            AudienzzPrebidMobile.blankOnScreenReload = original
        }
    }

    @Test fun `failed requests and cancelled auctions consume budget and final failure cannot retry`() {
        val slot = slot(interval = 30_000)
        slot.handler.load(withLazyLoading = false) { _, _ -> }
        repeat(10) { slot.handler.reloadAd() } // superseded Prebid auctions are still spent requests
        assertEquals(11, slot.requests.size)
        slot.handoff(); slot.googleError()
        shadowOf(Looper.getMainLooper()).idleFor(7, TimeUnit.DAYS)
        assertEquals(11, slot.requests.size)
        assertFalse(slot.handler.refreshController.hasRequestInFlight)
    }

    @Test fun `automatic transport retries also stop at the slot budget`() {
        val slot = slot(interval = 30_000)
        slot.handler.load(withLazyLoading = false) { _, _ -> }
        repeat(11) { index ->
            assertEquals(index + 1, slot.requests.size)
            slot.handoff(); slot.googleError()
            shadowOf(Looper.getMainLooper()).idleFor(30, TimeUnit.SECONDS)
        }
        shadowOf(Looper.getMainLooper()).idleFor(7, TimeUnit.DAYS)
        assertEquals(11, slot.requests.size)
    }

    @Test fun `native replacement shares cap other slots do not and first load recovers on new page`() {
        val context = AudienzzAdRequestContext.forSlot("managed-slot")
        val old = slot(context)
        old.handler.load(withLazyLoading = false) { _, _ -> }
        repeat(10) { old.complete(); old.handler.reloadAd() }
        old.complete(); old.handler.destroy()
        val replacement = slot(AudienzzAdRequestContext.forSlot("managed-slot"))
        replacement.handler.load(withLazyLoading = false) { _, _ -> }
        assertEquals(0, replacement.requests.size)
        assertTrue(replacement.handler.refreshController.blockReasons.contains(RefreshBlockReason.REFRESH_LIMIT))
        val other = slot()
        other.handler.load(withLazyLoading = false) { _, _ -> }
        assertRequest(other.requests.single(), 1, 2, 0)
        coordinator.onScreenResumed("article")
        assertRequest(replacement.requests.single(), 2, 1, 0)
    }

    @Test fun `unreported page is capped too and interstitial request builder remains unrestricted`() {
        coordinator = ScreenAdCoordinator()
        screenAdCoordinatorOverride = coordinator
        val slot = slot()
        slot.handler.load(withLazyLoading = false) { _, _ -> }
        repeat(20) { slot.complete(); slot.handler.reloadAd() }
        assertEquals(11, slot.requests.size)
        assertRequest(slot.requests.last(), 0, 1, 10)
        AudienzzPrebidMobile.observeForegroundReimpression()
        val host = mockk<android.app.Activity>(relaxed = true)
        AppForegroundMonitor.onActivityStarted(host)
        AppForegroundMonitor.onActivityStopped(host)
        assertFalse(AppForegroundMonitor.isForeground)
        shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.DAYS)
        AppForegroundMonitor.onActivityStarted(host)
        shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.SECONDS)
        assertEquals("no reported page means foreground does not reset the cap", 11, slot.requests.size)
        val interstitial = AudienzzAdRequestContext()
        repeat(20) { interstitial.buildRequest(AdManagerAdRequest.Builder()) }
        assertRequest(interstitial.buildRequest(AdManagerAdRequest.Builder()), 0, 2, 20)
    }
}
