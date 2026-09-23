package org.audienzz.mobile

import android.app.Activity
import android.os.Looper
import io.mockk.*
import org.audienzz.mobile.api.config.*
import org.audienzz.mobile.di.MainComponent
import org.audienzz.mobile.manager.RemoteConfigManager
import org.audienzz.mobile.original.AudienzzAdViewHandler
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Lazy loading and the prefetch margin are delivery decisions a publisher has to be able to make.
 *
 * [AudienzzRemoteBannerView] used to hardcode `withLazyLoading = true` and read the margin only from
 * the ad config, so a publisher whose slot auctioned too late had no lever at all.
 */
@RunWith(RobolectricTestRunner::class)
class CodexPendingIntentReviewTest {

    private lateinit var activity: Activity
    private var loadedLazy: Boolean? = null
    private var loadedMargin: Int? = null

    private fun config(lazyLoad: Boolean? = null, prefetchDp: Int? = null) = RemoteAdUnitConfig(
        1,
        RemoteConfig(
            adType = "banner",
            refreshTimeSeconds = 30,
            prefetchDistanceDp = prefetchDp,
            lazyLoad = lazyLoad,
        ),
        RemoteGamConfig("/1234/unit", listOf("320x50")),
        RemotePrebidConfig("placement", listOf("320x50")),
    )

    private fun seed(config: RemoteAdUnitConfig) {
        val manager = mockk<RemoteConfigManager>()
        coEvery { manager.getAdUnitConfig(any()) } returns config
        mockkObject(MainComponent.Companion)
        every { MainComponent.remoteConfigManager } returns manager
    }

    @Before fun setup() {
        activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        loadedLazy = null
        loadedMargin = null
        mockkConstructor(AudienzzAdViewHandler::class)
        every {
            anyConstructed<AudienzzAdViewHandler>().load(any(), any(), any(), any())
        } answers {
            loadedLazy = firstArg()
            loadedMargin = secondArg()
        }
        every { anyConstructed<AudienzzAdViewHandler>().enableSmartRefresh() } just Runs
    }

    @After fun cleanup() = unmockkAll()

    private fun view(adConfigId: String = "remote-banner") =
        AudienzzRemoteBannerView(activity, adConfigId)

    /**
     * The config fetch hops to [Dispatchers.IO] before resuming on the main looper, so idling once
     * is not enough — pump until the handler has been driven, with a bound so a genuine failure
     * fails the assertion rather than hanging.
     */
    private fun pump(maxMs: Long = 5_000) {
        val deadline = System.currentTimeMillis() + maxMs
        while (System.currentTimeMillis() < deadline && loadedLazy == null) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(5)
        }
        shadowOf(Looper.getMainLooper()).idle()
    }


    @Test fun `publisher intent survives config resolving after stop and cover`() {
        val gate = kotlinx.coroutines.CompletableDeferred<RemoteAdUnitConfig>()
        val manager = mockk<RemoteConfigManager>()
        coEvery { manager.getAdUnitConfig(any()) } coAnswers { gate.await() }
        mockkObject(MainComponent.Companion)
        every { MainComponent.remoteConfigManager } returns manager
        val v = view()
        v.loadAd()
        v.stopAutoRefresh()
        v.setHostCover(true)
        gate.complete(config())
        pump()
        assertNotNull("fixture must actually construct and load the handler", loadedLazy)
        verify(exactly = 1) { anyConstructed<AudienzzAdViewHandler>().stopAutoRefresh() }
        verify(exactly = 1) { anyConstructed<AudienzzAdViewHandler>().pauseForHostCover() }
        v.destroy()
    }
}
