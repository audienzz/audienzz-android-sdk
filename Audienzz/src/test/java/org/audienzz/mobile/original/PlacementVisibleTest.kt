package org.audienzz.mobile.original

import android.app.Activity
import android.graphics.Rect
import android.os.Looper
import android.view.View
import android.view.ViewTreeObserver
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.admanager.AdManagerAdView
import io.mockk.*
import org.audienzz.mobile.*
import org.audienzz.mobile.event.entity.EventDomain
import org.audienzz.mobile.event.entity.EventType
import org.audienzz.mobile.screen.*
import org.audienzz.mobile.util.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.util.concurrent.TimeUnit

/**
 * `placementVisible`, the fill-rate denominator: once per auction, the first time at least one
 * pixel of the slot is on screen after the auction ended with the app in the foreground. Nothing
 * for a slot that never shows; the next auction or leaving the page closes the window.
 */
@RunWith(RobolectricTestRunner::class)
class PlacementVisibleTest {
    private lateinit var handler: AudienzzAdViewHandler
    private lateinit var view: AdManagerAdView
    private lateinit var unit: AudienzzAdUnit
    private lateinit var host: Activity
    private lateinit var events: MutableList<EventDomain>
    private val responses = mutableListOf<(AudienzzResultCode?) -> Unit>()
    private var gamListener: AdListener = object : AdListener() {}
    // Every tracker on the view (placement and viewability) registers its own listener.
    private val draws = mutableListOf<ViewTreeObserver.OnPreDrawListener>()

    @Before fun setup() {
        AudienzzPrebidMobile.sdkInitializedOverride = true
        AppForegroundMonitor.resetForTesting()
        screenAdCoordinatorOverride = ScreenAdCoordinator()
        AudienzzPrebidMobile.observeForegroundRecovery()
        AudienzzPrebidMobile.pageImpression("A")
        host = mockk(relaxed = true)
        view = mockk(relaxed = true)
        every { view.isAttachedToWindow } returns true
        every { view.responseInfo } returns null
        every { view.adListener } answers { gamListener }
        every { view.adListener = any() } answers { gamListener = firstArg() }
        every { view.viewTreeObserver.addOnPreDrawListener(any()) } answers { draws.add(firstArg()) }
        every { view.viewTreeObserver.removeOnPreDrawListener(any()) } answers { draws.remove(firstArg()) }
        every { view.viewTreeObserver.isAlive } returns true
        unit = mockk(relaxed = true)
        every { unit.configId } returns "placement-code"
        every { unit.audienzzRefreshIntervalMillis } returns 30_000L
        every { unit.autoRefreshTime } returns 30_000
        every { unit.fetchDemand(any(), any()) } answers { responses.add(secondArg()) }
        events = mutableListOf()
        val logger = mockk<org.audienzz.mobile.event.EventLogger>(relaxed = true)
        every { logger.logEvent(capture(events)) } just Runs
        mockkObject(org.audienzz.mobile.di.MainComponent.Companion)
        every { org.audienzz.mobile.di.MainComponent.eventLogger } returns logger
        handler = AudienzzAdViewHandler(view, unit)
        handler.setScreen("A")
        hidden()
    }

    @After fun cleanup() {
        AudienzzPrebidMobile.sdkInitializedOverride = null
        handler.destroy()
        AudienzzPrebidMobile.pageImpression("cleanup")
        screenAdCoordinatorOverride = null
        AppForegroundMonitor.resetForTesting()
        unmockkAll()
    }

    private fun idle(ms: Long) { shadowOf(Looper.getMainLooper()).idleFor(ms, TimeUnit.MILLISECONDS) }
    private fun placements() = events.filter { it.eventType == EventType.PLACEMENT_VISIBLE }
    private var baseline = 0
    private fun load() { handler.load(withLazyLoading = false) { _, _ -> }; baseline = draws.size }
    /** Whether a placement tracker is still observing frames, beside the slot's other listeners. */
    private fun watching() = draws.size > baseline
    private fun auctionEndsFilled() { responses.last()(AudienzzResultCode.NO_BIDS); gamListener.onAdLoaded() }
    private fun noFill(): LoadAdError = mockk(relaxed = true) { every { code } returns AdRequest.ERROR_CODE_NO_FILL }

    /** [pixels] rows of a 250px slot on screen. */
    private fun onScreen(pixels: Int = 250) {
        every { view.visibility } returns View.VISIBLE
        every { view.measuredHeight } returns 250
        every { view.getGlobalVisibleRect(any()) } answers { firstArg<Rect>().set(0, 0, 300, pixels); true }
        draws.toList().forEach { it.onPreDraw() }
    }

    private fun hidden() {
        every { view.getGlobalVisibleRect(any()) } returns false
        draws.toList().forEach { it.onPreDraw() }
    }

    @Test fun sentOnceWithTheAuctionOfItsImpression() {
        load(); auctionEndsFilled(); gamListener.onAdImpression()
        assertTrue(placements().isEmpty())
        onScreen(); hidden(); onScreen(); idle(2_000)
        val placement = placements().single()
        val impression = events.single { it.eventType == EventType.AD_IMPRESSION }
        assertEquals(impression.auctionId, placement.auctionId)
        assertNotNull(placement.auctionId)
        assertEquals(0, placement.slotReload)
        assertEquals("placement-code", placement.adUnitCode)
    }

    @Test fun onePixelIsEnough() {
        load(); auctionEndsFilled()
        assertTrue(watching())
        onScreen(pixels = 1)
        assertEquals(1, placements().size)
        // Sent means done: the slot stops watching instead of re-checking every frame.
        assertFalse(watching())
    }

    @Test fun visibleBeforeTheAuctionEndsWaitsForItsEnd() {
        load(); onScreen()
        assertTrue(placements().isEmpty())
        auctionEndsFilled()
        assertEquals(1, placements().size)
    }

    @Test fun aNoFillThatShowsIsSentToo() {
        load(); responses.last()(AudienzzResultCode.NO_BIDS); gamListener.onAdFailedToLoad(noFill())
        onScreen()
        assertEquals(1, placements().size)
    }

    @Test fun aSlotThatNeverShowsSendsNothingAndRefreshClosesItsAuction() {
        load(); auctionEndsFilled(); idle(1_000)
        val first = requireNotNull(events.single { it.eventType == EventType.BID_REQUEST }.auctionId)
        handler.reloadAd()
        onScreen()
        // The first auction was never seen; the refresh's own auction has not ended yet.
        assertTrue(placements().isEmpty())
        auctionEndsFilled()
        val placement = placements().single()
        assertNotEquals(first, placement.auctionId)
        assertEquals(1, placement.slotReload)
    }

    @Test fun leavingThePageClosesTheAuction() {
        load(); auctionEndsFilled()
        assertTrue(watching())
        AudienzzPrebidMobile.pageImpression("B")
        assertFalse(watching())
        onScreen()
        assertTrue(placements().isEmpty())
    }

    @Test fun waitsForTheForeground() {
        AppForegroundMonitor.onActivityStarted(host)
        load(); auctionEndsFilled()
        AppForegroundMonitor.onActivityStopped(host)
        onScreen()
        assertTrue(placements().isEmpty())
        AppForegroundMonitor.onActivityStarted(host)
        assertEquals(1, placements().size)
        AppForegroundMonitor.onActivityStopped(host); AppForegroundMonitor.onActivityStarted(host)
        assertEquals(1, placements().size)
    }

    @Test fun destroyClosesTheAuction() {
        load(); auctionEndsFilled()
        assertTrue(watching())
        handler.destroy()
        assertFalse(watching())
        onScreen()
        assertTrue(placements().isEmpty())
    }
}
