package org.audienzz.mobile

import android.app.Activity
import android.os.Looper
import android.view.View
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.admanager.AdManagerAdRequest
import com.google.android.gms.ads.admanager.AdManagerAdView
import com.google.android.gms.ads.admanager.AdManagerInterstitialAd
import io.mockk.*
import kotlinx.coroutines.test.StandardTestDispatcher
import org.audienzz.mobile.di.MainComponent
import org.audienzz.mobile.event.EventBatcher
import org.audienzz.mobile.event.EventLoggerImpl
import org.audienzz.mobile.event.network.entity.EventNetwork
import org.audienzz.mobile.event.network.mapper.EventNetworkMapper
import org.audienzz.mobile.original.AudienzzAdViewHandler
import org.audienzz.mobile.original.AudienzzInterstitialAdHandler
import org.audienzz.mobile.original.callbacks.AudienzzInterstitialAdLoadCallback
import org.audienzz.mobile.screen.ScreenAdCoordinator
import org.audienzz.mobile.screen.screenAdCoordinatorOverride
import org.audienzz.mobile.util.AppForegroundMonitor
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import java.lang.ref.WeakReference
import java.util.concurrent.TimeUnit

/** Bridge calls decode equal route values into distinct objects; string literals hide that. */
@RunWith(RobolectricTestRunner::class)
class BridgePageRecoveryTest {
    private val dispatcher = StandardTestDispatcher()
    private val events = mutableListOf<EventNetwork>()
    private val requests = mutableListOf<AdManagerAdRequest>()
    private val replies = mutableListOf<(AudienzzResultCode?) -> Unit>()
    private val updates = mutableListOf<String>()
    private lateinit var logger: EventLoggerImpl
    private lateinit var coordinator: ScreenAdCoordinator
    private lateinit var handler: AudienzzAdViewHandler
    private lateinit var creative: View
    private lateinit var host: Activity
    private var googleListener = object : AdListener() {}
    private lateinit var installedBannerListener: AdListener
    private var previousBlanking = false

    @Before fun setup() {
        AppForegroundMonitor.resetForTesting()
        AudienzzPrebidMobile.sdkInitializedOverride = true
        previousBlanking = AudienzzPrebidMobile.blankOnScreenReload
        AudienzzPrebidMobile.blankOnScreenReload = true
        coordinator = ScreenAdCoordinator()
        screenAdCoordinatorOverride = coordinator
        val sender = mockk<EventBatcher>(relaxed = true)
        every { sender.enqueue(capture(events)) } just Runs
        logger = EventLoggerImpl(sender, EventNetworkMapper(RuntimeEnvironment.getApplication()),
            mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), dispatcher)
        mockkObject(MainComponent.Companion)
        every { MainComponent.eventLogger } returns logger

        // Same values, separately decoded allocations. Neither the display name nor the banner
        // retains the original pageImpression token as they would with an interned literal.
        AudienzzPrebidMobile.pageImpression(String("bridge-route-1".toCharArray()), "Article")
        val bannerRoute = String("bridge-route-1".toCharArray())
        assertNotSame(bannerRoute, coordinator.activeScreen)
        assertEquals(bannerRoute, coordinator.activeScreen)
        AudienzzPrebidMobile.pageImpressionObserver = { updates += it }
        host = mockk(relaxed = true)
        creative = View(RuntimeEnvironment.getApplication())
        val view = mockk<AdManagerAdView>(relaxed = true)
        every { view.isAttachedToWindow } returns true
        every { view.visibility } returns View.VISIBLE
        every { view.measuredHeight } returns 50
        every { view.adUnitId } returns "/fixture/banner"
        every { view.responseInfo } returns null
        every { view.childCount } returns 1
        every { view.getChildAt(0) } returns creative
        every { view.getGlobalVisibleRect(any()) } answers {
            firstArg<android.graphics.Rect>().set(0, 0, 320, 50); true
        }
        installedBannerListener = googleListener
        every { view.adListener } answers { installedBannerListener }
        every { view.adListener = any() } answers { installedBannerListener = firstArg() }
        val unit = mockk<AudienzzAdUnit>(relaxed = true)
        every { unit.fetchDemand(any(), any()) } answers { replies += secondArg<(AudienzzResultCode?) -> Unit>() }
        handler = AudienzzAdViewHandler(view, unit)
        handler.setScreen(bannerRoute)
        handler.load(withLazyLoading = false) { request, _ -> requests += request }
        assertEquals(1, replies.size)
        finishBannerLoad()
    }

    @After fun cleanup() {
        handler.destroy()
        AudienzzPrebidMobile.pageImpressionObserver = null
        AudienzzPrebidMobile.pageImpression("cleanup") // cancel any pending delayed recovery
        AudienzzPrebidMobile.blankOnScreenReload = previousBlanking
        AudienzzPrebidMobile.sdkInitializedOverride = null
        screenAdCoordinatorOverride = null
        AppForegroundMonitor.resetForTesting()
        unmockkAll()
    }

    private fun finishBannerLoad() {
        replies.last()(AudienzzResultCode.NO_BIDS)
        installedBannerListener.onAdLoaded()
        installedBannerListener.onAdImpression()
    }

    /** Model collection of weak-only storage without relying on nondeterministic JVM GC timing. */
    private fun clearWeakScreenStorage() {
        val field = ScreenAdCoordinator::class.java.getDeclaredField("activeScreenRef")
        field.isAccessible = true
        (field.get(coordinator) as? WeakReference<*>)?.clear()
    }

    private fun assertReplacement(cycle: Int) {
        assertEquals(cycle + 1, replies.size)
        assertEquals(cycle, requests.size) // new Google handoff still awaits Prebid
        assertEquals(View.INVISIBLE, creative.visibility)
        finishBannerLoad()
        assertEquals(View.VISIBLE, creative.visibility)
        val first = requests.first().customTargeting
        val last = requests.last().customTargeting
        for (key in listOf("au_page_seq", "au_slot")) {
            assertNotNull(first.getString(key))
            assertEquals(first.getString(key), last.getString(key))
        }
        assertEquals(cycle.toString(), last.getString("hb_refresh_count"))
        assertEquals(1, coordinator.epoch)
    }

    @Test fun `foreground still blanks and reloads after bridge token loses weak references`() {
        val page = logger.capturePageContext()
        assertNotNull(page.pageImpressionId)
        clearWeakScreenStorage()
        repeat(3) { index ->
            AppForegroundMonitor.onActivityStopped(host)
            AppForegroundMonitor.onActivityStarted(host)
            shadowOf(Looper.getMainLooper()).idleFor(500, TimeUnit.MILLISECONDS)
            assertReplacement(index + 1)
            assertEquals(page, logger.capturePageContext())
        }
        assertEquals(List(3) { "bridge-route-1" }, updates)
        dispatcher.scheduler.runCurrent()
        assertEquals(1, events.count { it.eventType == "pageImpression" })
        assertEquals(4, events.count { it.eventType == "bidRequest" })
    }

    @Test fun `installed interstitial callbacks recover after bridge token loses weak references`() {
        val page = logger.capturePageContext()
        assertNotNull(page.pageImpressionId)
        clearWeakScreenStorage()
        repeat(3) { index ->
            val unit = mockk<AudienzzInterstitialAdUnit>(relaxed = true)
            every { unit.fetchDemand(any(), any()) } answers {
                secondArg<(AudienzzResultCode?) -> Unit>()(AudienzzResultCode.NO_BIDS)
            }
            val ad = mockk<AdManagerInterstitialAd>(relaxed = true)
            var installed: FullScreenContentCallback? = null
            every { ad.fullScreenContentCallback } answers { installed }
            every { ad.fullScreenContentCallback = any() } answers { installed = firstArg() }
            AudienzzInterstitialAdHandler(unit, "/fixture/interstitial").load(
                adLoadCallback = object : AudienzzInterstitialAdLoadCallback() {},
                resultCallback = { _, _, callback -> callback.onAdLoaded(ad) })
            val callback = requireNotNull(installed)
            callback.onAdShowedFullScreenContent()
            assertEquals(index + 1, replies.size)
            callback.onAdDismissedFullScreenContent()
            callback.onAdDismissedFullScreenContent()
            assertReplacement(index + 1)
            assertEquals(page, logger.capturePageContext())
        }
        assertEquals(List(3) { "bridge-route-1" }, updates)
        dispatcher.scheduler.runCurrent()
        assertEquals(1, events.count { it.eventType == "pageImpression" })
        assertEquals(7, events.count { it.eventType == "bidRequest" }) // four banner + three interstitial
    }

    @Test fun `navigation replaces the retained route and native hosts stay weak`() {
        coordinator.onScreenResumed(String("bridge-route-2".toCharArray()), "Article")
        clearWeakScreenStorage()
        assertEquals("bridge-route-2", coordinator.activeScreen)
        coordinator.onScreenResumed(host, "Native screen")
        assertSame(host, coordinator.activeScreen)
        clearWeakScreenStorage()
        assertNull("Neither the old route nor the native host may be retained", coordinator.activeScreen)
    }
}
