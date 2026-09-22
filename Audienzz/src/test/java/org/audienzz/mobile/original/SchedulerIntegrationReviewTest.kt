package org.audienzz.mobile.original

import android.app.Activity
import android.os.Looper
import android.view.ViewTreeObserver
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.admanager.AdManagerAdView
import io.mockk.*
import org.audienzz.mobile.*
import org.audienzz.mobile.screen.*
import org.audienzz.mobile.util.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class SchedulerIntegrationReviewTest {
    private lateinit var handler: AudienzzAdViewHandler
    private lateinit var view: AdManagerAdView
    private lateinit var unit: AudienzzAdUnit
    private lateinit var host: Activity
    private val responses = mutableListOf<(AudienzzResultCode?) -> Unit>()
    private var gamLoads = 0
    private var gamListener: AdListener = object : AdListener() {}
    @Before fun setup() {
        AppForegroundMonitor.resetForTesting()
        screenAdCoordinatorOverride = ScreenAdCoordinator()
        AudienzzPrebidMobile.observeForegroundReimpression()
        AudienzzPrebidMobile.pageImpression("A")
        host = mockk(relaxed = true)
        view = mockk(relaxed = true)
        every { view.isAttachedToWindow } returns true
        every { view.adListener } answers { gamListener }
        every { view.adListener = any() } answers { gamListener = firstArg() }
        unit = mockk(relaxed = true)
        every { unit.audienzzRefreshIntervalMillis } returns 30_000L
        every { unit.autoRefreshTime } returns 30_000
        every { unit.fetchDemand(any(), any()) } answers { responses.add(secondArg()) }
        handler = AudienzzAdViewHandler(view, unit)
        handler.setScreen("A")
    }
    @After fun cleanup() {
        handler.destroy()
        AudienzzPrebidMobile.pageImpression("cleanup")
        screenAdCoordinatorOverride = null
        AppForegroundMonitor.resetForTesting()
        unmockkAll()
    }
    private fun load(lazy: Boolean = false) { handler.load(withLazyLoading = lazy) { _, _ -> gamLoads++ } }
    private fun idle(ms: Long) { shadowOf(Looper.getMainLooper()).idleFor(ms, TimeUnit.MILLISECONDS) }

    @Test fun `capped banner stays quiet in background and foreground page grants one fresh request`() {
        AppForegroundMonitor.onActivityStarted(host)
        load()
        repeat(11) { index ->
            assertEquals(index + 1, responses.size)
            responses.last()(AudienzzResultCode.NO_BIDS)
            gamListener.onAdLoaded()
            if (index < 10) handler.reloadAd()
        }
        assertTrue(handler.refreshController.blockReasons.contains(org.audienzz.mobile.refresh.RefreshBlockReason.REFRESH_LIMIT))
        AppForegroundMonitor.onActivityStopped(host)
        assertFalse(AppForegroundMonitor.isForeground)
        idle(24 * 60 * 60 * 1000L)
        assertEquals(11, responses.size)
        AppForegroundMonitor.onActivityStarted(host)
        idle(1000)
        assertEquals(12, responses.size)
        assertFalse(handler.refreshController.blockReasons.contains(org.audienzz.mobile.refresh.RefreshBlockReason.REFRESH_LIMIT))
    }

    @Test fun `foreground overdue banner gets only page replacement`() {
        load(); responses[0](AudienzzResultCode.SUCCESS); gamListener.onAdLoaded()
        AppForegroundMonitor.onActivityStopped(host)
        idle(31_000)
        AppForegroundMonitor.onActivityStarted(host)
        idle(1000)
        assertEquals("initial plus exactly one foreground replacement", 2, responses.size)
    }
    @Test fun `first load rejected before activity start recovers after publisher report`() {
        AppForegroundMonitor.onActivityStopped(host)
        AudienzzPrebidMobile.pageImpression("A") // publisher onCreate, before onActivityStarted
        load()
        assertEquals(0, responses.size)
        AppForegroundMonitor.onActivityStarted(host)
        idle(1000)
        assertEquals("foreground must recover first load even when automatic impression is suppressed", 1, responses.size)
    }
    @Test fun `consumed prefetch trigger recovers when native viewport becomes visible`() {
        mockkStatic("org.audienzz.mobile.util.ViewUtilKt")
        lateinit var prefetch: () -> Unit
        lateinit var becameVisible: () -> Unit
        every { view.addPrefetchMarginListener(any(), any()) } answers { prefetch = arg(2) }
        every { view.addContinuousVisibilityListener(any(), any(), any()) } answers {
            becameVisible = arg(2); mockk<ViewTreeObserver.OnPreDrawListener>(relaxed = true)
        }
        every { view.isVisibleForSmartRefresh() } returns false
        every { view.isRefreshEligible() } returns false
        load(lazy = true)
        handler.enableSmartRefresh()
        prefetch()
        becameVisible()
        idle(1000)
        assertEquals("offscreen prefetch must not consume the only first-load trigger", 1, responses.size)
    }
    @Test fun `Prebid timeout followed by successful GAM load does not retry after two seconds`() {
        load()
        responses[0](AudienzzResultCode.TIMEOUT)
        assertEquals(1, gamLoads)
        gamListener.onAdLoaded()
        idle(2100)
        assertEquals("GAM filled the slot; the Prebid timeout must not force another replacement", 1, responses.size)
    }
    @Test fun `controller stays in flight until GAM finishes loading`() {
        load()
        responses[0](AudienzzResultCode.NO_BIDS)
        assertEquals(1, gamLoads)
        assertTrue("Prebid finished but GAM has not; the request pipeline is still in flight", handler.refreshController.hasRequestInFlight)
    }
    @Test fun `changing configured interval after load updates controller`() {
        handler.destroy()
        val realUnit = spyk(AudienzzBannerAdUnit("review", 320, 50,
            java.util.EnumSet.of(org.audienzz.mobile.api.data.AudienzzAdUnitFormat.BANNER)))
        realUnit.setAutoRefreshInterval(30)
        every { realUnit.fetchDemand(any(), any()) } answers { responses.add(secondArg()) }
        handler = AudienzzAdViewHandler(view, realUnit)
        handler.setScreen("A")
        load()
        realUnit.setAutoRefreshInterval(0)
        assertEquals(0L, realUnit.audienzzRefreshIntervalMillis)
        responses[0](AudienzzResultCode.SUCCESS)
        gamListener.onAdLoaded()
        idle(31_000)
        assertEquals("disabled refresh must cancel future periodic requests", 1, responses.size)
    }

    @Test fun `old Google load drains before page replacement starts`() {
        val publisher = mockk<AdListener>(relaxed = true)
        gamListener = publisher
        load(); responses[0](AudienzzResultCode.NO_BIDS)
        AudienzzPrebidMobile.pageImpression("A")
        assertEquals("no overlapping GAM pipeline", 1, responses.size)
        gamListener.onAdLoaded()
        verify(exactly = 0) { publisher.onAdLoaded() }
        assertEquals("replacement starts when stale Google load drains", 2, responses.size)
        assertTrue(handler.refreshController.hasRequestInFlight)
        responses[1](AudienzzResultCode.NO_BIDS)
        gamListener.onAdLoaded()
        assertFalse(handler.refreshController.hasRequestInFlight)
        verify(exactly = 1) { publisher.onAdLoaded() }
    }

    @Test fun `Google no fill waits normal interval while transport error retries`() {
        load(); responses[0](AudienzzResultCode.TIMEOUT)
        gamListener.onAdFailedToLoad(com.google.android.gms.ads.LoadAdError(3, "no fill", "test", null, null))
        idle(2100)
        assertEquals(1, responses.size)
        idle(28_000)
        assertEquals(2, responses.size)
        responses[1](AudienzzResultCode.NO_BIDS)
        gamListener.onAdFailedToLoad(com.google.android.gms.ads.LoadAdError(2, "network", "test", null, null))
        idle(2100)
        assertEquals(3, responses.size)
    }

    @Test fun `never attached banner loads once but does not refresh`() {
        every { view.isAttachedToWindow } returns false
        load(); responses[0](AudienzzResultCode.NO_BIDS); gamListener.onAdLoaded()
        idle(60_000)
        assertEquals(1, responses.size)
    }

    @Test fun `publisher resume recovers a rejected initial load`() {
        handler.stopAutoRefresh()
        load()
        assertEquals(0, responses.size)
        handler.resumeAutoRefresh()
        assertEquals(1, responses.size)
    }

    @Test fun `destroyed banner drops a queued Prebid response`() {
        load()
        handler.destroy()
        responses[0](AudienzzResultCode.NO_BIDS)
        assertEquals(0, gamLoads)
        idle(60_000)
        assertEquals(1, responses.size)
    }
}
