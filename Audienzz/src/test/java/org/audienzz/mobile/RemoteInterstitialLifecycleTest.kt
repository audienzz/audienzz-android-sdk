package org.audienzz.mobile

import android.app.Activity
import android.os.Looper
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.admanager.AdManagerAdRequest
import com.google.android.gms.ads.admanager.AdManagerInterstitialAd
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import org.audienzz.mobile.api.config.*
import org.audienzz.mobile.di.MainComponent
import org.audienzz.mobile.manager.RemoteConfigManager
import org.audienzz.mobile.original.AudienzzInterstitialAdHandler
import org.audienzz.mobile.original.callbacks.*
import org.audienzz.mobile.util.AppForegroundMonitor
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class RemoteInterstitialLifecycleTest {
    private lateinit var owner: AudienzzRemoteConfigInterstitial
    private lateinit var activity: Activity
    private lateinit var loaded: AudienzzInterstitialAdLoadCallback
    private lateinit var fullscreen: AudienzzFullScreenContentCallback
    private lateinit var handoff: (AudienzzResultCode?, AdManagerAdRequest, AudienzzInterstitialAdLoadCallback) -> Unit
    private val events = mockk<AudienzzRemoteConfigInterstitial.Events>(relaxed = true)
    private var loads = 0

    @Before fun setup() {
        AppForegroundMonitor.resetForTesting()
        loads = 0
        activity = mockk(relaxed = true)
        val config = RemoteAdUnitConfig(1, RemoteConfig("interstitial"),
            RemoteGamConfig("/probe", emptyList()), RemotePrebidConfig("probe", emptyList()))
        val manager = mockk<RemoteConfigManager>()
        coEvery { manager.getAdUnitConfig(any()) } returns config
        mockkObject(MainComponent.Companion)
        every { MainComponent.remoteConfigManager } returns manager
        mockkConstructor(AudienzzInterstitialAdHandler::class)
        every { anyConstructed<AudienzzInterstitialAdHandler>().load(any(), any(), any(), any()) } answers {
            loads++
            loaded = secondArg()
            fullscreen = thirdArg()
            handoff = arg(3)
        }
        owner = AudienzzRemoteConfigInterstitial(activity, "probe", events)
        owner.configDispatcher = Dispatchers.Main
    }
    @After fun cleanup() {
        owner.destroy()
        if (::fullscreen.isInitialized) fullscreen.onAdDismissedFullScreenContent()
        AppForegroundMonitor.resetForTesting()
        unmockkAll()
    }
    private fun prefetchAndShow() { owner.prefetchAndShow(); shadowOf(Looper.getMainLooper()).idle() }

    @Test fun `duplicate load coalesces inventory and auto shows once`() {
        prefetchAndShow(); prefetchAndShow()
        assertEquals(1, loads)
        val ad = mockk<AdManagerInterstitialAd>(relaxed = true)
        loaded.onAdLoaded(ad)
        verify(exactly = 1) { ad.show(activity) }
        prefetchAndShow()
        assertEquals(1, loads)
        fullscreen.onAdDismissedFullScreenContent()
        prefetchAndShow()
        assertEquals(2, loads)
    }

    @Test fun `destroy before Google completion cannot auto show`() {
        prefetchAndShow()
        owner.destroy()
        val ad = mockk<AdManagerInterstitialAd>(relaxed = true)
        loaded.onAdLoaded(ad)
        verify(exactly = 0) { ad.show(any()) }
        verify(exactly = 0) { events.onLoaded() }
    }

    @Test fun `destroy before demand handoff prevents Google request`() {
        prefetchAndShow()
        owner.destroy()
        mockkStatic(AdManagerInterstitialAd::class)
        handoff(AudienzzResultCode.NO_BIDS, AdManagerAdRequest.Builder().build(), loaded)
        verify(exactly = 0) { AdManagerInterstitialAd.load(any<android.content.Context>(), any<String>(), any<AdManagerAdRequest>(), any<com.google.android.gms.ads.admanager.AdManagerInterstitialAdLoadCallback>()) }
    }

    @Test fun `destroy from loaded callback cancels pending automatic presentation`() {
        prefetchAndShow()
        every { events.onLoaded() } answers { owner.destroy() }
        val ad = mockk<AdManagerInterstitialAd>(relaxed = true)
        loaded.onAdLoaded(ad)
        verify(exactly = 0) { ad.show(any()) }
    }

    @Test fun `background at completion reports failure without delayed automatic show`() {
        prefetchAndShow()
        AppForegroundMonitor.onActivityStarted(activity)
        AppForegroundMonitor.onActivityStopped(activity)
        val ad = mockk<AdManagerInterstitialAd>(relaxed = true)
        loaded.onAdLoaded(ad)
        verify(exactly = 0) { ad.show(any()) }
        // The publisher is told WHY, not merely that something went wrong: `inactive` is the same
        // skip reason an explicit show() would report for the same state.
        verify { events.onError(match { it.contains("inactive") }) }
        verify { events.onLifecycleEvent(match { it["event"] == "opportunitySkipped" && it["reason"] == "inactive" }) }
        AppForegroundMonitor.onActivityStarted(activity)
        verify(exactly = 0) { ad.show(any()) }
    }

    @Test fun `destroy while presenting preserves terminal callback`() {
        prefetchAndShow()
        loaded.onAdLoaded(mockk(relaxed = true))
        owner.destroy()
        fullscreen.onAdFailedToShowFullScreenContent(AdError(1, "error", "google"))
        verify(exactly = 1) { events.onFailedToShow(any()) }
        prefetchAndShow()
        assertEquals(1, loads)
    }
    private fun prefetch() { owner.prefetch(); shadowOf(Looper.getMainLooper()).idle() }

    @Test fun `prefetch retains inventory and never remembers a missed opportunity`() {
        prefetch(); prefetch()
        assertFalse(owner.show(activity, eligible = true))
        val ad = mockk<AdManagerInterstitialAd>(relaxed = true)
        loaded.onAdLoaded(ad)
        prefetch()
        assertEquals(1, loads)
        assertTrue(owner.isReady)
        verify(exactly = 0) { ad.show(any()) }
        assertFalse(owner.show(activity, eligible = false))
        assertTrue(owner.isReady)
        assertTrue(owner.show(activity, eligible = true))
        assertFalse(owner.show(activity, eligible = true))
        verify(exactly = 1) { ad.show(activity) }
    }

    @Test fun `background skips opportunity without discarding ready inventory`() {
        prefetch()
        val ad = mockk<AdManagerInterstitialAd>(relaxed = true)
        loaded.onAdLoaded(ad)
        AppForegroundMonitor.onActivityStarted(activity)
        AppForegroundMonitor.onActivityStopped(activity)
        assertFalse(owner.show(activity, true))
        assertTrue(owner.isReady)
        AppForegroundMonitor.onActivityStarted(activity)
        verify(exactly = 0) { ad.show(any()) }
        assertTrue(owner.show(activity, true))
    }

    @Test fun `expired prefetch is replaced only on explicit prefetch`() {
        var clock = 0L
        owner.now = { clock }
        prefetch()
        val old = mockk<AdManagerInterstitialAd>(relaxed = true)
        loaded.onAdLoaded(old)
        clock = 3_600_000
        assertFalse(owner.isReady)
        assertFalse(owner.show(activity, true))
        assertEquals(1, loads)
        prefetch()
        assertEquals(2, loads)
        verify(exactly = 0) { old.show(any()) }
    }

    @Test fun `two owners cannot present concurrently and the second stays ready`() {
        prefetch()
        loaded.onAdLoaded(mockk(relaxed = true))
        val firstFullscreen = fullscreen
        val second = AudienzzRemoteConfigInterstitial(activity, "second", events)
        second.configDispatcher = Dispatchers.Main
        second.prefetch(); shadowOf(Looper.getMainLooper()).idle()
        val secondAd = mockk<AdManagerInterstitialAd>(relaxed = true)
        loaded.onAdLoaded(secondAd)
        val secondFullscreen = fullscreen
        assertTrue(owner.show(activity, true))
        assertFalse(second.show(activity, true))
        assertTrue(second.isReady)
        firstFullscreen.onAdDismissedFullScreenContent()
        assertTrue(second.show(activity, true))
        secondFullscreen.onAdDismissedFullScreenContent()
        second.destroy()
    }

    /// The contract this API exists for: the verb decides whether anything is presented.
    @Test fun `prefetch never presents and prefetchAndShow reuses what it holds`() {
        prefetch()
        val ad = mockk<AdManagerInterstitialAd>(relaxed = true)
        loaded.onAdLoaded(ad)
        verify(exactly = 0) { ad.show(any()) }
        assertTrue(owner.isReady)

        owner.prefetchAndShow(); shadowOf(Looper.getMainLooper()).idle()
        verify(exactly = 1) { ad.show(activity) }
        assertEquals(1, loads)
    }

    @Test fun `repeated prefetchAndShow coalesces onto one request and shows once`() {
        owner.prefetchAndShow(); owner.prefetchAndShow()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, loads)
        val ad = mockk<AdManagerInterstitialAd>(relaxed = true)
        loaded.onAdLoaded(ad)
        verify(exactly = 1) { ad.show(activity) }
    }

    @Test fun `a presentation asked for by a failed load does not leak to the next prefetch`() {
        owner.prefetchAndShow(); shadowOf(Looper.getMainLooper()).idle()
        loaded.onAdFailedToLoad(mockk(relaxed = true))

        prefetch()
        val ad = mockk<AdManagerInterstitialAd>(relaxed = true)
        loaded.onAdLoaded(ad)
        verify(exactly = 0) { ad.show(any()) }
        assertTrue(owner.isReady)
    }

    @Test fun `prefetch presentation keeps ownership when destroy is requested`() {
        prefetch()
        loaded.onAdLoaded(mockk(relaxed = true))
        assertTrue(owner.show(activity, true))
        owner.destroy()
        fullscreen.onAdDismissedFullScreenContent()
        verify(exactly = 1) { events.onClosed() }
        prefetch()
        assertEquals(1, loads)
    }

}
