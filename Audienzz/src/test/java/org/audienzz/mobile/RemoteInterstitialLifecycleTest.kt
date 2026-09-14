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
    private fun load() { owner.loadAd(); shadowOf(Looper.getMainLooper()).idle() }

    @Test fun `duplicate load coalesces inventory and auto shows once`() {
        load(); load()
        assertEquals(1, loads)
        val ad = mockk<AdManagerInterstitialAd>(relaxed = true)
        loaded.onAdLoaded(ad)
        verify(exactly = 1) { ad.show(activity) }
        load()
        assertEquals(1, loads)
        fullscreen.onAdDismissedFullScreenContent()
        load()
        assertEquals(2, loads)
    }

    @Test fun `destroy before Google completion cannot auto show`() {
        load()
        owner.destroy()
        val ad = mockk<AdManagerInterstitialAd>(relaxed = true)
        loaded.onAdLoaded(ad)
        verify(exactly = 0) { ad.show(any()) }
        verify(exactly = 0) { events.onLoaded() }
    }

    @Test fun `destroy before demand handoff prevents Google request`() {
        load()
        owner.destroy()
        mockkStatic(AdManagerInterstitialAd::class)
        handoff(AudienzzResultCode.NO_BIDS, AdManagerAdRequest.Builder().build(), loaded)
        verify(exactly = 0) { AdManagerInterstitialAd.load(any<android.content.Context>(), any<String>(), any<AdManagerAdRequest>(), any<com.google.android.gms.ads.admanager.AdManagerInterstitialAdLoadCallback>()) }
    }

    @Test fun `destroy from loaded callback cancels pending automatic presentation`() {
        load()
        every { events.onLoaded() } answers { owner.destroy() }
        val ad = mockk<AdManagerInterstitialAd>(relaxed = true)
        loaded.onAdLoaded(ad)
        verify(exactly = 0) { ad.show(any()) }
    }

    @Test fun `background at completion reports failure without delayed automatic show`() {
        load()
        AppForegroundMonitor.onActivityStarted(activity)
        AppForegroundMonitor.onActivityStopped(activity)
        val ad = mockk<AdManagerInterstitialAd>(relaxed = true)
        loaded.onAdLoaded(ad)
        verify(exactly = 0) { ad.show(any()) }
        verify { events.onError(match { it.contains("Activity") }) }
        AppForegroundMonitor.onActivityStarted(activity)
        verify(exactly = 0) { ad.show(any()) }
    }

    @Test fun `destroy while presenting preserves terminal callback`() {
        load()
        loaded.onAdLoaded(mockk(relaxed = true))
        owner.destroy()
        fullscreen.onAdFailedToShowFullScreenContent(AdError(1, "error", "google"))
        verify(exactly = 1) { events.onFailedToShow(any()) }
        load()
        assertEquals(1, loads)
    }
    private fun preload() { owner.preload(); shadowOf(Looper.getMainLooper()).idle() }

    @Test fun `preload retains inventory and never remembers a missed opportunity`() {
        preload(); preload()
        assertFalse(owner.showAtOpportunity(activity, eligible = true))
        val ad = mockk<AdManagerInterstitialAd>(relaxed = true)
        loaded.onAdLoaded(ad)
        preload()
        assertEquals(1, loads)
        assertTrue(owner.isReady)
        verify(exactly = 0) { ad.show(any()) }
        assertFalse(owner.showAtOpportunity(activity, eligible = false))
        assertTrue(owner.isReady)
        assertTrue(owner.showAtOpportunity(activity, eligible = true))
        assertFalse(owner.showAtOpportunity(activity, eligible = true))
        verify(exactly = 1) { ad.show(activity) }
    }

    @Test fun `background skips opportunity without discarding ready inventory`() {
        preload()
        val ad = mockk<AdManagerInterstitialAd>(relaxed = true)
        loaded.onAdLoaded(ad)
        AppForegroundMonitor.onActivityStarted(activity)
        AppForegroundMonitor.onActivityStopped(activity)
        assertFalse(owner.showAtOpportunity(activity, true))
        assertTrue(owner.isReady)
        AppForegroundMonitor.onActivityStarted(activity)
        verify(exactly = 0) { ad.show(any()) }
        assertTrue(owner.showAtOpportunity(activity, true))
    }

    @Test fun `expired preload is replaced only on explicit preload`() {
        var clock = 0L
        owner.now = { clock }
        preload()
        val old = mockk<AdManagerInterstitialAd>(relaxed = true)
        loaded.onAdLoaded(old)
        clock = 3_600_000
        assertFalse(owner.isReady)
        assertFalse(owner.showAtOpportunity(activity, true))
        assertEquals(1, loads)
        preload()
        assertEquals(2, loads)
        verify(exactly = 0) { old.show(any()) }
    }

    @Test fun `two owners cannot present concurrently and the second stays ready`() {
        preload()
        loaded.onAdLoaded(mockk(relaxed = true))
        val firstFullscreen = fullscreen
        val second = AudienzzRemoteConfigInterstitial(activity, "second", events)
        second.configDispatcher = Dispatchers.Main
        second.preload(); shadowOf(Looper.getMainLooper()).idle()
        val secondAd = mockk<AdManagerInterstitialAd>(relaxed = true)
        loaded.onAdLoaded(secondAd)
        val secondFullscreen = fullscreen
        assertTrue(owner.showAtOpportunity(activity, true))
        assertFalse(second.showAtOpportunity(activity, true))
        assertTrue(second.isReady)
        firstFullscreen.onAdDismissedFullScreenContent()
        assertTrue(second.showAtOpportunity(activity, true))
        secondFullscreen.onAdDismissedFullScreenContent()
        second.destroy()
    }

    @Test fun `preload presentation keeps ownership when destroy is requested`() {
        preload()
        loaded.onAdLoaded(mockk(relaxed = true))
        assertTrue(owner.showAtOpportunity(activity, true))
        owner.destroy()
        fullscreen.onAdDismissedFullScreenContent()
        verify(exactly = 1) { events.onClosed() }
        preload()
        assertEquals(1, loads)
    }

}
