package org.audienzz.mobile

import android.os.Looper
import io.mockk.mockk
import org.audienzz.mobile.screen.ScreenAdCoordinator
import org.audienzz.mobile.screen.screenAdCoordinatorOverride
import org.audienzz.mobile.util.AppForegroundMonitor
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.util.concurrent.TimeUnit

/**
 * Returning from the background is a new page impression, which recreates the active page's
 * banners. Exactly one has to happen per visit: two means a duplicate auction and a discarded
 * creative, none means a restored app keeps showing a stale ad.
 *
 * The app may report the visit itself, in which case the SDK stands down. Deciding that from how
 * long ago the last report happened fails in both directions, so ownership of the visit is tracked
 * explicitly — and this is the Android half of a decision that has repeatedly diverged from iOS.
 */
@RunWith(RobolectricTestRunner::class)
class ForegroundReimpressionTest {

    private lateinit var impressions: MutableList<String>
    private lateinit var hostActivity: android.app.Activity

    @Before
    fun setUp() {
        AppForegroundMonitor.resetForTesting()
        screenAdCoordinatorOverride = ScreenAdCoordinator()
        hostActivity = mockk(relaxed = true)
        impressions = mutableListOf()

        // Give the coordinator an active screen to re-report, then start counting.
        AudienzzPrebidMobile.pageImpression("Article")
        AudienzzPrebidMobile.observeForegroundReimpression()
        AudienzzPrebidMobile.pageImpressionObserver = { impressions.add(it) }
    }

    @After
    fun tearDown() {
        AudienzzPrebidMobile.pageImpressionObserver = null
        screenAdCoordinatorOverride = null
        AppForegroundMonitor.resetForTesting()
    }

    private fun settle(millis: Long = 1_000) =
        shadowOf(Looper.getMainLooper()).idleFor(millis, TimeUnit.MILLISECONDS)

    private fun background() = AppForegroundMonitor.onActivityStopped(hostActivity)

    private fun foreground() = AppForegroundMonitor.onActivityStarted(hostActivity)

    @Test
    fun `reports the visit when the app does not`() {
        background()
        foreground()
        settle()

        assertEquals(listOf("Article"), impressions)
    }

    @Test
    fun `does not report when the app already reported this visit`() {
        // An app reporting from onCreate reports before onActivityStarted. Judging by elapsed time
        // made a slow start look like a stale report and fired a second impression.
        background()
        AudienzzPrebidMobile.pageImpression("Article")
        settle(750)
        foreground()
        settle()

        assertEquals(
            "the app owns this visit, however long the start takes",
            listOf("Article"),
            impressions,
        )
    }

    @Test
    fun `reports a new visit even when the previous one was reported just before`() {
        // Judging by elapsed time let the PREVIOUS visit's report suppress this one, leaving the
        // restored app with no impression at all.
        AudienzzPrebidMobile.pageImpression("Article")
        background()
        foreground()
        settle()

        assertEquals(
            "the new visit needs its own impression regardless of how recent the last one was",
            listOf("Article", "Article"),
            impressions,
        )
    }

    @Test
    fun `does not report while still backgrounded`() {
        background()
        foreground()
        background()
        settle()

        assertEquals(emptyList<String>(), impressions)
    }
}
