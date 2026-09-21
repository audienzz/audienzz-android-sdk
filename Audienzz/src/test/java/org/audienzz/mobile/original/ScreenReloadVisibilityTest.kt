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

        // User scrolls back to banner #1. No manual resume: the installed native listener must recover it.
        inViewport = true
        observer.dispatchOnPreDraw()
        assertEquals("the visible returning slot must request its pending page replacement", 2, responses.size)
        responses[1](AudienzzResultCode.NO_BIDS)
        listener.onAdLoaded()
        assertEquals(2, googleLoads)
        assertEquals(View.VISIBLE, viewVisibility)
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
        assertEquals("control: an accepted replacement is blanked", View.INVISIBLE, viewVisibility)
        observer.dispatchOnPreDraw()
        assertTrue(handler.refreshController.blockReasons.contains(RefreshBlockReason.NOT_VISIBLE))

        // Leave again before the replacement's Prebid response. No Google callback can restore it.
        AudienzzPrebidMobile.pageImpression("legacy")
        assertEquals("retiring the request must retire its temporary blank", View.VISIBLE, viewVisibility)
        responses[1](AudienzzResultCode.NO_BIDS)
        assertEquals("the retired auction must not load Google", 1, googleLoads)
        AudienzzPrebidMobile.pageImpression("remote")
        observer.dispatchOnPreDraw()
        assertEquals("the next visit must not inherit the cancelled request's invisibility", 3, responses.size)
        responses[2](AudienzzResultCode.NO_BIDS)
        listener.onAdLoaded()
        assertEquals(2, googleLoads)
        assertEquals(View.VISIBLE, viewVisibility)
    }

    @Test fun `v2 a cancelled replacement releases its temporary blank`() = cancelledReplacementRecovers(v2 = true)

    @Test fun `v1 a cancelled replacement releases its temporary blank`() = cancelledReplacementRecovers(v2 = false)

}
