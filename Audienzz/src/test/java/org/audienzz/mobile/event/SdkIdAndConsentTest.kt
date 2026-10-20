package org.audienzz.mobile.event

import android.content.Context
import android.os.Looper
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.test.StandardTestDispatcher
import org.audienzz.mobile.AudienzzPrebidMobile
import org.audienzz.mobile.api.config.PrebidServerConfig
import org.audienzz.mobile.api.config.PublisherConfig
import org.audienzz.mobile.di.MainComponent
import org.audienzz.mobile.event.network.entity.EventNetwork
import org.audienzz.mobile.event.network.mapper.EventNetworkMapper
import org.audienzz.mobile.event.preferences.EventPreferences
import org.audienzz.mobile.event.preferences.EventPreferencesImpl
import org.audienzz.mobile.manager.RemoteConfigManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class SdkIdAndConsentTest {
    private val app: Context get() = RuntimeEnvironment.getApplication()

    @After fun cleanup() {
        AnalyticsContext.configure(null, "production")
        defaultPreferences().edit().clear().commit()
        unmockkAll()
    }

    private fun defaultPreferences() = app.getSharedPreferences("${app.packageName}_preferences", Context.MODE_PRIVATE)

    // ── sdk_id comes from the config ────────────────────────────────────────

    private fun initializeRemotely(config: PublisherConfig?): String? {
        mockkObject(MainComponent.Companion)
        every { MainComponent.init(any()) } just Runs
        every { MainComponent.eventLogger } returns null
        every { MainComponent.screenAdCoordinator } returns null
        val manager = mockk<RemoteConfigManager>(relaxed = true)
        coEvery { manager.getPublisherConfig(any()) } returns config
        every { MainComponent.remoteConfigManager } returns manager
        mockkStatic(com.google.android.gms.ads.MobileAds::class)
        every { com.google.android.gms.ads.MobileAds.initialize(any(), any()) } just Runs
        mockkStatic(org.prebid.mobile.PrebidMobile::class)
        var prebidStarted = false
        every { org.prebid.mobile.PrebidMobile.initializeSdk(any(), any<String>(), any()) } answers { prebidStarted = true }
        AudienzzPrebidMobile.initializeRemoteSdk(app, "init-id", null)
        // Before the config answers, analytics already reports the id the app passed.
        assertEquals("init-id", AnalyticsContext.snapshot().publisherId)
        repeat(200) { if (!prebidStarted) { shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(5) } }
        assertEquals(true, prebidStarted)
        return AnalyticsContext.snapshot().publisherId
    }

    private fun config(sdkId: String?) = PublisherConfig(id = 35, sdkId = sdkId,
        prebidServerConfig = PrebidServerConfig(url = "https://pbs.test", accountId = 1))

    @Test fun theConfigsSdkIdIsWhatAnalyticsReports() =
        assertEquals("sdk-7", initializeRemotely(config("sdk-7")))

    @Test fun aConfigWithoutSdkIdKeepsTheInitId() =
        assertEquals("init-id", initializeRemotely(config(null)))

    @Test fun noConfigKeepsTheInitId() =
        assertEquals("init-id", initializeRemotely(null))

    // ── consent_string before Prebid is ready ───────────────────────────────

    private fun pageImpressionConsent(preferences: EventPreferences): String? {
        val batcher = mockk<EventBatcher>(relaxed = true)
        val captured = mutableListOf<EventNetwork>()
        every { batcher.enqueue(capture(captured)) } returns Unit
        val dispatcher = StandardTestDispatcher()
        val logger = EventLoggerImpl(batcher, EventNetworkMapper(app), preferences,
            mockk(relaxed = true), mockk(relaxed = true), dispatcher)
        logger.onScreenResumed("A")
        dispatcher.scheduler.runCurrent()
        return captured.single().attributes["consent_string"]
    }

    @Test fun theCmpsStoredStringIsSentBeforePrebidHasInitialized() {
        // Prebid is not initialized in this test, so it answers null — as on every cold start.
        defaultPreferences().edit().putString("IABTCF_TCString", "CQ-cmp").commit()
        assertEquals("CQ-cmp", pageImpressionConsent(EventPreferencesImpl(mockk(relaxed = true), app)))
    }

    @Test fun noStoredStringSendsNoConsent() {
        assertNull(pageImpressionConsent(EventPreferencesImpl(mockk(relaxed = true), app)))
    }

    @Test fun aCmpThatStoredSomethingElseIsIgnored() {
        defaultPreferences().edit().putInt("IABTCF_TCString", 1).commit()
        assertNull(EventPreferencesImpl(mockk(relaxed = true), app).getIabTcString())
        defaultPreferences().edit().putString("IABTCF_TCString", "  ").commit()
        assertNull(EventPreferencesImpl(mockk(relaxed = true), app).getIabTcString())
    }
}
