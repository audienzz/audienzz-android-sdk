package org.audienzz.mobile.event

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import org.audienzz.mobile.di.qualifier.IO
import org.audienzz.mobile.event.network.entity.EventNetwork
import org.audienzz.mobile.event.repository.remote.RemoteEventRepository
import org.audienzz.mobile.util.AppForegroundMonitor
import org.audienzz.mobile.util.AudienzzDiagnostics
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Immediate delivery with a durable outbox. The internal name is retained for DI compatibility;
 * each POST now contains ONE event, with no batching timer. One consumer owns persistence/state,
 * and at most one HTTP request is in flight. No caller performs disk or network I/O.
 */
@Singleton
internal class EventBatcher @Inject constructor(
    private val remoteRepository: RemoteEventRepository,
    private val store: EventStore,
    @IO dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : CoroutineScope, AppForegroundMonitor.Listener {
    override val coroutineContext = dispatcher + SupervisorJob() +
        CoroutineExceptionHandler { _, error -> Log.e(TAG, "Unexpected delivery error", error) }

    private val events = Channel<EventNetwork>(EventStore.MAX_LINES, BufferOverflow.DROP_OLDEST,
        onUndeliveredElement = {
            AudienzzDiagnostics.log("analytics", "dropped", "count" to 1, "reason" to "capacity")
        })
    private val flushSignals = Channel<Unit>(Channel.CONFLATED)
    private val retrySignals = Channel<Unit>(Channel.CONFLATED)
    private val completions = Channel<Completion>(Channel.CONFLATED)
    private data class Completion(val event: EventNetwork, val error: Throwable?)

    init {
        AppForegroundMonitor.addListener(this)
        startConsumer()
    }

    fun enqueue(event: EventNetwork) { events.trySend(event) }
    fun flush() { flushSignals.trySend(Unit) }
    override fun onEnterBackground() = flush()
    override fun onEnterForeground() = flush()

    private fun startConsumer() = launch {
        // Restore once BEFORE processing new events. Producers no longer write concurrently with
        // restore, which previously could put the same event in both the restored list and channel.
        val pending = store.loadAll().associateByTo(linkedMapOf()) { it.eventId }
        var inFlight: String? = null
        var retryJob: Job? = null
        var failures = 0

        fun sendIfReady() {
            if (inFlight != null || retryJob != null) return
            val event = pending.values.firstOrNull() ?: return
            inFlight = event.eventId
            AudienzzDiagnostics.log("analytics", "sending", "count" to 1, "attempt" to failures + 1)
            launch {
                val error = try {
                    remoteRepository.submitBatch(listOf(event))
                    null
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (error: Throwable) {
                    error
                }
                completions.send(Completion(event, error))
            }
        }

        sendIfReady()
        while (isActive) {
            select<Unit> {
                events.onReceive { event ->
                    if (!pending.containsKey(event.eventId)) {
                        // Decide eviction here, before append reaches the store's own safety cap.
                        // Retry rotation can give memory a different order from the disk journal.
                        while (pending.size >= EventStore.MAX_LINES) {
                            val oldest = pending.keys.first { it != inFlight }
                            pending.remove(oldest)
                            store.remove(oldest)
                            AudienzzDiagnostics.log("analytics", "dropped", "count" to 1, "reason" to "capacity")
                        }
                        store.append(event, protectedId = inFlight) // BEFORE any HTTP attempt.
                        pending[event.eventId] = event
                        AudienzzDiagnostics.log("analytics", "queued", "type" to event.eventType)
                    }
                }
                completions.onReceive { result ->
                    inFlight = null
                    val event = result.event
                    val error = result.error
                    if (error == null) {
                        failures = 0
                        pending.remove(event.eventId)
                        store.remove(event.eventId) // Identity, never a position shifted by overflow.
                        AudienzzDiagnostics.log("analytics", "sent", "count" to 1)
                    } else {
                        failures++
                        // Rotate failed events so a permanently rejected payload cannot strand
                        // every later event. The whole sender still observes the retry cooldown.
                        pending.remove(event.eventId)
                        pending[event.eventId] = event
                        AudienzzDiagnostics.log("analytics", "failed", "count" to 1,
                            "attempt" to failures, "status" to (error as? HttpException)?.code(),
                            "reason" to error.javaClass.simpleName)
                        val delayMs = (2_000L shl (failures - 1).coerceAtMost(20)).coerceAtMost(60_000L)
                        retryJob = launch { delay(delayMs); retrySignals.send(Unit) }
                        AudienzzDiagnostics.log("analytics", "retryScheduled", "delayMs" to delayMs)
                    }
                }
                retrySignals.onReceive { retryJob = null }
                flushSignals.onReceive { /* A lifecycle hint must not bypass retryJob. */ }
            }
            sendIfReady()
        }
    }

    companion object { private const val TAG = "EventBatcher" }
}
