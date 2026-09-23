package org.audienzz.mobile.original

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
        // Robolectric never really initializes Prebid, and an uninitialized Prebid now
        // defers every auction — see AudienzzPrebidMobile.sdkInitializedOverride.
        AudienzzPrebidMobile.sdkInitializedOverride = true
        AppForegroundMonitor.resetForTesting()
        coordinator = ScreenAdCoordinator()
        screenAdCoordinatorOverride = coordinator
        coordinator.onScreenResumed("article")
    }

    @After fun cleanup() {
        AudienzzPrebidMobile.sdkInitializedOverride = null
        handlers.forEach { it.destroy() }
        screenAdCoordinatorOverride = null
        AppForegroundMonitor.resetForTesting()
        unmockkAll()
    }

    private class Slot(val handler: AudienzzAdViewHandler, val requests: MutableList<AdManagerAdRequest>,
                       val handoff: () -> Unit, val googleComplete: () -> Unit) {
        fun complete() { handoff(); googleComplete() }
    }

    private fun slot(): Slot {
        val view = mockk<AdManagerAdView>(relaxed = true)
        val observer = View(RuntimeEnvironment.getApplication()).viewTreeObserver
        every { view.viewTreeObserver } returns observer
        every { view.isAttachedToWindow } returns true
        var listener: AdListener = object : AdListener() {}
        every { view.adListener } answers { listener }
        every { view.adListener = any() } answers { listener = firstArg() }
        val unit = mockk<AudienzzAdUnit>(relaxed = true)
        val requests = mutableListOf<AdManagerAdRequest>()
        var response: ((AudienzzResultCode?) -> Unit)? = null
        every { unit.fetchDemand(any(), any()) } answers {
            requests.add(firstArg() as AdManagerAdRequest)
            response = secondArg()
        }
        val handler = AudienzzAdViewHandler(view, unit)
        handler.setScreen("article")
        handlers.add(handler)
        return Slot(handler, requests, { response!!(AudienzzResultCode.NO_BIDS) }, { listener.onAdLoaded() })
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
}
