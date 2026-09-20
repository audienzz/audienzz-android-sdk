package org.audienzz.mobile

import android.app.Activity
import android.os.Looper
import com.google.android.gms.ads.AdError
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

/**
 * Inventory that loads and is then released without ever being seen is the load-to-impression
 * gap. `loaded` with no matching `impression` was silent, and expiry in particular was only
 * evaluated lazily inside isReady, so an ad could age out with nothing recorded anywhere.
 */
@RunWith(RobolectricTestRunner::class)
class InterstitialDiscardTest {
    private lateinit var owner: AudienzzRemoteConfigInterstitial
    private lateinit var activity: Activity
    private lateinit var loaded: AudienzzInterstitialAdLoadCallback
    private lateinit var fullscreen: AudienzzFullScreenContentCallback
    private val emitted = mutableListOf<Map<String, Any?>>()
    private val events = object : AudienzzRemoteConfigInterstitial.Events {
        override fun onLoaded() {}
        override fun onFailed(error: com.google.android.gms.ads.LoadAdError) {}
        override fun onOpened() {}
        override fun onClosed() {}
        override fun onClicked() {}
        override fun onFailedToShow(error: AdError) {}
        override fun onError(message: String) {}
        override fun onLifecycleEvent(values: Map<String, Any?>) { emitted += values }
    }
    private var clock = 0L

    @Before fun setup() {
        AppForegroundMonitor.resetForTesting()
        emitted.clear(); clock = 0L
        activity = mockk(relaxed = true)
        every { activity.isFinishing } returns false
        every { activity.isDestroyed } returns false
        val config = RemoteAdUnitConfig(1, RemoteConfig("interstitial"),
            RemoteGamConfig("/probe", emptyList()), RemotePrebidConfig("probe", emptyList()))
        val manager = mockk<RemoteConfigManager>()
        coEvery { manager.getAdUnitConfig(any()) } returns config
        mockkObject(MainComponent.Companion)
        every { MainComponent.remoteConfigManager } returns manager
        mockkConstructor(AudienzzInterstitialAdHandler::class)
        every { anyConstructed<AudienzzInterstitialAdHandler>().load(any(), any(), any(), any()) } answers {
            loaded = secondArg(); fullscreen = thirdArg()
        }
        owner = AudienzzRemoteConfigInterstitial(activity, "probe", events)
        owner.configDispatcher = Dispatchers.Main
        owner.now = { clock }
    }

    @After fun cleanup() {
        AppForegroundMonitor.resetForTesting(); unmockkAll()
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
    private fun prefetch() { owner.prefetch(); idle() }
    private fun deliver() { loaded.onAdLoaded(mockk(relaxed = true)); idle() }
    private fun discards() = emitted.filter { it["event"] == "discardedWithoutImpression" }
    private fun names() = emitted.map { it["event"] }

    private fun hold() {
        prefetch(); deliver()
        assertTrue("fixture must actually hold inventory", owner.isReady)
    }

    @Test fun `expiry reports the discard exactly once`() {
        hold()
        assertTrue("control: held inventory is not a discard", discards().isEmpty())

        clock += 3_600_001L
        prefetch() // observing the expiry is what releases it

        assertEquals(1, discards().size)
        assertEquals("expired", discards().single()["reason"])
        assertTrue((discards().single()["loadAgeMillis"] as Long) > 3_600_000L)
    }

    @Test fun `disposal of held inventory reports a discard`() {
        hold()
        owner.destroy()
        assertEquals(1, discards().size)
        assertEquals("disposed", discards().single()["reason"])
    }

    @Test fun `a bridge can report a replacement instead of a disposal`() {
        hold()
        owner.destroy("replaced")
        assertEquals(1, discards().size)
        assertEquals("replaced", discards().single()["reason"])
    }

    @Test fun `a presentation failure reports a discard`() {
        hold()
        owner.show(activity, eligible = true)
        fullscreen.onAdFailedToShowFullScreenContent(mockk(relaxed = true))
        idle()

        assertTrue(names().contains("showFailed"))
        assertEquals(1, discards().size)
        assertEquals("presentationFailed", discards().single()["reason"])
    }

    @Test fun `presented and dismissed with no impression reports a discard`() {
        hold()
        owner.show(activity, eligible = true)
        fullscreen.onAdShowedFullScreenContent()
        fullscreen.onAdDismissedFullScreenContent()
        idle()

        assertEquals(1, discards().size)
        assertEquals("dismissedWithoutImpression", discards().single()["reason"])
    }

    @Test fun `inventory that recorded an impression is never a discard`() {
        hold()
        owner.show(activity, eligible = true)
        fullscreen.onAdShowedFullScreenContent()
        fullscreen.onAdImpression()
        fullscreen.onAdDismissedFullScreenContent()
        idle()

        assertTrue(names().contains("impression"))
        assertTrue("inventory that was seen was not wasted", discards().isEmpty())
    }

    @Test fun `a load failure is not an unused successful load`() {
        prefetch()
        loaded.onAdFailedToLoad(mockk(relaxed = true))
        idle()
        owner.destroy()

        assertTrue("there was never any inventory to waste", discards().isEmpty())
    }

    @Test fun `an owner holding nothing reports no discard on disposal`() {
        owner.destroy()
        assertTrue(discards().isEmpty())
    }

    @Test fun `every discard carries the load id and an age`() {
        hold()
        val loadId = emitted.first { it["event"] == "loaded" }["loadId"]
        clock += 300_000L
        owner.destroy()

        val discard = discards().single()
        assertEquals("the discard must be correlatable with its load", loadId, discard["loadId"])
        assertEquals(300_000L, discard["loadAgeMillis"])
        assertEquals("probe", discard["configId"])
    }
}
