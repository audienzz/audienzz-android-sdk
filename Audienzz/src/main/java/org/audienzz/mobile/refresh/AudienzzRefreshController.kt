package org.audienzz.mobile.refresh

import android.util.Log

/**
 * The single owner of periodic banner refresh.
 *
 * ## Why this exists
 *
 * Refresh used to be scheduled by two parties at once. Prebid ran its own timer (armed from its
 * response handlers, on both success and failure, and re-armed by responses that arrived after the
 * SDK had paused), while the SDK separately posted delayed fetches of its own. Neither could see
 * the other's state, so a pause could be undone by an in-flight response, and a page transition, a
 * pending periodic refresh and a deferred retry could each issue their own replacement for the same
 * transition.
 *
 * Prebid is now never given a refresh interval — on Android `autoRefreshDelay` stays 0, which makes
 * `BidLoader.setupRefreshTimer()` return without scheduling anything on either the success or the
 * failure path, and leaves its refresh listener null. The configured interval lives here instead,
 * and every periodic request is scheduled, gated and counted by this class.
 *
 * ## Timing rules
 *
 * - The interval is measured from the moment a request **completes**, not from when it starts, so a
 *   slow auction does not shorten the gap between creatives.
 * - Durations use a monotonic clock ([RefreshScheduler.nowMillis]). Wall-clock time would let a
 *   device clock change fire a refresh instantly or suppress it indefinitely.
 * - Blocking cancels the pending task but does **not** move the due time: elapsed time keeps
 *   counting while a banner is off screen, which preserves the existing stale-aware resume. When
 *   the last block clears, an overdue banner refreshes immediately and an in-date one waits out the
 *   remainder. (Charging only visible time is deliberately out of scope for this migration.)
 * - An interval of 0 disables periodic refresh entirely; nothing is ever scheduled.
 *
 * ## Ownership rules
 *
 * - At most one request is in flight per banner. While one is, nothing new is scheduled.
 * - Every block reason is independent; clearing one never clears another.
 * - Eligibility is re-checked when scheduled work runs, not only when it is scheduled, because the
 *   banner can be paged out, hidden or backgrounded in between.
 * - A generation is stamped on each request. A response from a superseded generation, or any
 *   callback after [destroy], must not load an ad, mutate state or schedule a successor.
 */
internal class AudienzzRefreshController(
    private val scheduler: RefreshScheduler = MainLooperRefreshScheduler(),
    private val logTag: String = TAG,
    /** Issues one request. The controller never loads ads itself. */
    private val onRequestDue: (RefreshRequestReason, Int) -> Unit,
) {

    /**
     * Configured interval in milliseconds; 0 (or negative) disables periodic refresh.
     *
     * Held here rather than read back from Prebid's ad-unit configuration, which is deliberately
     * left at 0 so Prebid schedules nothing.
     */
    var intervalMillis: Long = 0
        private set

    private val blocks = linkedSetOf<RefreshBlockReason>()

    /** Monotonic time the last request completed, or null before the first completion. */
    private var lastCompletionAt: Long? = null

    /**
     * Generation of the outstanding request, or null when none is.
     *
     * Tracked per generation rather than as a bare flag. A superseded request's completion must not
     * clear the flag belonging to the replacement that overtook it, and — the failure that made
     * this necessary — a superseded completion that never cleared the flag at all left the
     * controller believing a request was forever outstanding, so it never scheduled again and the
     * slot was stranded empty.
     */
    private var inFlightGeneration: Int? = null
    private var destroyed = false
    private var consecutiveFailures = 0

    /**
     * Increments whenever outstanding work is invalidated (a page transition, a block, destruction).
     * A callback carrying an older generation is stale and must do nothing.
     */
    var generation: Int = 0
        private set

    // ── Configuration ───────────────────────────────────────────────────────

    /** Applies the configured interval. Passing 0 disables refresh and cancels pending work. */
    fun setIntervalMillis(millis: Long) {
        intervalMillis = if (millis > 0) millis else 0
        if (intervalMillis == 0L) {
            scheduler.cancel()
        } else {
            scheduleNext()
        }
    }

    // ── Block state ─────────────────────────────────────────────────────────

    val isBlocked: Boolean get() = blocks.isNotEmpty()

    /** The reasons currently blocking refresh, for logging and tests. */
    val blockReasons: Set<RefreshBlockReason> get() = blocks.toSet()

    /**
     * Adds a block reason. Any pending periodic work is cancelled and outstanding callbacks are
     * invalidated, so a response that arrives after this cannot schedule a successor.
     */
    fun block(reason: RefreshBlockReason) {
        if (destroyed) return
        if (blocks.add(reason)) {
            Log.d(logTag, "refresh blocked by $reason (now $blocks)")
        }
        scheduler.cancel()
        // Deliberately NOT a generation bump. A request in flight when the banner scrolls out of
        // view is still legitimate — its creative belongs to this page — so it should complete and
        // restart the interval normally; the block is what stops the NEXT one being scheduled.
        // Superseding it instead made the banner look permanently overdue, so scrolling away and
        // back refreshed twice in quick succession. Generations are for page transitions and
        // destruction, where the response really does belong to a context that no longer applies.
    }

    /**
     * Removes one block reason. Refresh resumes only when every reason has been cleared, so a
     * visibility resume cannot undo a publisher pause.
     */
    fun unblock(reason: RefreshBlockReason, schedule: Boolean = true) {
        if (destroyed) return
        if (!blocks.remove(reason)) return
        Log.d(logTag, "refresh unblocked from $reason (remaining $blocks)")
        if (schedule && blocks.isEmpty()) {
            scheduleNext()
        }
    }

    /** Clears every block reason. Used when a banner joins a fresh page. */
    fun unblockAll() {
        if (destroyed) return
        if (blocks.isEmpty()) return
        blocks.clear()
        Log.d(logTag, "refresh unblocked from all reasons")
        scheduleNext()
    }

    // ── Request lifecycle ───────────────────────────────────────────────────

    /**
     * Records that a request has started, whatever its reason. Returns the generation it belongs
     * to; the caller passes that back on completion so a superseded response can be recognised.
     */
    fun onRequestStarted(reason: RefreshRequestReason): Int {
        generation++
        inFlightGeneration = generation
        scheduler.cancel()
        if (reason != RefreshRequestReason.LOAD_RETRY) {
            consecutiveFailures = 0
        }
        Log.d(logTag, "request started: $reason (generation $generation)")
        return generation
    }

    /**
     * Records a completed request and arranges what follows.
     *
     * A response from a superseded generation is ignored entirely — it must not restart the clock
     * or schedule anything, since its page or eligibility no longer applies.
     */
    fun onRequestCompleted(generationAtRequest: Int, success: Boolean) {
        if (destroyed || generationAtRequest != generation || inFlightGeneration != generationAtRequest) return
        inFlightGeneration = null
        lastCompletionAt = scheduler.nowMillis()

        if (success) {
            consecutiveFailures = 0
            scheduleNext()
            return
        }

        consecutiveFailures++
        if (consecutiveFailures > MAX_CONSECUTIVE_FAILURES) {
            // Stop retrying rather than hammer a failing endpoint. The slot is not stranded: the
            // next page impression, visibility resume or foreground still starts a fresh request.
            Log.w(logTag, "giving up after $consecutiveFailures consecutive failures; " +
                "the next page impression or resume will try again")
            scheduleNext()
            return
        }
        scheduleRetry()
    }

    /**
     * True while a request of the CURRENT generation is outstanding, so callers never start a
     * second one. A request left over from a superseded generation does not count: its callback is
     * already inert, so waiting for it would strand the banner.
     */
    val hasRequestInFlight: Boolean get() = inFlightGeneration == generation

    // ── Scheduling ──────────────────────────────────────────────────────────

    /**
     * Schedules the next periodic refresh, or fires one immediately if the banner is already
     * overdue. Safe to call repeatedly; the scheduler keeps at most one pending task.
     */
    fun scheduleNext() {
        if (destroyed || intervalMillis <= 0 || isBlocked || hasRequestInFlight) {
            return
        }
        val last = lastCompletionAt
        if (last == null) {
            // Nothing has loaded yet, so there is no interval to measure from. The first load is
            // driven by lazy loading or an explicit load, not by this controller.
            return
        }
        val dueAt = last + intervalMillis
        val delay = (dueAt - scheduler.nowMillis()).coerceAtLeast(0)
        Log.d(logTag, "next refresh in ${delay}ms")
        scheduler.postDelayed(delay) { fireIfStillEligible(RefreshRequestReason.PERIODIC_REFRESH) }
    }

    private fun scheduleRetry() {
        if (destroyed || isBlocked) return
        val delay = RETRY_BASE_DELAY_MS shl (consecutiveFailures - 1)
        Log.d(logTag, "retry $consecutiveFailures in ${delay}ms")
        scheduler.postDelayed(delay) { fireIfStillEligible(RefreshRequestReason.LOAD_RETRY) }
    }

    /**
     * Re-checks eligibility at execution time. A task scheduled while the banner was eligible can
     * come due after it has been hidden, backgrounded or paged out.
     */
    private fun fireIfStillEligible(reason: RefreshRequestReason) {
        if (destroyed) return
        if (isBlocked) {
            Log.d(logTag, "$reason due but blocked by $blocks; skipping")
            return
        }
        if (hasRequestInFlight) {
            Log.d(logTag, "$reason due but a request is already in flight; skipping")
            return
        }
        if (reason == RefreshRequestReason.PERIODIC_REFRESH && intervalMillis <= 0) return
        onRequestDue(reason, generation)
    }

    /**
     * Invalidates outstanding work without blocking. Used by a page transition, which issues its
     * own replacement and must not also let a pending periodic refresh or retry fire.
     */
    fun invalidatePending() {
        scheduler.cancel()
        generation++
        inFlightGeneration = null
    }

    /**
     * Marks the banner as having just loaded, so the interval is measured from now. Used when a
     * load is issued outside the controller (a first load or a page replacement).
     */
    fun noteLoadedNow() {
        lastCompletionAt = scheduler.nowMillis()
    }

    fun destroy() {
        destroyed = true
        scheduler.cancel()
        generation++
        blocks.clear()
    }

    val isDestroyed: Boolean get() = destroyed

    private companion object {
        const val TAG = "AudienzzRefresh"

        /** Bounded so a persistently failing slot cannot retry forever. */
        const val MAX_CONSECUTIVE_FAILURES = 3

        /** Doubles per attempt: 2s, 4s, 8s. */
        const val RETRY_BASE_DELAY_MS = 2_000L
    }
}
