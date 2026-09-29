package org.audienzz.mobile.util

import android.app.Activity
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The monitor gates every auction, so a wrong answer here either blocks all ads or lets a
 * backgrounded app keep auctioning. The cases below are the ones that actually shipped broken:
 * late initialization (the SDK is started from inside an already-running Activity, which is always
 * true for the Flutter and React Native bridges) means the first lifecycle callbacks are simply
 * never delivered, and Android does not replay them.
 */
class AppForegroundMonitorTest {

    private lateinit var events: MutableList<String>

    private val listener = object : AppForegroundMonitor.Listener {
        override fun onEnterBackground() { events.add("background") }
        override fun onEnterForeground() { events.add("foreground") }
    }

    private fun activity(): Activity = mockk(relaxed = true)

    @Before
    fun setUp() {
        AppForegroundMonitor.resetForTesting()
        events = mutableListOf()
        AppForegroundMonitor.addListener(listener)
    }

    @After
    fun tearDown() {
        AppForegroundMonitor.resetForTesting()
    }

    @Test
    fun `reports foreground before any lifecycle callback is observed`() {
        // The SDK is routinely initialized from inside a running Activity. Treating the resulting
        // "no callbacks seen yet" as background blocked every first auction on the launch screen.
        assertTrue(AppForegroundMonitor.isForeground)
    }

    @Test
    fun `stays foreground when an unobserved activity stops while another is visible`() {
        val a = activity() // already running when the SDK registered — never observed starting
        val b = activity()

        AppForegroundMonitor.onActivityStarted(b)
        AppForegroundMonitor.onActivityStopped(a)

        assertTrue("B is still visible", AppForegroundMonitor.isForeground)
        assertEquals(emptyList<String>(), events)
    }

    @Test
    fun `reports background when the sole unobserved activity stops`() {
        // Initialize inside running A, then background A. Nothing else was ever observed, so this
        // is the first real background transition — not an unknown state to ignore.
        val a = activity()

        AppForegroundMonitor.onActivityStopped(a)

        assertFalse(AppForegroundMonitor.isForeground)
        assertEquals(listOf("background"), events)
    }

    @Test
    fun `reports foreground again after the first background`() {
        val a = activity()

        AppForegroundMonitor.onActivityStopped(a)
        events.clear()
        AppForegroundMonitor.onActivityStarted(a)

        assertTrue(AppForegroundMonitor.isForeground)
        assertEquals(listOf("foreground"), events)
    }

    @Test
    fun `does not report background while a second activity is still started`() {
        val a = activity()
        val b = activity()

        AppForegroundMonitor.onActivityStarted(a)
        AppForegroundMonitor.onActivityStarted(b)
        AppForegroundMonitor.onActivityStopped(a)

        assertTrue(AppForegroundMonitor.isForeground)
        assertEquals(emptyList<String>(), events)

        AppForegroundMonitor.onActivityStopped(b)

        assertFalse(AppForegroundMonitor.isForeground)
        assertEquals(listOf("background"), events)
    }

    @Test
    fun `a repeated stop of the same activity does not report background twice`() {
        val a = activity()
        val b = activity()

        AppForegroundMonitor.onActivityStarted(a)
        AppForegroundMonitor.onActivityStarted(b)
        AppForegroundMonitor.onActivityStopped(a)
        AppForegroundMonitor.onActivityStopped(a)

        assertTrue("B is still started", AppForegroundMonitor.isForeground)
        assertEquals(emptyList<String>(), events)
    }

    @Test
    fun `removed listeners stop receiving events`() {
        val a = activity()
        AppForegroundMonitor.removeListener(listener)

        AppForegroundMonitor.onActivityStopped(a)

        assertEquals(emptyList<String>(), events)
    }

    @Test
    fun `late init host remains foreground across a translucent interstitial`() {
        val host = activity() // onStart happened before SDK initialization
        val interstitial = activity()
        AppForegroundMonitor.onActivityPaused(host)
        AppForegroundMonitor.onActivityStarted(interstitial)
        AppForegroundMonitor.onActivityResumed(host)
        AppForegroundMonitor.onActivityStopped(interstitial)

        assertTrue(AppForegroundMonitor.isForeground)
        assertEquals(emptyList<String>(), events)

        // Remembering the host must not hide the next REAL background transition.
        AppForegroundMonitor.onActivityPaused(host)
        AppForegroundMonitor.onActivityStopped(host)
        assertFalse(AppForegroundMonitor.isForeground)
        assertEquals(listOf("background"), events)
        AppForegroundMonitor.onActivityStarted(host)
        AppForegroundMonitor.onActivityResumed(host)
        assertTrue(AppForegroundMonitor.isForeground)
        assertEquals(listOf("background", "foreground"), events)
    }

    @Test
    fun `overlay stopping before host resume does not briefly background a late init host`() {
        val host = activity()
        val overlay = activity()
        AppForegroundMonitor.onActivityPaused(host)
        AppForegroundMonitor.onActivityStarted(overlay)
        AppForegroundMonitor.onActivityStopped(overlay)
        assertTrue(AppForegroundMonitor.isForeground)
        assertEquals(emptyList<String>(), events)
        AppForegroundMonitor.onActivityResumed(host)
        assertEquals(emptyList<String>(), events)
    }

    @Test
    fun `initialization during an overlay still recognizes the returning resumed host`() {
        val host = activity()
        val overlay = activity() // its onStart was also before initialization
        AppForegroundMonitor.onActivityResumed(host)
        AppForegroundMonitor.onActivityStopped(overlay)
        assertTrue(AppForegroundMonitor.isForeground)
        assertEquals(emptyList<String>(), events)
        AppForegroundMonitor.onActivityStopped(host)
        assertFalse(AppForegroundMonitor.isForeground)
        assertEquals(listOf("background"), events)
    }
}
