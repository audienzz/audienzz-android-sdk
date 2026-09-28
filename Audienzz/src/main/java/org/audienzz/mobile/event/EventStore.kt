package org.audienzz.mobile.event

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.audienzz.mobile.event.network.entity.EventNetwork
import org.audienzz.mobile.util.AudienzzDiagnostics
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Append-only durable outbox. Existing event-only JSONL files are read without migration loss.
 * Acknowledgements are small ID records; compact every 64 removals or when empty, rather than
 * reading/rewriting the entire backlog after EVERY request. Callers use the delivery IO worker.
 */
@Singleton
internal class EventStore(
    context: Context,
    private val maxLines: Int,
    private val json: Json,
) {
    @Inject
    constructor(context: Context) : this(context, MAX_LINES, Json { ignoreUnknownKeys = true })

    private val file = File(File(context.filesDir, "audienzz"), "events.jsonl")
    private val lock = Any()
    private var cached: LinkedHashMap<String, EventNetwork>? = null
    private var removals = 0

    fun loadAll(): List<EventNetwork> = synchronized(lock) { records().values.toList() }

    fun append(event: EventNetwork, protectedId: String? = null) = synchronized(lock) {
        val records = records()
        records[event.eventId] = event
        appendLine(json.encodeToString(EventNetwork.serializer(), event))
        while (records.size > maxLines) {
            val oldest = records.keys.first { it != protectedId }
            remove(oldest)
        }
    }

    fun remove(id: String) = synchronized(lock) {
        val records = records()
        if (records.remove(id) == null) return@synchronized
        removals++
        if (records.isEmpty() || removals >= 64) {
            compact()
        } else {
            appendLine(buildJsonObject { put("_au_ack", id) }.toString())
        }
    }

    // Kept for store maintenance/tests; delivery always acknowledges by identity.
    fun removeOldest(count: Int) = synchronized(lock) {
        if (count > 0) records().keys.take(count).forEach { remove(it) }
    }

    fun count(): Int = synchronized(lock) { records().size }

    private fun records(): LinkedHashMap<String, EventNetwork> {
        cached?.let { return it }
        val records = linkedMapOf<String, EventNetwork>()
        cached = records
        var damaged = false
        if (file.exists()) {
            runCatching {
                file.forEachLine { line ->
                    if (line.isNotBlank()) {
                        runCatching {
                            val objectValue = json.parseToJsonElement(line).jsonObject
                            val ack = objectValue["_au_ack"]?.jsonPrimitive?.content
                            if (ack != null) {
                                records.remove(ack)
                                removals++
                            } else {
                                val event = json.decodeFromJsonElement(EventNetwork.serializer(), objectValue)
                                records[event.eventId] = event
                            }
                        }.onFailure { damaged = true }
                    }
                }
            }.onFailure { persistenceFailed() }
        }
        val overflow = records.size > maxLines
        while (records.size > maxLines) records.remove(records.keys.first())
        // Repair a torn tail before appending; it must not swallow the next intact event.
        if (damaged || overflow || removals >= 64) compact()
        return records
    }

    private fun appendLine(line: String) {
        runCatching {
            file.parentFile?.mkdirs()
            // Prefix a newline too: even an interrupted previous write is isolated from this one.
            file.appendText("\n$line\n")
        }.onFailure { persistenceFailed() }
    }

    private fun compact() {
        val records = cached ?: return
        runCatching {
            if (records.isEmpty()) {
                check(!file.exists() || file.delete())
            } else {
                file.parentFile?.mkdirs()
                val temp = File(file.parentFile, "events.jsonl.tmp")
                temp.bufferedWriter().use { writer ->
                    records.values.forEach {
                        writer.write(json.encodeToString(EventNetwork.serializer(), it))
                        writer.newLine()
                    }
                }
                // Never truncate the original if replacement fails. Replaying an acknowledged
                // event after a crash is recoverable via event_id; losing pending events is not.
                check(temp.renameTo(file))
            }
            removals = 0
        }.onFailure { persistenceFailed() }
    }

    private fun persistenceFailed() = AudienzzDiagnostics.log("analytics", "persistenceFailed")

    companion object { internal const val MAX_LINES = 500 }
}
