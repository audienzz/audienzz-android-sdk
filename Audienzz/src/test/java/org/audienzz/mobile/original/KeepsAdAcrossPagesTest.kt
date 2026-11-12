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

/** `keepsAdAcrossPages`: a page transition pauses the banner instead of releasing and reloading it. */
@RunWith(RobolectricTestRunner::class)
class KeepsAdAcrossPagesTest {
    private lateinit var handler: AudienzzAdViewHandler
    private lateinit var view: AdManagerAdView
    private lateinit var observer: ViewTreeObserver
    private var listener: AdListener = object : AdListener() {}
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
}
