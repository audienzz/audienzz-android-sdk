package org.audienzz.mobile.event

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeoutOrNull
import org.audienzz.mobile.di.qualifier.IO
import org.audienzz.mobile.event.entity.EventDomain
import org.audienzz.mobile.event.repository.remote.RemoteEventRepository
import org.audienzz.mobile.util.AppForegroundMonitor
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory batching queue for clickstream events.
 *
 * Events are enriched + sequenced by [EventLoggerImpl] and handed here. Instead of one POST per
 * event, a single consumer coroutine coalesces them and POSTs a batch to `/submit/batch` when the
 * buffer reaches [MAX_BATCH_SIZE], after [FLUSH_INTERVAL_MS] of inactivity, or on an explicit
 * [flush] (app background/foreground). Failed batches are retried with exponential backoff up to
 * [MAX_RETRIES], then dropped.
 *
 * Ordering is safe to reorder/retry because `session_seq` is assigned at event creation
 * ([EventLoggerImpl.logEvent]); the backend orders by sequence, not arrival. In-memory only — a
 * buffer not yet flushed is lost if the process is killed. The events channel is capped at
 * [MAX_QUEUE_SIZE] with drop-oldest overflow.
 */
@Singleton
internal class EventBatcher @Inject constructor(
    private val remoteRepository: RemoteEventRepository,
    @IO dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : CoroutineScope, AppForegroundMonitor.Listener {

    override val coroutineContext = dispatcher + SupervisorJob() +
        CoroutineExceptionHandler { _, throwable ->
            Log.e(TAG, "Unexpected coroutine error", throwable)
        }

    private val events = Channel<EventDomain>(
        capacity = MAX_QUEUE_SIZE,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** Conflated: a pending flush request coalesces; delivering it breaks the fill loop early. */
    private val flushSignals = Channel<Unit>(capacity = Channel.CONFLATED)

    init {
        AppForegroundMonitor.addListener(this)
        startConsumer()
    }

    /** Enqueue an already-enriched event. Non-suspending; drops the oldest buffered event on overflow. */
    fun enqueue(event: EventDomain) {
        events.trySend(event)
    }

    /** Ask the consumer to send whatever it has buffered now (no-op if the buffer is empty). */
    fun flush() {
        flushSignals.trySend(Unit)
    }

    override fun onEnterBackground() = flush()

    override fun onEnterForeground() = flush()

    private fun startConsumer() = launch {
        val batch = ArrayList<EventDomain>(MAX_BATCH_SIZE)
        while (isActive) {
            // Block until the first event of the next batch arrives.
            batch.add(events.receive())
            // Fill up to MAX_BATCH_SIZE, or until the flush interval elapses, or an explicit flush.
            var flushNow = false
            withTimeoutOrNull(FLUSH_INTERVAL_MS) {
                while (batch.size < MAX_BATCH_SIZE && !flushNow) {
                    select {
                        events.onReceive { batch.add(it) }
                        flushSignals.onReceive { flushNow = true }
                    }
                }
            }
            sendWithRetry(ArrayList(batch))
            batch.clear()
        }
    }

    private suspend fun sendWithRetry(batch: List<EventDomain>) {
        var attempt = 0
        while (true) {
            try {
                remoteRepository.submitBatch(batch)
                Log.d(TAG, "batch sent (${batch.size} events)")
                return
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                if (attempt >= MAX_RETRIES) {
                    Log.e(TAG, "batch dropped after $MAX_RETRIES retries (${batch.size} events)", throwable)
                    return
                }
                attempt++
                val delayMs = RETRY_BASE_DELAY_MS shl (attempt - 1) // 2s, 4s, 8s
                Log.w(TAG, "batch failed, retry $attempt/$MAX_RETRIES in ${delayMs}ms: ${throwable.message}")
                delay(delayMs)
            }
        }
    }

    companion object {

        private const val TAG = "EventBatcher"

        // Kept in sync with the iOS AUEventQueue.
        private const val MAX_BATCH_SIZE = 20
        private const val FLUSH_INTERVAL_MS = 5000L
        private const val MAX_QUEUE_SIZE = 500
        private const val MAX_RETRIES = 3
        private const val RETRY_BASE_DELAY_MS = 2000L
    }
}
