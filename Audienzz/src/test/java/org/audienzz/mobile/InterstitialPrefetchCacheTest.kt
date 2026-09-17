package org.audienzz.mobile

import android.app.Activity
import android.os.Looper
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

/** What a publisher calling prefetch several times in a row actually costs. */
@RunWith(RobolectricTestRunner::class)
class InterstitialPrefetchCacheTest {
    private lateinit var owner: AudienzzRemoteConfigInterstitial
    private lateinit var activity: Activity
    private lateinit var loaded: AudienzzInterstitialAdLoadCallback
    private lateinit var fullscreen: AudienzzFullScreenContentCallback
    private val events = mockk<AudienzzRemoteConfigInterstitial.Events>(relaxed = true)
    private var loads = 0
    private var clock = 0L

    @Before fun setup() {
        AppForegroundMonitor.resetForTesting()
        loads = 0; clock = 0L
        activity = mockk(relaxed = true)
        val config = RemoteAdUnitConfig(1, RemoteConfig("interstitial"),
            RemoteGamConfig("/probe", emptyList()), RemotePrebidConfig("probe", emptyList()))
        val manager = mockk<RemoteConfigManager>()
        coEvery { manager.getAdUnitConfig(any()) } returns config
        mockkObject(MainComponent.Companion)
        every { MainComponent.remoteConfigManager } returns manager
        mockkConstructor(AudienzzInterstitialAdHandler::class)
        every { anyConstructed<AudienzzInterstitialAdHandler>().load(any(), any(), any(), any()) } answers {
            loads++; loaded = secondArg(); fullscreen = thirdArg()
        }
        owner = AudienzzRemoteConfigInterstitial(activity, "probe", events)
        owner.configDispatcher = Dispatchers.Main
        owner.now = { clock }
    }

    @After fun cleanup() {
        owner.destroy(); AppForegroundMonitor.resetForTesting(); unmockkAll()
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
    private fun preload() { owner.preload(); idle() }
    private fun deliver() { loaded.onAdLoaded(mockk<AdManagerInterstitialAd>(relaxed = true)); idle() }

    @Test fun `four prefetches in a row buy one ad`() {
        preload(); preload(); preload(); preload()
        assertEquals("only the first may reach the ad server", 1, loads)
        deliver()
        assertTrue(owner.isReady)

        // And again once one is already cached.
        preload(); preload()
        assertEquals("a cached ad is not replaced by another prefetch", 1, loads)
    }

    @Test fun `only one ad is held at a time`() {
        preload(); deliver()
        val first = owner.isReady
        preload(); idle()
        assertTrue(first)
        assertEquals("no second ad is fetched while one is held", 1, loads)
    }

    @Test fun `an expired ad is replaced but only once`() {
        preload(); deliver()
        assertTrue(owner.isReady)

        clock += 3_600_001L                       // past the one-hour expiry
        assertFalse("an expired ad must not count as inventory", owner.isReady)

        preload(); preload(); preload()
        assertEquals("expiry allows exactly one replacement fetch", 2, loads)
    }
}
