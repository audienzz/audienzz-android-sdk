package org.audienzz.mobile.event

import android.content.Context
import kotlinx.serialization.json.*
import org.audienzz.mobile.event.network.entity.EventNetwork
import org.audienzz.mobile.util.AudienzzDiagnostics
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/** Single-worker durable journal. Never evicts an unacknowledged event to admit a newer one. */
@Singleton
internal class EventStore(
    context: Context,
    private val maxBytes: Int,
    private val json: Json,
) {
    @Inject constructor(context: Context) : this(context, MAX_BYTES, Json { ignoreUnknownKeys = true })
    enum class Admission { STORED, FULL, IO_ERROR }
    private val file = File(File(context.filesDir, "audienzz"), "events.jsonl")
    private var cached: LinkedHashMap<String, EventNetwork>? = null
    private val quarantined = linkedSetOf<String>()
    private var payloadBytes = 0
    private var removals = 0

    @Synchronized fun loadAll(): List<EventNetwork> = records().filterKeys { it !in quarantined }.values.toList()
    @Synchronized fun quarantinedIds(): Set<String> { records(); return quarantined.toSet() }

    @Synchronized fun append(event: EventNetwork): Admission {
        val records = try { records() } catch (_: Exception) { return Admission.IO_ERROR }
        if (records.containsKey(event.eventId)) return Admission.STORED
        val line = json.encodeToString(EventNetwork.serializer(), event)
        val bytes = line.toByteArray(Charsets.UTF_8).size
        if (payloadBytes.toLong() + bytes > maxBytes) return Admission.FULL
        if (!appendLine(line)) return Admission.IO_ERROR
        records[event.eventId] = event
        payloadBytes += bytes
        checkpointIfNeeded()
        return Admission.STORED
    }

    /** A single durable acknowledgement covers only the IDs in the successful HTTP batch. */
    @Synchronized fun acknowledge(ids: List<String>): Boolean {
        val records = records()
        val present = ids.filter { it in records }
        if (present.isEmpty()) return true
        if (!appendLine(buildJsonObject { put("_au_ack_ids", JsonArray(present.map(::JsonPrimitive))) }.toString())) return false
        for (id in present) {
            records.remove(id)?.let { payloadBytes -= json.encodeToString(EventNetwork.serializer(), it).toByteArray(Charsets.UTF_8).size }
            quarantined.remove(id)
        }
        removals += present.size
        checkpointIfNeeded()
        return true
    }

    /** Rejected singletons stay on disk, but no longer block delivery of valid events. */
    @Synchronized fun quarantine(id: String): Boolean {
        if (id in quarantined) return true
        if (!appendLine(buildJsonObject { put("_au_quarantine", id) }.toString())) return false
        quarantined.add(id)
        checkpointIfNeeded()
        return true
    }

    @Synchronized fun remove(id: String) { acknowledge(listOf(id)) }
    @Synchronized fun removeOldest(count: Int) { acknowledge(records().keys.take(count.coerceAtLeast(0))) }
    @Synchronized fun count(): Int = records().size

    private fun records(): LinkedHashMap<String, EventNetwork> {
        cached?.let { return it }
        val records = linkedMapOf<String, EventNetwork>()
        val rejected = linkedSetOf<String>()
        var damaged = false
        try {
            if (file.exists()) file.forEachLine { line ->
                if (line.isNotBlank()) {
                    try {
                        val value = json.parseToJsonElement(line).jsonObject
                        when {
                            "_au_ack" in value -> { records.remove(value.getValue("_au_ack").jsonPrimitive.content); removals++ }
                            "_au_ack_ids" in value -> value.getValue("_au_ack_ids").jsonArray.forEach {
                                val id = it.jsonPrimitive.content
                                records.remove(id); rejected.remove(id); removals++
                            }
                            "_au_quarantine" in value -> rejected.add(value.getValue("_au_quarantine").jsonPrimitive.content)
                            else -> { val event = json.decodeFromJsonElement(EventNetwork.serializer(), value); records[event.eventId] = event }
                        }
                    } catch (_: Exception) { damaged = true }
                }
            }
        } catch (error: Exception) {
            persistenceFailed()
            throw error // Never replace an unreadable backlog with an empty cache.
        }
        cached = records
        quarantined.addAll(rejected)
        payloadBytes = records.values.sumOf { json.encodeToString(EventNetwork.serializer(), it).toByteArray(Charsets.UTF_8).size }
        // Legacy files may exceed the new cap: drain them, never truncate them on upgrade.
        if (damaged || removals >= maxOf(64, records.size / 4)) compact()
        return records
    }

    private fun appendLine(line: String): Boolean = try {
        file.parentFile?.mkdirs()
        FileOutputStream(file, true).use { output ->
            // Isolate a torn previous write, including a previous write that threw.
            output.write("\n$line\n".toByteArray(Charsets.UTF_8))
            output.fd.sync()
        }
        true
    } catch (_: Exception) { persistenceFailed(); false }

    private fun checkpointIfNeeded() {
        if (cached?.isEmpty() == true || removals >= maxOf(64, (cached?.size ?: 0) / 4) || file.length() > maxBytes.toLong() + 1024 * 1024) compact()
    }

    private fun compact() {
        val records = cached ?: return
        try {
            if (records.isEmpty()) {
                check(!file.exists() || file.delete())
            } else {
                file.parentFile?.mkdirs()
                val temp = File(file.parentFile, "events.jsonl.tmp")
                FileOutputStream(temp).use { output ->
                    records.values.forEach { output.write((json.encodeToString(EventNetwork.serializer(), it) + "\n").toByteArray(Charsets.UTF_8)) }
                    quarantined.forEach { output.write((buildJsonObject { put("_au_quarantine", it) }.toString() + "\n").toByteArray(Charsets.UTF_8)) }
                    output.fd.sync()
                }
                check(temp.renameTo(file))
            }
            removals = 0
        } catch (_: Exception) { persistenceFailed() }
    }

    private fun persistenceFailed() = AudienzzDiagnostics.log("analytics", "persistenceFailed")
    companion object { internal const val MAX_BYTES = 20 * 1024 * 1024 }
}
