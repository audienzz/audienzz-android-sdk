package org.audienzz.mobile.event

import android.content.Context
import android.util.Log
import kotlinx.serialization.json.Json
import org.audienzz.mobile.event.network.entity.EventNetwork
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Disk backing for [EventBatcher] — a durable outbox so events survive process death.
 *
 * Events are stored as JSON Lines: one serialized event per line, appended as it is enqueued and
 * removed from the front once delivered. Appending (rather than rewriting the whole buffer per
 * event) keeps the cost of an enqueue constant regardless of how much is backed up.
 *
 * Writing on *enqueue* is the whole point. The batcher already flushes when the app backgrounds, so
 * the tidy path was never the lossy one; what was lost was a foreground crash or process kill, and
 * only a write that has already happened by then can survive it.
 *
 * **Removal is positional on purpose.** The producer appends before handing the event to the
 * channel, and the single consumer drains the channel in order, so the batch that just settled is
 * always the oldest lines in the file. Removing "the first n" therefore needs no identity matching
 * and, crucially, cannot race with events still sitting in the channel — those were appended later
 * and sit behind the lines being dropped.
 *
 * A line that fails to parse is skipped rather than failing the load: the last line can be a partial
 * write if the process died mid-append, and one torn event is not a reason to drop the hundreds of
 * intact ones in front of it.
 *
 * Every method is synchronized: unlike the iOS `AUEventStore` (which lives on one serial queue),
 * appends arrive on the producer's coroutine while removals happen on the consumer's.
 */
@Singleton
internal class EventStore(
    context: Context,
    private val maxLines: Int,
    private val json: Json,
) {
    /**
     * Dagger's entry point. The cap and the codec are constructor parameters only so a test can
     * drive overflow without writing five hundred events; they are not configuration.
     */
    @Inject
    constructor(context: Context) : this(context, MAX_LINES, Json { ignoreUnknownKeys = true })

    private val file: File = File(File(context.filesDir, DIRECTORY).apply { mkdirs() }, FILE_NAME)
    private val lock = Any()

    /** Every event still on disk, oldest first. Unparseable lines are skipped. */
    fun loadAll(): List<EventNetwork> = synchronized(lock) {
        val lines = readLines() ?: return emptyList()
        var skipped = 0
        val events = lines.mapNotNull { line ->
            runCatching { json.decodeFromString(EventNetwork.serializer(), line) }
                .getOrNull()
                .also { if (it == null) skipped++ }
        }
        if (skipped > 0) Log.d(TAG, "skipped $skipped unreadable line(s) while restoring")
        if (events.isNotEmpty()) Log.d(TAG, "restored ${events.size} event(s) from disk")
        events
    }

    /** Append one event. Constant cost, except when the cap forces a trim. */
    fun append(event: EventNetwork) = synchronized(lock) {
        runCatching {
            file.appendText(json.encodeToString(EventNetwork.serializer(), event) + "\n")
            trimIfOversizeLocked()
        }.onFailure { Log.w(TAG, "could not persist event", it) }
        Unit
    }

    /**
     * Drop the [count] oldest events — the batch that just settled, delivered or given up on.
     *
     * Either outcome means it is no longer owed; leaving a permanently failing batch on disk would
     * have every subsequent launch retry it forever.
     */
    fun removeOldest(count: Int) = synchronized(lock) {
        if (count <= 0) return
        val lines = readLines() ?: return
        if (count >= lines.size) {
            runCatching { file.delete() }
            return
        }
        writeLinesLocked(lines.drop(count))
    }

    /** Visible for tests: how many events are currently on disk. */
    fun count(): Int = synchronized(lock) { readLines()?.size ?: 0 }

    // MARK: - Internals (all called under `lock`)

    private fun readLines(): List<String>? {
        if (!file.exists()) return null
        return runCatching { file.readLines().filter { it.isNotBlank() } }
            .onFailure { Log.w(TAG, "could not read stored events", it) }
            .getOrNull()
    }

    /**
     * Cap the file independently of the channel's own drop-oldest overflow.
     *
     * The two bounds are not the same thing: an event dropped from a full channel is never
     * consumed, so nothing would ever remove its line. Without this the file would keep growing
     * through exactly the backlog the cap exists to survive.
     */
    private fun trimIfOversizeLocked() {
        val lines = readLines() ?: return
        if (lines.size <= maxLines) return
        val overflow = lines.size - maxLines
        Log.d(TAG, "store overflow — dropped $overflow oldest event(s)")
        writeLinesLocked(lines.drop(overflow))
    }

    private fun writeLinesLocked(lines: List<String>) {
        runCatching {
            // Write-then-rename: a process death mid-rewrite leaves the previous file, never a
            // truncated one.
            val temp = File(file.parentFile, "$FILE_NAME.tmp")
            temp.writeText(lines.joinToString(separator = "", postfix = "") { "$it\n" })
            if (!temp.renameTo(file)) {
                temp.copyTo(file, overwrite = true)
                temp.delete()
            }
        }.onFailure { Log.w(TAG, "could not rewrite store", it) }
    }

    companion object {

        private const val TAG = "EventStore"
        private const val DIRECTORY = "audienzz"
        private const val FILE_NAME = "events.jsonl"

        /** Matches the channel's MAX_QUEUE_SIZE in [EventBatcher]. */
        internal const val MAX_LINES = 500
    }
}
