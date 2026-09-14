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
}
