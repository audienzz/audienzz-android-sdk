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
import org.audienzz.mobile.event.entity.EventDomain
import org.audienzz.mobile.event.entity.EventType
import org.audienzz.mobile.refresh.RefreshBlockReason
import org.audienzz.mobile.screen.ScreenAdCoordinator
import org.audienzz.mobile.screen.screenAdCoordinatorOverride
import org.audienzz.mobile.util.AppForegroundMonitor
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import android.os.Looper
import java.util.concurrent.TimeUnit
import org.robolectric.RobolectricTestRunner

/** `keepsAdAcrossPages`: a page transition pauses the banner instead of releasing and reloading it. */
@RunWith(RobolectricTestRunner::class)
class KeepsAdAcrossPagesTest {
    private lateinit var handler: AudienzzAdViewHandler
    private lateinit var view: AdManagerAdView
    private lateinit var observer: ViewTreeObserver
    private var listener: AdListener = object : AdListener() {}
    private var appEventListener: com.google.android.gms.ads.admanager.AppEventListener =
        com.google.android.gms.ads.admanager.AppEventListener { _, _ -> }
    private val responses = mutableListOf<(AudienzzResultCode?) -> Unit>()
    private val requests = mutableListOf<com.google.android.gms.ads.admanager.AdManagerAdRequest>()
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
    private val events = mutableListOf<EventDomain>()
    private var responseId = "A"
    private var oldV2: Boolean? = null

    @Before fun setup() {
        // Robolectric never really initializes Prebid, and an uninitialized Prebid now
        // defers every auction — see AudienzzPrebidMobile.sdkInitializedOverride.
        AudienzzPrebidMobile.sdkInitializedOverride = true
        oldBlank = AudienzzPrebidMobile.blankOnScreenReload
        oldV2 = AudienzzPrebidMobile.smartRefreshV2Override
        AppForegroundMonitor.resetForTesting()
        screenAdCoordinatorOverride = ScreenAdCoordinator()
        AudienzzPrebidMobile.observeForegroundRecovery()
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
        every { view.responseInfo } answers {
            mockk(relaxed = true) { every { this@mockk.responseId } answers { this@KeepsAdAcrossPagesTest.responseId } }
        }
        val logger = mockk<org.audienzz.mobile.event.EventLogger>(relaxed = true)
        every { logger.logEvent(capture(events)) } just Runs
        mockkObject(org.audienzz.mobile.di.MainComponent.Companion)
        every { org.audienzz.mobile.di.MainComponent.eventLogger } returns logger
        every { view.appEventListener } answers { appEventListener }
        every { view.appEventListener = any() } answers { appEventListener = firstArg() }
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

    private fun loadVisibleBanner(blank: Boolean = true, v2: Boolean = true) {
        AudienzzPrebidMobile.blankOnScreenReload = blank
        AudienzzPrebidMobile.smartRefreshV2Override = v2
        handler.load(withLazyLoading = false) { request, _ -> googleLoads++; requests += request }
        handler.enableSmartRefresh()
        assertEquals(1, responses.size)
        responses[0](AudienzzResultCode.NO_BIDS)
        listener.onAdLoaded()
        assertEquals("control: the initial creative completed", 1, googleLoads)
        assertEquals(View.VISIBLE, viewVisibility)
        assertEquals(View.VISIBLE, creativeVisibility)
    }

    @Test fun `leaving and returning keeps the creative and requests nothing`() {
        handler.keepsAdAcrossPages = true
        loadVisibleBanner()

        AudienzzPrebidMobile.pageImpression("article")
        assertEquals("not blanked on the way out", View.VISIBLE, creativeVisibility)
        assertTrue(handler.refreshController.blockReasons.contains(RefreshBlockReason.PAGE_INACTIVE))

        AudienzzPrebidMobile.pageImpression("remote")
        observer.dispatchOnPreDraw()
        assertEquals("no auction on return", 1, responses.size)
        assertEquals(1, googleLoads)
        assertEquals("the same creative is shown", View.VISIBLE, creativeVisibility)
        assertFalse(handler.refreshController.blockReasons.contains(RefreshBlockReason.PAGE_INACTIVE))
    }

    @Test fun `control - by default a return forces a fresh auction`() {
        loadVisibleBanner()
        AudienzzPrebidMobile.pageImpression("article")
        assertEquals("released and blanked", View.INVISIBLE, creativeVisibility)
        AudienzzPrebidMobile.pageImpression("remote")
        observer.dispatchOnPreDraw()
        assertEquals(2, responses.size)
    }

    @Test fun `a re-report of the same page does not reload a kept ad`() {
        handler.keepsAdAcrossPages = true
        loadVisibleBanner()
        AudienzzPrebidMobile.pageImpression("remote")
        observer.dispatchOnPreDraw()
        assertEquals(1, responses.size)
        assertEquals(View.VISIBLE, creativeVisibility)
    }

    @Test fun `no auction runs while the page is away`() {
        handler.keepsAdAcrossPages = true
        loadVisibleBanner()
        AudienzzPrebidMobile.pageImpression("article")
        handler.reloadAd()
        handler.resumeSmartRefresh()
        observer.dispatchOnPreDraw()
        assertEquals(1, responses.size)
    }

    @Test fun `an auction in flight when the page is left is retired, creative kept`() {
        handler.keepsAdAcrossPages = true
        loadVisibleBanner()
        handler.reloadAd()
        assertEquals("a refresh is in flight", 2, responses.size)
        AudienzzPrebidMobile.pageImpression("article")
        responses[1](AudienzzResultCode.NO_BIDS)
        assertEquals("the retired auction never reaches Google", 1, googleLoads)
        AudienzzPrebidMobile.pageImpression("remote")
        observer.dispatchOnPreDraw()
        assertEquals("nothing replaces it on return", 2, responses.size)
    }

    @Test fun `a banner that never loaded is released and re-armed as before`() {
        handler.keepsAdAcrossPages = true
        AudienzzPrebidMobile.blankOnScreenReload = true
        AudienzzPrebidMobile.smartRefreshV2Override = true
        handler.load(withLazyLoading = false) { request, _ -> googleLoads++; requests += request }
        handler.enableSmartRefresh()
        assertEquals(1, responses.size)
        AudienzzPrebidMobile.pageImpression("article")
        responses[0](AudienzzResultCode.NO_BIDS)
        assertEquals("the first auction belonged to the visit that ended", 0, googleLoads)
        AudienzzPrebidMobile.pageImpression("remote")
        observer.dispatchOnPreDraw()
        assertEquals("first load re-armed on return", 2, responses.size)
    }

    private fun waitBeyondTheRefreshInterval() {
        shadowOf(Looper.getMainLooper()).idleFor(31, TimeUnit.SECONDS)
    }

    @Test fun `refresh resumes after a return`() {
        handler.keepsAdAcrossPages = true
        loadVisibleBanner()
        AudienzzPrebidMobile.pageImpression("article")
        waitBeyondTheRefreshInterval()
        assertEquals("no refresh while away", 1, responses.size)
        AudienzzPrebidMobile.pageImpression("remote")
        observer.dispatchOnPreDraw()
        waitBeyondTheRefreshInterval()
        assertEquals("the configured refresh runs again on return", 2, responses.size)
    }

    @Test fun `refresh resumes after a return that retired an auction in flight`() {
        handler.keepsAdAcrossPages = true
        loadVisibleBanner()
        waitBeyondTheRefreshInterval()
        assertEquals("a periodic refresh is in flight", 2, responses.size)
        AudienzzPrebidMobile.pageImpression("article")
        responses[1](AudienzzResultCode.NO_BIDS)
        AudienzzPrebidMobile.pageImpression("remote")
        observer.dispatchOnPreDraw()
        waitBeyondTheRefreshInterval()
        assertEquals("the next refresh still comes", 3, responses.size)
    }

    @Test fun `refresh resumes after the app returns from the background`() {
        handler.keepsAdAcrossPages = true
        loadVisibleBanner()
        val activity = mockk<android.app.Activity>(relaxed = true)
        AppForegroundMonitor.onActivityStarted(activity)
        AppForegroundMonitor.onActivityStopped(activity)
        waitBeyondTheRefreshInterval()
        assertEquals("no refresh in the background", 1, responses.size)
        AppForegroundMonitor.onActivityStarted(activity)
        shadowOf(Looper.getMainLooper()).idle()
        observer.dispatchOnPreDraw()
        assertEquals("foreground recovery keeps the ad", 1, responses.size)
        waitBeyondTheRefreshInterval()
        assertEquals("the configured refresh runs again", 2, responses.size)
    }

    @Test fun `a slot whose request only failed is retried on return, not kept`() {
        handler.keepsAdAcrossPages = true
        AudienzzPrebidMobile.blankOnScreenReload = true
        AudienzzPrebidMobile.smartRefreshV2Override = true
        handler.load(withLazyLoading = false) { request, _ -> googleLoads++; requests += request }
        handler.enableSmartRefresh()
        responses[0](AudienzzResultCode.NO_BIDS)
        val noFill = mockk<com.google.android.gms.ads.LoadAdError>(relaxed = true)
        every { noFill.code } returns com.google.android.gms.ads.AdRequest.ERROR_CODE_NO_FILL
        listener.onAdFailedToLoad(noFill)
        AudienzzPrebidMobile.pageImpression("article")
        AudienzzPrebidMobile.pageImpression("remote")
        observer.dispatchOnPreDraw()
        assertEquals("an empty slot gets a new request on return", 2, responses.size)
    }

    private fun events(type: EventType) = events.filter { it.eventType == type }

    /**
     * A kept banner showing creative A (seen), whose refresh B is loading in Google when the reader
     * leaves. Returns B's auction id.
     */
    private fun leaveWhileBIsLoading(): String {
        handler.keepsAdAcrossPages = true
        loadVisibleBanner()
        listener.onAdImpression()
        observer.dispatchOnPreDraw()
        waitBeyondTheRefreshInterval()
        assertEquals("refresh B started", 2, responses.size)
        responses[1](AudienzzResultCode.NO_BIDS)
        assertEquals("creative B is loading into the view", 2, googleLoads)
        val auctionB = requireNotNull(events(EventType.BID_REQUEST).last().auctionId)
        AudienzzPrebidMobile.pageImpression("article")
        assertTrue(handler.holdPending)
        assertFalse("nothing is counted once the page is away", handler.countsExposure)
        return auctionB
    }

    @Test fun `a Google load in flight when the page is left commits B, counts it only on return`() {
        val auctionB = leaveWhileBIsLoading()
        val placementsBefore = events(EventType.PLACEMENT_VISIBLE).size

        responseId = "B"
        listener.onAdLoaded()
        assertFalse("the hold applied once B completed", handler.holdPending)
        assertTrue(handler.refreshController.blockReasons.contains(RefreshBlockReason.PAGE_INACTIVE))
        assertFalse(handler.refreshController.hasRequestInFlight)
        // Google records B's impression while the article is on top: B's auction is credited,
        // because B is what the slot shows (Google counted it too).
        listener.onAdImpression()
        assertEquals(auctionB, events(EventType.AD_IMPRESSION).last().auctionId)
        observer.dispatchOnPreDraw()
        shadowOf(Looper.getMainLooper()).idleFor(2, TimeUnit.SECONDS)
        assertEquals("no placementVisible while away", placementsBefore, events(EventType.PLACEMENT_VISIBLE).size)
        assertTrue(
            "no viewability while away",
            events(EventType.VIEWABILITY_START).none { it.auctionId == auctionB },
        )
        waitBeyondTheRefreshInterval()
        assertEquals("nothing while away", 2, responses.size)

        AudienzzPrebidMobile.pageImpression("remote")
        observer.dispatchOnPreDraw()
        assertEquals("no auction on return", 2, responses.size)
        assertEquals(
            "B's placementVisible on return, under B's auction",
            auctionB,
            events(EventType.PLACEMENT_VISIBLE).last().auctionId,
        )
        shadowOf(Looper.getMainLooper()).idleFor(2, TimeUnit.SECONDS)
        observer.dispatchOnPreDraw()
        assertTrue(events(EventType.VIEWABILITY_START).any { it.auctionId == auctionB })
        waitBeyondTheRefreshInterval()
        assertEquals("refresh resumes after B", 3, responses.size)
    }

    @Test fun `a Prebid win reported after the hold applied still attributes the kept creative`() {
        val auctionB = leaveWhileBIsLoading()
        responseId = "B"
        listener.onAdLoaded()
        assertFalse("the hold applied", handler.holdPending)
        // GAM reports the Prebid line item after the load completed, then B's impression.
        appEventListener.onAppEvent("Prebid", "")
        listener.onAdImpression()
        val impression = events(EventType.AD_IMPRESSION).last()
        assertEquals(auctionB, impression.auctionId)
        assertEquals("prebid", impression.bidderCode)
    }

    @Test fun `a reload cannot retire the load a hold is waiting for`() {
        val auctionB = leaveWhileBIsLoading()
        handler.reloadAd()
        assertEquals("no new auction", 2, responses.size)
        responseId = "B"
        listener.onAdLoaded()
        listener.onAdImpression()
        assertEquals("B is still credited", auctionB, events(EventType.AD_IMPRESSION).last().auctionId)
        assertFalse(handler.holdPending)
    }

    @Test fun `a failed load still settles the hold, and the kept ad stays`() {
        leaveWhileBIsLoading()
        val noFill = mockk<com.google.android.gms.ads.LoadAdError>(relaxed = true)
        every { noFill.code } returns com.google.android.gms.ads.AdRequest.ERROR_CODE_NO_FILL
        listener.onAdFailedToLoad(noFill)
        assertFalse(handler.holdPending)
        assertFalse(handler.countsExposure)
        AudienzzPrebidMobile.pageImpression("remote")
        observer.dispatchOnPreDraw()
        assertEquals("A is kept: no auction on return", 2, responses.size)
        assertTrue(handler.countsExposure)
    }

    @Test fun `a Google load that never completes still settles the hold`() {
        leaveWhileBIsLoading()
        shadowOf(Looper.getMainLooper()).idleFor(121, TimeUnit.SECONDS)
        assertFalse(handler.holdPending)
        assertEquals("nothing while away", 2, responses.size)
        AudienzzPrebidMobile.pageImpression("remote")
        observer.dispatchOnPreDraw()
        assertEquals("no auction on return", 2, responses.size)
    }

    @Test fun `a background while the hold is pending lets the load complete`() {
        val auctionB = leaveWhileBIsLoading()
        val activity = mockk<android.app.Activity>(relaxed = true)
        AppForegroundMonitor.onActivityStarted(activity)
        AppForegroundMonitor.onActivityStopped(activity)
        responseId = "B"
        listener.onAdLoaded()
        listener.onAdImpression()
        assertEquals(auctionB, events(EventType.AD_IMPRESSION).last().auctionId)
        assertFalse(handler.holdPending)
    }

    @Test fun `an ordinary released banner records no impression`() {
        loadVisibleBanner()
        AudienzzPrebidMobile.pageImpression("article")
        listener.onAdImpression()
        assertTrue(events(EventType.AD_IMPRESSION).isEmpty())
    }
}
