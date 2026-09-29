package org.audienzz.mobile

import android.os.Looper
import io.mockk.*
import kotlinx.coroutines.test.StandardTestDispatcher
import org.audienzz.mobile.di.MainComponent
import org.audienzz.mobile.event.*
import org.audienzz.mobile.event.network.entity.EventNetwork
import org.audienzz.mobile.event.network.mapper.EventNetworkMapper
import org.audienzz.mobile.targeting.AudienzzAdRequestContext
import org.robolectric.RuntimeEnvironment
import org.junit.Assert.*
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

/** Real lifecycle, coordinator and analytics logger; HTTP is replaced by a capturing batcher. */
@RunWith(RobolectricTestRunner::class)
class ForegroundRecoveryTest {

    private val dispatcher = StandardTestDispatcher()
    private val events = mutableListOf<EventNetwork>()
    private lateinit var logger: EventLoggerImpl
    private lateinit var initialPage: AnalyticsPageContext
    private lateinit var viewUpdates: MutableList<String>
    private lateinit var hostActivity: android.app.Activity

    @Before
    fun setUp() {
        AppForegroundMonitor.resetForTesting()
        screenAdCoordinatorOverride = ScreenAdCoordinator()
        hostActivity = mockk(relaxed = true)
        viewUpdates = mutableListOf()

        val sender = mockk<EventBatcher>(relaxed = true)
        every { sender.enqueue(capture(events)) } just Runs
        logger = EventLoggerImpl(sender, EventNetworkMapper(RuntimeEnvironment.getApplication()),
            mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), dispatcher)
        mockkObject(MainComponent.Companion)
        every { MainComponent.eventLogger } returns logger
        // Give the coordinator an active screen to recover, then start counting.
        AudienzzPrebidMobile.pageImpression("Article")
        initialPage = logger.capturePageContext()
        assertNotNull(initialPage.pageImpressionId)
        AudienzzPrebidMobile.observeForegroundRecovery()
        AudienzzPrebidMobile.pageImpressionObserver = { viewUpdates.add(it) }
    }

    @After
    fun tearDown() {
        AudienzzPrebidMobile.pageImpressionObserver = null
        screenAdCoordinatorOverride = null
        AppForegroundMonitor.resetForTesting()
        unmockkAll()
    }

    private fun settle(millis: Long = 1_000) =
        shadowOf(Looper.getMainLooper()).idleFor(millis, TimeUnit.MILLISECONDS)

    private fun background() = AppForegroundMonitor.onActivityStopped(hostActivity)

    private fun foreground() = AppForegroundMonitor.onActivityStarted(hostActivity)

    @Test
    fun `recovers ads without reporting another analytics page`() {
        background()
        foreground()
        settle()

        assertEquals(listOf("Article"), viewUpdates) // bridge refresh signal is still sent
        dispatcher.scheduler.runCurrent()
        assertEquals(1, events.count { it.eventType == "pageImpression" })
        assertEquals(initialPage, logger.capturePageContext())
        assertEquals(1, screenAdCoordinatorOverride!!.epoch)
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
            viewUpdates,
        )
    }

    @Test
    fun `quick return recovers ads while preserving the latest explicit visit`() {
        // Judging by elapsed time let the PREVIOUS visit's report suppress this one, leaving the
        // restored app with no ad recovery.
        AudienzzPrebidMobile.pageImpression("Article")
        background()
        foreground()
        settle()

        assertEquals(
            "both explicit report and recovery notify bridge views",
            listOf("Article", "Article"),
            viewUpdates,
        )
    }

    @Test
    fun `does not report while still backgrounded`() {
        background()
        foreground()
        background()
        settle()

        assertEquals(emptyList<String>(), viewUpdates)
    }
    @Test fun `foreground preserves slots and counters across repeated returns`() {
        val ledger = screenAdCoordinatorOverride!!.requestLedger
        val first = AudienzzAdRequestContext.forSlot("flutter:1", "Article")
        val second = AudienzzAdRequestContext.forSlot("flutter:2", "Article")
        assertEquals(0, ledger.nextRequest(first).refresh)
        assertEquals(0, ledger.nextRequest(second).refresh)
        repeat(3) { index ->
            background(); foreground(); settle()
            assertSame(first, AudienzzAdRequestContext.forSlot("flutter:1", "Article"))
            val next = ledger.nextRequest(first)
            assertEquals(1, next.pageSequence); assertEquals(1, next.slot)
            assertEquals(index + 1, next.refresh)
            assertEquals(initialPage, logger.capturePageContext())
        }
        assertEquals(2, ledger.nextRequest(second).slot)
        dispatcher.scheduler.runCurrent()
        assertEquals(1, events.count { it.eventType == "pageImpression" })
    }

    @Test fun `navigation during the delay supersedes recovery and starts a new page`() {
        background(); foreground()
        AudienzzPrebidMobile.pageImpression("Next")
        settle()
        assertEquals(listOf("Next"), viewUpdates)
        assertEquals(2, screenAdCoordinatorOverride!!.epoch)
        assertEquals("Next", logger.capturePageContext().screenName)
        assertNotEquals(initialPage.pageImpressionId, logger.capturePageContext().pageImpressionId)
        dispatcher.scheduler.runCurrent()
        assertEquals(2, events.count { it.eventType == "pageImpression" })
    }

    @Test fun `recovery observer can navigate without restoring the old page afterwards`() {
        AudienzzPrebidMobile.pageImpressionObserver = { page ->
            viewUpdates += page
            if (page == "Article") AudienzzPrebidMobile.pageImpression("Next")
        }
        background(); foreground(); settle()
        assertEquals(listOf("Article", "Next"), viewUpdates)
        assertEquals("Next", screenAdCoordinatorOverride!!.activeScreen)
        assertEquals(2, screenAdCoordinatorOverride!!.epoch)
        dispatcher.scheduler.runCurrent()
        assertEquals(2, events.count { it.eventType == "pageImpression" })
    }

    @Test fun `without an active page foreground does not invent one`() {
        screenAdCoordinatorOverride = ScreenAdCoordinator()
        background(); foreground(); settle()
        assertEquals(emptyList<String>(), viewUpdates)
        assertEquals(0, screenAdCoordinatorOverride!!.epoch)
        assertEquals(initialPage, logger.capturePageContext())
    }

}
