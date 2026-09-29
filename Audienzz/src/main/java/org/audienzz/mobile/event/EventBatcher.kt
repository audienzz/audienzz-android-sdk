package org.audienzz.mobile.event

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.selects.select
import kotlinx.serialization.json.Json
import org.audienzz.mobile.di.qualifier.IO
import org.audienzz.mobile.event.network.entity.EventNetwork
import org.audienzz.mobile.event.repository.remote.RemoteEventRepository
import org.audienzz.mobile.util.AppForegroundMonitor
import org.audienzz.mobile.util.AudienzzDiagnostics
import retrofit2.HttpException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/** Persist now, batch later. A single worker owns timers, acknowledgements and all HTTP attempts. */
@Singleton
internal class EventBatcher internal constructor(
    private val remoteRepository: RemoteEventRepository,
    private val store: EventStore,
    dispatcher: CoroutineDispatcher,
    private val config: Config,
    private val now: () -> Long,
    private val jitter: () -> Double,
    context: Context? = null,
) : CoroutineScope, AppForegroundMonitor.Listener {
    @Inject constructor(remoteRepository: RemoteEventRepository, store: EventStore,
                        @IO dispatcher: CoroutineDispatcher = Dispatchers.IO, context: Context) :
        this(remoteRepository, store, dispatcher, Config(), SystemClock::elapsedRealtime, { Random.nextDouble(0.5, 1.0) }, context)

    internal data class Config(val batchSize: Int = 25, val batchBytes: Int = 128 * 1024,
        val batchDelayMs: Long = 5000, val minIntervalMs: Long = 2000,
        val retryBaseMs: Long = 2000, val retryMaxMs: Long = 60_000)

    override val coroutineContext = dispatcher + SupervisorJob() +
        CoroutineExceptionHandler { _, error -> Log.e("EventBatcher", "Unexpected delivery error", error) }
    // Bounded admission, without DROP_OLDEST (which used to silently replace owed events).
    private val events = Channel<EventNetwork>(1024)
    private val flushSignals = Channel<Unit>(Channel.CONFLATED)
    private val wakeSignals = Channel<Unit>(Channel.CONFLATED)
    private val completions = Channel<Completion>(Channel.CONFLATED)
    private data class Completion(val events: List<EventNetwork>, val error: Throwable?, val startedAt: Long)
    private val json = Json { encodeDefaults = false; explicitNulls = false }

    init {
        AppForegroundMonitor.addListener(this)
        // Connectivity is only a flush hint; the sender still owns every deadline.
        val connectivity = context?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = flush()
        }
        if (connectivity != null) runCatching {
            connectivity.registerDefaultNetworkCallback(callback)
        }
        coroutineContext[Job]?.invokeOnCompletion {
            AppForegroundMonitor.removeListener(this)
            if (connectivity != null) runCatching { connectivity.unregisterNetworkCallback(callback) }
        }
        startConsumer()
    }
    fun enqueue(event: EventNetwork) {
        if (events.trySend(event).isFailure) dropped("ingressCapacity")
    }
    fun flush() { flushSignals.trySend(Unit) }
    override fun onEnterBackground() = flush()
    override fun onEnterForeground() = flush()

    private fun startConsumer() = launch {
        val pending = linkedMapOf<String, EventNetwork>()
        val unsaved = linkedMapOf<String, EventNetwork>()
        val arrived = mutableMapOf<String, Long>()
        val plans = ArrayDeque<List<EventNetwork>>()
        var restored = false
        var inFlight = false
        var wake: Job? = null
        var lastStart: Long? = null
        var retryAt = 0L
        var storageRetryAt = 0L
        var failures = 0
        var drain = false
        var unsavedBytes = 0

        fun size(event: EventNetwork) = json.encodeToString(EventNetwork.serializer(), event).toByteArray(Charsets.UTF_8).size
        fun later(at: Long) {
            wake?.cancel()
            wake = launch { delay((at - now()).coerceIn(1, 86_400_000)); wakeSignals.send(Unit) }
        }
        fun persist() {
            if (now() < storageRetryAt) return
            try {
                if (!restored) {
                    store.loadAll().forEach { pending[it.eventId] = it; arrived[it.eventId] = now() }
                    restored = true
                    drain = pending.isNotEmpty()
                }
                for ((id, event) in unsaved.toMap()) {
                    when (store.append(event)) {
                        EventStore.Admission.IO_ERROR -> { storageRetryAt = now() + config.retryBaseMs; return }
                        EventStore.Admission.FULL -> { dropped("storageCapacity"); arrived.remove(id) }
                        EventStore.Admission.STORED -> pending[id] = event
                    }
                    unsaved.remove(id); unsavedBytes -= size(event)
                }
            } catch (_: Exception) { storageRetryAt = now() + config.retryBaseMs }
        }
        fun pump() {
            wake?.cancel(); wake = null
            persist()
            if (inFlight) return
            if (pending.isEmpty()) {
                if (unsaved.isNotEmpty() || !restored) later(storageRetryAt)
                else drain = false
                return
            }
            val batch = if (plans.isNotEmpty()) plans.first() else {
                var bytes = 2
                val selected = mutableListOf<EventNetwork>()
                for (event in pending.values) {
                    val added = size(event) + if (selected.isEmpty()) 0 else 1
                    if (selected.size >= config.batchSize || bytes + added > config.batchBytes) break
                    selected.add(event); bytes += added
                }
                // An oversized singleton cannot fit any legal POST. Retain it for inspection.
                if (selected.isEmpty()) {
                    val id = pending.keys.first()
                    if (store.quarantine(id)) {
                        pending.remove(id); arrived.remove(id)
                        AudienzzDiagnostics.log("analytics", "quarantined", "reason" to "payloadTooLarge", "count" to 1)
                        later(now() + 1)
                    } else later(now() + config.retryBaseMs)
                    return
                }
                selected
            }
            val full = batch.size >= config.batchSize || batch.size < pending.size
            val deadline = if (drain || plans.isNotEmpty() || full) now()
                else (arrived[batch.first().eventId] ?: now()) + config.batchDelayMs
            val allowed = maxOf(deadline, retryAt, lastStart?.plus(config.minIntervalMs) ?: 0L)
            if (now() < allowed) { later(minOf(allowed, if (unsaved.isNotEmpty()) storageRetryAt else Long.MAX_VALUE)); return }
            if (plans.isNotEmpty()) plans.removeFirst()
            inFlight = true; drain = true; lastStart = now()
            AudienzzDiagnostics.log("analytics", "sending", "count" to batch.size, "attempt" to failures + 1)
            launch {
                val startedAt = now()
                val error = try { remoteRepository.submitBatch(batch); null }
                    catch (cancel: CancellationException) { throw cancel }
                    catch (error: Throwable) { error }
                completions.send(Completion(batch, error, startedAt))
            }
        }
        pump()
        while (isActive) {
            select<Unit> {
                events.onReceive { event ->
                    if (event.eventId !in pending && event.eventId !in unsaved) {
                        val bytes = size(event)
                        if (unsavedBytes.toLong() + bytes <= 1024 * 1024) {
                            unsaved[event.eventId] = event; unsavedBytes += bytes
                            arrived[event.eventId] = now()
                            AudienzzDiagnostics.log("analytics", "queued", "type" to event.eventType)
                        } else dropped("memoryCapacity")
                    }
                }
                completions.onReceive { result ->
                    inFlight = false
                    lastStart = result.startedAt
                    val ids = result.events.map { it.eventId }
                    val error = result.error
                    if (error == null && store.acknowledge(ids)) {
                        ids.forEach { pending.remove(it); arrived.remove(it) }
                        failures = 0; retryAt = 0
                        AudienzzDiagnostics.log("analytics", "sent", "count" to ids.size)
                    } else {
                        val status = (error as? HttpException)?.code()
                        AudienzzDiagnostics.log("analytics", "failed", "count" to ids.size, "status" to status,
                            "reason" to (error?.javaClass?.simpleName ?: "ackPersistence"))
                        if (status in listOf(400, 413, 422)) {
                            if (ids.size > 1) {
                                val halves = result.events.chunked((ids.size + 1) / 2)
                                halves.asReversed().forEach { plans.addFirst(it) }
                            } else if (store.quarantine(ids.single())) {
                                pending.remove(ids.single()); arrived.remove(ids.single())
                                AudienzzDiagnostics.log("analytics", "quarantined", "count" to 1, "status" to status)
                            } else plans.addFirst(result.events)
                        } else plans.addFirst(result.events)
                        failures = (failures + 1).coerceAtMost(21)
                        val exponential = (config.retryBaseMs shl (failures - 1)).coerceAtMost(config.retryMaxMs)
                        val wait = maxOf((exponential * jitter()).toLong(), retryAfterMs((error as? HttpException)?.response()?.headers()?.get("Retry-After")))
                        retryAt = now() + wait.coerceAtMost(Long.MAX_VALUE - now())
                        AudienzzDiagnostics.log("analytics", "retryScheduled", "delayMs" to wait)
                    }
                }
                wakeSignals.onReceive { }
                flushSignals.onReceive { drain = true }
            }
            pump()
        }
    }

    private fun dropped(reason: String) = AudienzzDiagnostics.log("analytics", "dropped", "count" to 1, "reason" to reason)
    companion object {
        internal fun retryAfterMs(value: String?, wallTime: Long = System.currentTimeMillis()): Long {
            if (value == null) return 0
            value.trim().toLongOrNull()?.let { return it.coerceIn(0, Long.MAX_VALUE / 1000) * 1000 }
            return runCatching {
                val format = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US).apply { timeZone = TimeZone.getTimeZone("GMT"); isLenient = false }
                ((format.parse(value)?.time ?: wallTime) - wallTime).coerceAtLeast(0)
            }.getOrDefault(0)
        }
    }
}
