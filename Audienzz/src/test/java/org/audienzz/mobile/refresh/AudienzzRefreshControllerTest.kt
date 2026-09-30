package org.audienzz.mobile.refresh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The controller is the single owner of periodic refresh, so these pin the rules the rest of the
 * SDK relies on: when the interval starts, what blocking does to it, and which callbacks are
 * allowed to schedule a successor.
 *
 * Time and scheduling are faked outright rather than idled through a looper, so every assertion is
 * about the decision rather than about how long a test slept.
 */
class AudienzzRefreshControllerTest {

    /** Deterministic stand-in: one pending task, and a clock the test advances by hand. */
    private class FakeScheduler : RefreshScheduler {
        var now: Long = 1_000L
        private var dueAt: Long? = null
        private var action: (() -> Unit)? = null

        /** Kept past a cancel so [fireIgnoringCancellation] can model a cancel that didn't work. */
        private var lastScheduled: (() -> Unit)? = null

        val hasPending: Boolean get() = action != null
        val pendingDelay: Long? get() = dueAt?.let { it - now }

        override fun postDelayed(delayMillis: Long, action: () -> Unit) {
            this.dueAt = now + delayMillis
            this.action = action
            this.lastScheduled = action
        }

        override fun cancel() {
            dueAt = null
            action = null
        }

        /**
         * Runs the most recently scheduled task as if a cancel had failed to take effect —
         * deliberately ignoring [cancel], which is the whole point. Not hypothetical: a
         * cancellation that silently did nothing (posting and removing through different Handler
         * instances) shipped once, so the controller re-checks eligibility when work executes
         * rather than trusting that cancelled work stays cancelled.
         */
        fun fireIgnoringCancellation() {
            lastScheduled?.invoke()
        }

        override fun nowMillis(): Long = now

        /** Advances the clock, running the pending task if it comes due. */
        fun advance(millis: Long) {
            val target = now + millis
            while (true) {
                val due = dueAt ?: break
                if (due > target) break
                now = due
                val toRun = action
                dueAt = null
                action = null
                toRun?.invoke()
            }
            now = target
        }
    }

    private lateinit var scheduler: FakeScheduler
    private lateinit var requests: MutableList<RefreshRequestReason>
    private lateinit var controller: AudienzzRefreshController

    private val interval = 30_000L

    @Before
    fun setUp() {
        scheduler = FakeScheduler()
        requests = mutableListOf()
        controller = AudienzzRefreshController(scheduler = scheduler) { reason, _ ->
            requests.add(reason)
        }
        controller.setIntervalMillis(interval)
    }

    /** Drives a full request round trip, as the handler does. */
    private fun completeARequest(
        reason: RefreshRequestReason = RefreshRequestReason.FIRST_LOAD,
        success: Boolean = true,
    ) {
        val generation = controller.onRequestStarted(reason)
        controller.onRequestCompleted(generation, success)
    }

    // ── The interval ────────────────────────────────────────────────────────

    @Test
    fun `nothing is scheduled before the first load completes`() {
        // The first load is owned by lazy loading or an explicit load, not by this controller.
        assertFalse(scheduler.hasPending)
        assertEquals(emptyList<RefreshRequestReason>(), requests)
    }

    @Test
    fun `the interval is measured from completion, not from the request`() {
        // A slow auction must not shorten the gap between creatives.
        val generation = controller.onRequestStarted(RefreshRequestReason.FIRST_LOAD)
        scheduler.advance(5_000)
        controller.onRequestCompleted(generation, success = true)

        assertEquals(interval, scheduler.pendingDelay)
    }

    @Test
    fun `a periodic refresh fires once the interval elapses`() {
        completeARequest()

        scheduler.advance(interval)

        assertEquals(listOf(RefreshRequestReason.PERIODIC_REFRESH), requests)
    }

    @Test
    fun `an interval of zero disables refresh entirely`() {
        controller.setIntervalMillis(0)
        completeARequest()

        scheduler.advance(10 * interval)

        assertFalse(scheduler.hasPending)
        assertEquals(emptyList<RefreshRequestReason>(), requests)
    }

    // ── Blocking ────────────────────────────────────────────────────────────

    @Test
    fun `blocking cancels the pending refresh`() {
        completeARequest()

        controller.block(RefreshBlockReason.NOT_VISIBLE)

        assertFalse(scheduler.hasPending)
        scheduler.advance(10 * interval)
        assertEquals(emptyList<RefreshRequestReason>(), requests)
    }

    @Test
    fun `clearing one reason does not clear another`() {
        // A single boolean could not express this, and a visibility resume silently undid a
        // publisher pause.
        completeARequest()
        controller.block(RefreshBlockReason.PUBLISHER)
        controller.block(RefreshBlockReason.NOT_VISIBLE)

        controller.unblock(RefreshBlockReason.NOT_VISIBLE)

        assertTrue(controller.isBlocked)
        assertEquals(setOf(RefreshBlockReason.PUBLISHER), controller.blockReasons)
        scheduler.advance(10 * interval)
        assertEquals(emptyList<RefreshRequestReason>(), requests)
    }

    @Test
    fun `refresh resumes only once every reason is cleared`() {
        completeARequest()
        controller.block(RefreshBlockReason.PUBLISHER)
        controller.block(RefreshBlockReason.APP_BACKGROUND)

        controller.unblock(RefreshBlockReason.APP_BACKGROUND)
        controller.unblock(RefreshBlockReason.PUBLISHER)
        scheduler.advance(interval)

        assertEquals(listOf(RefreshRequestReason.PERIODIC_REFRESH), requests)
    }

    @Test
    fun `six eligible seconds then forty hidden seconds leaves four seconds`() {
        controller.setIntervalMillis(10_000)
        completeARequest()
        scheduler.advance(6_000)
        controller.block(RefreshBlockReason.NOT_VISIBLE)
        scheduler.advance(40_000)
        controller.unblock(RefreshBlockReason.NOT_VISIBLE)
        assertEquals(4_000L, scheduler.pendingDelay)
        scheduler.advance(3_999)
        assertEquals(emptyList<RefreshRequestReason>(), requests)
        scheduler.advance(1)
        assertEquals(listOf(RefreshRequestReason.PERIODIC_REFRESH), requests)
    }

    @Test
    fun `every block pauses time and overlapping duplicate blocks do not count twice`() {
        for (reason in RefreshBlockReason.entries) {
            requests.clear()
            controller.setIntervalMillis(10_000)
            completeARequest()
            scheduler.advance(6_000)
            controller.block(reason)
            scheduler.advance(20_000)
            controller.block(reason)
            controller.block(RefreshBlockReason.PUBLISHER)
            scheduler.advance(20_000)
            controller.unblock(reason)
            if (reason != RefreshBlockReason.PUBLISHER) {
                assertFalse(scheduler.hasPending)
                scheduler.advance(20_000)
                controller.unblock(RefreshBlockReason.PUBLISHER)
            }
            assertEquals("remaining time for $reason", 4_000L, scheduler.pendingDelay)
            scheduler.advance(4_000)
            assertEquals(listOf(RefreshRequestReason.PERIODIC_REFRESH), requests)
        }
    }

    @Test
    fun `prefetch completed while hidden earns no time until visible`() {
        controller.setIntervalMillis(10_000)
        controller.block(RefreshBlockReason.NOT_VISIBLE)
        completeARequest()
        scheduler.advance(86_400_000)
        controller.unblock(RefreshBlockReason.NOT_VISIBLE)
        assertEquals(10_000L, scheduler.pendingDelay)
        scheduler.advance(9_999)
        assertTrue(requests.isEmpty())
        scheduler.advance(1)
        assertEquals(listOf(RefreshRequestReason.PERIODIC_REFRESH), requests)
    }

    @Test
    fun `interval changes preserve eligible time without credit for disabled time`() {
        controller.setIntervalMillis(10_000)
        completeARequest()
        scheduler.advance(6_000)
        controller.setIntervalMillis(0)
        scheduler.advance(86_400_000)
        assertFalse(scheduler.hasPending)
        controller.setIntervalMillis(17_000)
        assertEquals(11_000L, scheduler.pendingDelay)
        scheduler.advance(5_000)
        controller.setIntervalMillis(17_000)
        controller.scheduleNext()
        assertEquals(6_000L, scheduler.pendingDelay)
        scheduler.advance(5_999)
        assertTrue(requests.isEmpty())
        scheduler.advance(1)
        assertEquals(listOf(RefreshRequestReason.PERIODIC_REFRESH), requests)
    }

    @Test
    fun `shortening a blocked interval retains only previously earned time`() {
        completeARequest()
        scheduler.advance(6_000)
        controller.block(RefreshBlockReason.NOT_VISIBLE)
        scheduler.advance(40_000)
        controller.setIntervalMillis(10_000)
        controller.unblock(RefreshBlockReason.NOT_VISIBLE)
        assertEquals(4_000L, scheduler.pendingDelay)
    }

    @Test
    fun `multiple visible segments accumulate and the replacement starts a fresh cycle`() {
        controller.setIntervalMillis(10_000)
        completeARequest()
        repeat(3) {
            scheduler.advance(3_000)
            controller.block(RefreshBlockReason.NOT_VISIBLE)
            scheduler.advance(40_000)
            controller.unblock(RefreshBlockReason.NOT_VISIBLE)
        }
        assertEquals(1_000L, scheduler.pendingDelay)
        scheduler.advance(1_000)
        assertEquals(1, requests.size)
        val generation = controller.onRequestStarted(RefreshRequestReason.PERIODIC_REFRESH)
        scheduler.advance(20_000)
        controller.onRequestCompleted(generation, true)
        assertEquals(10_000L, scheduler.pendingDelay)
        scheduler.advance(9_999)
        assertEquals(1, requests.size)
        scheduler.advance(1)
        assertEquals(2, requests.size)
    }

    @Test
    fun `an early cancelled callback cannot consume the remaining interval`() {
        controller.setIntervalMillis(10_000)
        completeARequest()
        scheduler.advance(6_000)
        controller.block(RefreshBlockReason.NOT_VISIBLE)
        scheduler.advance(40_000)
        controller.unblock(RefreshBlockReason.NOT_VISIBLE)
        scheduler.fireIgnoringCancellation()
        assertTrue(requests.isEmpty())
        assertEquals(4_000L, scheduler.pendingDelay)
        scheduler.advance(4_000)
        assertEquals(1, requests.size)
    }

    @Test
    fun `eligibility is rechecked when the scheduled task runs`() {
        // The banner can be hidden between scheduling and execution, so checking only at schedule
        // time is not enough.
        completeARequest()
        controller.block(RefreshBlockReason.NOT_VISIBLE)
        controller.unblock(RefreshBlockReason.NOT_VISIBLE)
        assertTrue(scheduler.hasPending)

        // Hidden again after the task was scheduled, without cancelling it explicitly.
        scheduler.advance(interval - 1)
        controller.block(RefreshBlockReason.NOT_VISIBLE)
        scheduler.advance(10)

        assertEquals(emptyList<RefreshRequestReason>(), requests)
    }

    // ── In-flight requests ──────────────────────────────────────────────────

    @Test
    fun `a refresh that comes due despite cancellation is still refused while blocked`() {
        completeARequest()
        controller.block(RefreshBlockReason.NOT_VISIBLE)
        controller.unblock(RefreshBlockReason.NOT_VISIBLE)
        controller.block(RefreshBlockReason.PUBLISHER)

        scheduler.fireIgnoringCancellation()

        assertEquals(emptyList<RefreshRequestReason>(), requests)
    }

    @Test
    fun `an in-flight request still restarts the interval when the banner is merely hidden`() {
        // Scrolling away does not invalidate the request: its creative belongs to this page.
        // Treating it as superseded left the banner looking permanently overdue, so scrolling back
        // refreshed immediately and then again a moment later.
        val generation = controller.onRequestStarted(RefreshRequestReason.PERIODIC_REFRESH)
        controller.block(RefreshBlockReason.NOT_VISIBLE)
        controller.onRequestCompleted(generation, success = true)

        controller.unblock(RefreshBlockReason.NOT_VISIBLE)

        assertEquals("a full interval from the response, not an instant refresh", interval, scheduler.pendingDelay)
    }

    @Test
    fun `a pause survives a successful response`() {
        // Prebid re-armed its own timer from its response handlers; a completion arriving after a
        // pause must not schedule the next request.
        val generation = controller.onRequestStarted(RefreshRequestReason.PERIODIC_REFRESH)
        controller.block(RefreshBlockReason.NOT_VISIBLE)

        controller.onRequestCompleted(generation, success = true)
        scheduler.advance(10 * interval)

        assertEquals(emptyList<RefreshRequestReason>(), requests)
    }

    @Test
    fun `a pause survives a failed response`() {
        val generation = controller.onRequestStarted(RefreshRequestReason.PERIODIC_REFRESH)
        controller.block(RefreshBlockReason.NOT_VISIBLE)

        controller.onRequestCompleted(generation, success = false)
        scheduler.advance(10 * interval)

        assertEquals(emptyList<RefreshRequestReason>(), requests)
    }

    @Test
    fun `only one request is in flight at a time`() {
        completeARequest()
        controller.onRequestStarted(RefreshRequestReason.PERIODIC_REFRESH)

        controller.scheduleNext()
        scheduler.advance(10 * interval)

        assertEquals("nothing may be scheduled while a request is outstanding", 0, requests.size)
    }

    @Test
    fun `a response from a superseded generation schedules nothing`() {
        completeARequest()
        val stale = controller.onRequestStarted(RefreshRequestReason.PERIODIC_REFRESH)
        controller.invalidatePending()

        controller.onRequestCompleted(stale, success = true)
        scheduler.advance(10 * interval)

        assertEquals(emptyList<RefreshRequestReason>(), requests)
    }

    @Test
    fun `a superseded response does not restart the interval`() {
        // Supersession comes from a page transition, not from being hidden: the replacement it
        // issues owns the clock, so the outgoing response must not push the next refresh out.
        completeARequest()
        val stale = controller.onRequestStarted(RefreshRequestReason.PERIODIC_REFRESH)
        scheduler.advance(interval * 2)
        controller.invalidatePending()

        controller.onRequestCompleted(stale, success = true)
        controller.scheduleNext()

        assertFalse("invalidated work must await a new completed request", scheduler.hasPending)
    }

    // ── Failures and retries ────────────────────────────────────────────────

    @Test
    fun `a failed request retries with backoff`() {
        completeARequest(success = false)

        assertEquals(2_000L, scheduler.pendingDelay)
        scheduler.advance(2_000)
        assertEquals(listOf(RefreshRequestReason.LOAD_RETRY), requests)
    }

    @Test
    fun `retries are bounded`() {
        completeARequest(success = false)
        repeat(5) {
            scheduler.advance(60_000)
            if (controller.hasRequestInFlight.not()) {
                val generation = controller.onRequestStarted(RefreshRequestReason.LOAD_RETRY)
                controller.onRequestCompleted(generation, success = false)
            }
        }

        val retries = requests.count { it == RefreshRequestReason.LOAD_RETRY }
        assertTrue("expected a bounded number of retries, got $retries", retries <= 3)
    }

    @Test
    fun `a successful request clears the failure count`() {
        completeARequest(success = false)
        scheduler.advance(2_000)
        val generation = controller.onRequestStarted(RefreshRequestReason.LOAD_RETRY)
        controller.onRequestCompleted(generation, success = true)

        assertEquals("back to the normal cadence", interval, scheduler.pendingDelay)
    }

    // ── Destruction ─────────────────────────────────────────────────────────

    @Test
    fun `a destroyed controller schedules nothing`() {
        completeARequest()

        controller.destroy()
        scheduler.advance(10 * interval)

        assertEquals(emptyList<RefreshRequestReason>(), requests)
    }

    @Test
    fun `a late callback after destroy does nothing`() {
        val generation = controller.onRequestStarted(RefreshRequestReason.PERIODIC_REFRESH)
        controller.destroy()

        controller.onRequestCompleted(generation, success = true)
        scheduler.advance(10 * interval)

        assertEquals(emptyList<RefreshRequestReason>(), requests)
    }

    @Test
    fun `blocking a destroyed controller is inert`() {
        controller.destroy()

        controller.block(RefreshBlockReason.NOT_VISIBLE)
        controller.unblockAll()
        scheduler.advance(10 * interval)

        assertEquals(emptyList<RefreshRequestReason>(), requests)
    }
}
