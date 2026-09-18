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
class CodexCoverRecheckTest {
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


    @Test fun `prefetched unimpressed creative is overdue at first visibility`() {
        load()
        responses[0](AudienzzResultCode.NO_BIDS)
        gamListener.onAdLoaded() // Google returned a creative; intentionally no impression yet.
        handler.pauseSmartRefresh()
        idle(35_000)
        assertEquals("hidden hold protects the cached creative", 1, responses.size)
        handler.resumeSmartRefresh()
        idle(1)
        assertEquals("CURRENT POLICY: returning starts a replacement before any impression", 2, responses.size)
        responses[1](AudienzzResultCode.NO_BIDS)
        assertEquals("both auctions reached Google", 2, gamLoads)
    }
    @Test fun `Flutter native path needs an external visibility pause to stop periodic requests`() {
        // Flutter does not call enableSmartRefresh: Dart supplies the only viewport verdict.
        load(); responses[0](AudienzzResultCode.NO_BIDS); gamListener.onAdLoaded()
        idle(30_001)
        assertEquals("without a false host verdict another auction starts", 2, responses.size)
        responses[1](AudienzzResultCode.NO_BIDS); gamListener.onAdLoaded()
        handler.pauseSmartRefresh()
        idle(120_000)
        assertEquals("CONTROL: actually delivering false stops subsequent auctions", 2, responses.size)
    }
    /**
     * The two holds are independent and either may be cleared first. Whichever order the host
     * happens to produce, the outcome must be the same: exactly one overdue request, not two and
     * not none. Added alongside the imported probe, which covers only the attach-then-resume order.
     */
    @Test fun `clearing the holds in either order recovers exactly one request`() {
        load(); responses[0](AudienzzResultCode.NO_BIDS); gamListener.onAdLoaded()
        assertEquals("fixture must complete the first load", 1, responses.size)
        val listeners = mutableListOf<android.view.View.OnAttachStateChangeListener>()
        verify { view.addOnAttachStateChangeListener(capture(listeners)) }
        assertTrue("fixture must use the installed listener", listeners.isNotEmpty())

        handler.pauseSmartRefresh()
        listeners.forEach { it.onViewDetachedFromWindow(view) }
        idle(35_000)
        assertEquals("both holds suppress the overdue refresh", 1, responses.size)

        // Reverse of the imported probe: host visibility first, attachment second.
        handler.resumeSmartRefresh()
        idle(1)
        assertEquals("a detached view must not auction", 1, responses.size)
        listeners.forEach { it.onViewAttachedToWindow(view) }
        idle(1)
        assertEquals("exactly one overdue request once both holds are clear", 2, responses.size)

        idle(1)
        assertEquals("and it does not repeat", 2, responses.size)
    }

    @Test fun `reattachment does not clear a pause left by the previous Flutter widget`() {
        load(); responses[0](AudienzzResultCode.NO_BIDS); gamListener.onAdLoaded()
        val listeners = mutableListOf<android.view.View.OnAttachStateChangeListener>()
        verify { view.addOnAttachStateChangeListener(capture(listeners)) }
        assertTrue("fixture must use the installed listener", listeners.isNotEmpty())
        handler.pauseSmartRefresh()
        listeners.forEach { it.onViewDetachedFromWindow(view) }
        idle(35_000)
        listeners.forEach { it.onViewAttachedToWindow(view) }
        idle(1)
        assertEquals("attach alone cannot resume the host hold", 1, responses.size)
        handler.resumeSmartRefresh()
        idle(1)
        assertEquals("CONTROL: a new widget's true verdict would recover exactly once", 2, responses.size)
    }

    @Test fun `RN cover survives the native viewport becoming visible again`() {
        var visible = true
        val observer = mockk<ViewTreeObserver>(relaxed = true)
        val preDraw = mutableListOf<ViewTreeObserver.OnPreDrawListener>()
        every { view.viewTreeObserver } returns observer
        every { observer.addOnPreDrawListener(any()) } answers { preDraw.add(firstArg()) }
        every { view.visibility } returns android.view.View.VISIBLE
        every { view.measuredHeight } returns 50
        every { view.getGlobalVisibleRect(any()) } answers {
            firstArg<android.graphics.Rect>().set(0,0,320,50)
            visible
        }
        load(); responses[0](AudienzzResultCode.NO_BIDS); gamListener.onAdLoaded()
        handler.enableSmartRefresh()
        assertTrue(preDraw.isNotEmpty())
        // RCTRemoteConfigBannerView.setCovered(true) now calls RemoteBanner.setHostCover(true),
        // which forwards exactly this public handler method. It used to call onPause(), i.e.
        // pauseSmartRefresh() — the same NOT_VISIBLE reason the geometry listener writes, which is
        // what this probe caught. Only the call is updated; the assertion is unchanged.
        handler.pauseForHostCover()
        idle(35_000)
        assertEquals(1,responses.size)
        visible=false; preDraw.forEach{it.onPreDraw()}
        visible=true; preDraw.forEach{it.onPreDraw()}
        idle(1)
        assertEquals("still covered: no publisher reportCover(false) occurred",1,responses.size)
    }

    @Test fun `clearing an RN cover does not clear an offscreen viewport hold`() {
        val observer=mockk<ViewTreeObserver>(relaxed=true)
        every {view.viewTreeObserver} returns observer
        every {view.getGlobalVisibleRect(any())} returns false
        every {view.visibility} returns android.view.View.VISIBLE
        every {view.measuredHeight} returns 50
        load();responses[0](AudienzzResultCode.NO_BIDS);gamListener.onAdLoaded()
        handler.enableSmartRefresh()
        handler.pauseForHostCover()
        idle(35_000)
        assertEquals(1,responses.size)
        handler.resumeFromHostCover() // setCovered(false) -> RemoteBanner.setHostCover(false)
        idle(1)
        assertEquals("viewport is still offscreen",1,responses.size)
    }

    /**
     * Kept, with the assertion inverted to what native actually guarantees — and must.
     *
     * The review that added this case says so itself: two explicit page impressions are two
     * transitions, and deduplicating them here would break a deliberate repeat report, which an
     * earlier review required to keep working. So the cost of double-reporting is real and is
     * measured here; the fix belongs in the managed integration, which must emit ONE activation
     * for one navigation. That half is pinned on the bridges — see the composition tests in
     * managed_banner_test.dart and managedBanner.test.tsx.
     */
    @Test fun `two explicit page reports legitimately buy two replacements`() {
        load();responses[0](AudienzzResultCode.NO_BIDS);gamListener.onAdLoaded()
        assertEquals(1,responses.size)
        AudienzzPrebidMobile.pageImpression("A")
        AudienzzPrebidMobile.pageImpression("A")
        assertEquals(
            "native must not swallow a deliberate repeat; the integration must not cause one",
            3,
            responses.size,
        )
    }
}
