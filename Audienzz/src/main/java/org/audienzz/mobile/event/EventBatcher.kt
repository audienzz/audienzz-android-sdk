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
import org.audienzz.mobile.event.network.entity.EventNetwork
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
 * ([EventLoggerImpl.logEvent]); the backend orders by sequence, not arrival. The channel is capped
 * at [MAX_QUEUE_SIZE] with drop-oldest overflow.
 *
 * The buffer is mirrored to disk by [EventStore], so it survives process death: events are appended
 * as they arrive and the file is rewritten once a batch settles. What is on disk is always what is
 * still owed to the collector — which is why events are handed here already mapped to
 * [EventNetwork]: the payload is frozen at creation time, so a restored event keeps the app version
 * and device context it was actually produced under rather than the restarted process's.
 */
@Singleton
internal class EventBatcher @Inject constructor(
    private val remoteRepository: RemoteEventRepository,
    private val store: EventStore,
    @IO dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : CoroutineScope, AppForegroundMonitor.Listener {

    override val coroutineContext = dispatcher + SupervisorJob() +
        CoroutineExceptionHandler { _, throwable ->
            Log.e(TAG, "Unexpected coroutine error", throwable)
        }

    private val events = Channel<EventNetwork>(
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
    fun enqueue(event: EventNetwork) {
        // Persist before buffering: a crash between the two loses nothing, the reverse loses the
        // event entirely.
        store.append(event)
        events.trySend(event)
    }

    /** Ask the consumer to send whatever it has buffered now (no-op if the buffer is empty). */
    fun flush() {
        flushSignals.trySend(Unit)
    }

    override fun onEnterBackground() = flush()

    override fun onEnterForeground() = flush()

    private fun startConsumer() = launch {
        // Anything the previous process did not get to send is owed to the collector. Send it
        // first, before accepting anything new.
        val restored = store.loadAll()
        if (restored.isNotEmpty()) {
            restored.chunked(MAX_BATCH_SIZE).forEach { chunk ->
                sendWithRetry(chunk)
                store.removeOldest(chunk.size)
            }
        }

        val batch = ArrayList<EventNetwork>(MAX_BATCH_SIZE)
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
            // Settled either way — delivered, or given up on after the retries — so it is no longer
            // owed. These are the oldest lines in the file; anything enqueued meanwhile was appended
            // behind them and is untouched.
            store.removeOldest(batch.size)
            batch.clear()
        }
    }

    private suspend fun sendWithRetry(batch: List<EventNetwork>) {
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

        /**
         * Sized against what a real screen produces. One ad slot emits roughly six events per
         * auction (bidRequest, bidResponse/noBid, bidWon, adImpression, viewability start/success),
         * so a four-slot screen is ~25 events per page impression — about one request per screen
         * visit rather than the several that a batch of 20 forced.
         */
        private const val MAX_BATCH_SIZE = 50

        /**
         * The ceiling on how long an event waits when traffic is too thin to fill a batch. At 5s a
         * trickle of one or two events still cost a request every five seconds, which is most of
         * what made the old behaviour chatty. Backgrounding still flushes immediately, so this
         * delays delivery rather than risking it — and now the buffer is on disk while it waits.
         */
        private const val FLUSH_INTERVAL_MS = 30_000L
        private const val MAX_QUEUE_SIZE = 500
        private const val MAX_RETRIES = 3
        private const val RETRY_BASE_DELAY_MS = 2000L
    }
}
