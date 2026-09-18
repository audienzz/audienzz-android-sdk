package org.audienzz.mobile.original

import android.app.Activity
import android.os.Looper
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.admanager.AdManagerAdView
import io.mockk.*
import org.audienzz.mobile.*
import org.audienzz.mobile.refresh.RefreshBlockReason
import org.audienzz.mobile.screen.*
import org.audienzz.mobile.util.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.util.concurrent.TimeUnit

/**
 * The lifecycle guarantees the managed integration rests on, counted at the native boundary:
 * `responses` is one entry per Prebid auction, `gamLoads` one per Google handoff. A Dart or JS
 * callback count cannot establish that nothing was requested.
 *
 * The one that matters most is the first-load prefetch exemption. It exists so a banner can auction
 * before it is attached or visible — and it must never be allowed to mean "ignore every block",
 * because background, inactive page, destroyed and publisher-stop are not geometry.
 */
@RunWith(RobolectricTestRunner::class)
class LifecycleGuaranteeTest {
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

    private fun load() { handler.load(withLazyLoading = false) { _, _ -> gamLoads++ } }
    private fun idle(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(ms, TimeUnit.MILLISECONDS)
    private fun background() = AppForegroundMonitor.onActivityStopped(host)
    private fun foreground() = AppForegroundMonitor.onActivityStarted(host)

    // region The background gate

    @Test fun `backgrounding before the first load stops the auction entirely`() {
        background()
        load()
        idle(1)
        assertEquals("the first-load exemption is geometry only, not lifecycle", 0, responses.size)
        assertEquals(0, gamLoads)
    }

    @Test fun `a response arriving after backgrounding starts no Google handoff`() {
        load()
        assertEquals("control: the auction started", 1, responses.size)
        background()
        // The request was already in flight; it is allowed to finish. What must not happen is a
        // Google load for a creative nobody can see.
        responses[0](AudienzzResultCode.NO_BIDS)
        idle(1)
        assertEquals("no Google handoff once the gate is closed", 0, gamLoads)
    }

    @Test fun `staying backgrounded across several refresh intervals buys nothing`() {
        load()
        responses[0](AudienzzResultCode.NO_BIDS)
        gamListener.onAdLoaded()
        assertEquals(1, gamLoads)

        background()
        idle(5 * 30_000)

        assertEquals("no periodic auction while backgrounded", 1, responses.size)
        assertEquals(1, gamLoads)
    }

    @Test fun `a late response cannot restart refresh after backgrounding`() {
        load()
        background()
        responses[0](AudienzzResultCode.NO_BIDS)
        idle(3 * 30_000)
        assertEquals("a stale callback must not rearm the scheduler", 1, responses.size)
    }

    // endregion

    // region The first-load exemption never bypasses lifecycle state

    @Test fun `a publisher stop blocks the first load`() {
        handler.stopAutoRefresh()
        load()
        idle(1)
        assertEquals(0, responses.size)

        handler.resumeAutoRefresh()
        idle(1)
        assertEquals("and the first load arrives once the publisher clears it", 1, responses.size)
    }

    @Test fun `an inactive page blocks the first load`() {
        AudienzzPrebidMobile.pageImpression("B")
        load()
        idle(1)
        assertEquals(0, responses.size)
    }

    @Test fun `a destroyed handler blocks the first load`() {
        handler.destroy()
        load()
        idle(1)
        assertEquals(0, responses.size)
    }

    @Test fun `geometry blocks are exactly the ones the first load may ignore`() {
        // Detached and not-visible are what prefetching is FOR.
        every { view.isAttachedToWindow } returns false
        handler.pauseSmartRefresh()
        load()
        idle(1)
        assertEquals("a prefetch may precede attachment and visibility", 1, responses.size)
    }

    // endregion

    // region Recovery ordering

    @Test fun `foreground then page activation recovers exactly once`() {
        load()
        responses[0](AudienzzResultCode.NO_BIDS)
        gamListener.onAdLoaded()
        background()
        idle(60_000)
        assertEquals(1, responses.size)

        // The app reports its own page on resume, which is the common pattern. The automatic
        // foreground impression must stand down rather than adding a second replacement.
        foreground()
        AudienzzPrebidMobile.pageImpression("A")
        idle(1_000)

        assertEquals("one return, one replacement", 2, responses.size)
    }

    @Test fun `foreground alone recovers exactly once when the app reports nothing`() {
        load()
        responses[0](AudienzzResultCode.NO_BIDS)
        gamListener.onAdLoaded()
        background()
        idle(60_000)
        assertEquals(1, responses.size)

        foreground()
        idle(5_000)

        assertEquals("the automatic impression owns the recovery", 2, responses.size)
    }

    @Test fun `page activation then foreground recovers exactly once`() {
        load()
        responses[0](AudienzzResultCode.NO_BIDS)
        gamListener.onAdLoaded()
        background()
        idle(60_000)
        assertEquals(1, responses.size)

        // The reverse order. Both must converge on the same single recovery.
        AudienzzPrebidMobile.pageImpression("A")
        foreground()
        idle(1_000)

        assertEquals("one return, one replacement", 2, responses.size)
    }

    // endregion

    @Test fun `the publisher stop survives a page activation`() {
        // Independent blocks: activating a page must not clear a publisher pause.
        load()
        responses[0](AudienzzResultCode.NO_BIDS)
        gamListener.onAdLoaded()
        handler.stopAutoRefresh()

        AudienzzPrebidMobile.pageImpression("A")
        idle(60_000)

        assertEquals("only resumeAutoRefresh clears a publisher stop", 1, responses.size)
    }
}
