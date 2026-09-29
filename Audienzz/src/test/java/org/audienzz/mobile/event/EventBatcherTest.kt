package org.audienzz.mobile.event

import android.util.Log
import io.mockk.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.*
import org.audienzz.mobile.event.network.entity.EventNetwork
import org.audienzz.mobile.event.repository.remote.RemoteEventRepository
import org.audienzz.mobile.util.AppForegroundMonitor
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
internal class EventBatcherTest {
    private lateinit var repository: RemoteEventRepository
    private lateinit var store: EventStore
    private val senders = mutableListOf<EventBatcher>()
    @Before fun setUp() {
        mockkStatic(Log::class)
        every { Log.e(any(), any(), any()) } returns 0
        repository = mockk()
        coEvery { repository.submitBatch(any()) } returns Unit
        store = mockk(relaxed = true)
        every { store.loadAll() } returns emptyList()
    }
    @After fun tearDown() {
        senders.forEach { it.cancel(); AppForegroundMonitor.removeListener(it) }
        unmockkStatic(Log::class)
    }
    private fun sender(scheduler: TestCoroutineScheduler) =
        EventBatcher(repository, store, StandardTestDispatcher(scheduler)).also { senders.add(it) }

    private fun event(index: Int) = EventNetwork(
        eventType = "adClick",
        companyId = "company",
        source = "android-sdk",
        eventId = index.toString(),
        pageImpressionId = "page",
        sessionId = "session",
        sessionStartTimestamp = 1_789_978_756L,
        sessionSeq = index,
        eventTimestamp = "2026-09-22T00:00:00.000Z",
        locale = "en-US",
        zoneOffsetSeconds = 0,
        screenHeight = 800,
        screenWidth = 400,
        viewportHeight = 800,
        viewportWidth = 400,
        deviceId = null,
        userAgent = null,
        osName = "Android",
        deviceCategory = "Smartphone",
        browserName = "Android WebView",
        sdkName = "android",
        sdkVersion = "0.2.2",
        appPackageName = "org.example",
        appVersion = "1.0",
        appTitle = "Example",
        screenName = "Screen",
        pageUrl = null,
        visitorId = "visitor",
        attributes = emptyMap(),
    )


    @Test fun `one event sends immediately without advancing time and is persisted first`() = runTest {
        val sender = sender(testScheduler)
        coEvery { repository.submitBatch(any()) } coAnswers {
            verify(exactly = 1) { store.append(match { it.eventId == "0" }, any()) }
        }
        sender.enqueue(event(0))
        // enqueue itself must not do filesystem I/O on the caller (possibly main) thread.
        verify(exactly = 0) { store.append(any(), any()) }
        runCurrent()
        coVerify(exactly = 1) { repository.submitBatch(match { it.map { e -> e.eventId } == listOf("0") }) }
        verify(exactly = 1) { store.remove("0") }
        assertEquals(0L, currentTime)
    }

    @Test fun `bursts remain individual requests with only one in flight`() = runTest {
        val done = CompletableDeferred<Unit>()
        coEvery { repository.submitBatch(any()) } coAnswers { done.await() }
        val sender = sender(testScheduler)
        repeat(30) { sender.enqueue(event(it)) }
        runCurrent()
        coVerify(exactly = 1) { repository.submitBatch(any()) }
        verify(exactly = 30) { store.append(any(), any()) }
        done.complete(Unit)
        runCurrent()
        coVerify(exactly = 30) { repository.submitBatch(match { it.size == 1 }) }
        verify(exactly = 30) { store.remove(any()) }
    }

    @Test fun `failures survive more than three retries and lifecycle hints do not bypass backoff`() = runTest {
        coEvery { repository.submitBatch(any()) } throws IOException("offline")
        val sender = sender(testScheduler)
        sender.enqueue(event(0))
        runCurrent()
        var attempts = 1
        for (delay in listOf(2000L, 4000L, 8000L, 16000L, 32000L, 60000L, 60000L)) {
            repeat(20) { sender.flush() }
            advanceTimeBy(delay - 1); runCurrent()
            coVerify(exactly = attempts) { repository.submitBatch(any()) }
            advanceTimeBy(1); runCurrent()
            attempts++
            coVerify(exactly = attempts) { repository.submitBatch(match { it.single().eventId == "0" }) }
            verify(exactly = 0) { store.remove(any()) }
        }
        sender.cancel()
    }

    @Test fun `new events during failure are persisted without bypassing cooldown`() = runTest {
        coEvery { repository.submitBatch(any()) } throws IOException("offline")
        val sender = sender(testScheduler)
        sender.enqueue(event(0)); runCurrent()
        sender.enqueue(event(1)); sender.flush(); runCurrent()
        verify { store.append(match { it.eventId == "1" }, any()) }
        coVerify(exactly = 1) { repository.submitBatch(any()) }
        coEvery { repository.submitBatch(any()) } returns Unit
        advanceTimeBy(2000); runCurrent()
        val sent = mutableListOf<List<EventNetwork>>()
        coVerify(exactly = 3) { repository.submitBatch(capture(sent)) }
        assertEquals(listOf("0", "0", "1"), sent.flatten().map { it.eventId })
        verify { store.remove("0"); store.remove("1") }
    }

    @Test fun `restore runs before new events and sends every identity just once`() = runTest {
        every { store.loadAll() } returns listOf(event(100), event(101))
        val sender = sender(testScheduler)
        sender.enqueue(event(0)) // Before the consumer runs: formerly vulnerable to restore races.
        runCurrent()
        val sent = mutableListOf<List<EventNetwork>>()
        coVerify(exactly = 3) { repository.submitBatch(capture(sent)) }
        assertEquals(listOf("100", "101", "0"), sent.flatten().map { it.eventId })
    }

    @Test fun `overflow cannot let an old acknowledgement remove newer events`() = runTest {
        val done = CompletableDeferred<Unit>()
        coEvery { repository.submitBatch(any()) } coAnswers { done.await() }
        val sender = sender(testScheduler)
        sender.enqueue(event(0)); runCurrent()
        // Process each enqueue while 0 stays in flight, overflowing the persistent pending set.
        for (i in 1..510) { sender.enqueue(event(i)); runCurrent() }
        verify { store.append(match { it.eventId == "510" }, "0") }
        verify(exactly = 0) { store.remove("0") }
        done.complete(Unit); runCurrent()
        val sent = mutableListOf<List<EventNetwork>>()
        coVerify(exactly = 500) { repository.submitBatch(capture(sent)) }
        assertEquals(listOf("0") + (12..510).map(Int::toString), sent.flatten().map { it.eventId })
        verify(exactly = 1) { store.remove("0") }
        verify(exactly = 1) { store.remove("510") }
    }
    @Test fun `retry rotation plus overflow removes exactly one persisted event`() = runTest {
        val directory = java.nio.file.Files.createTempDirectory("analytics-overflow").toFile()
        val context = mockk<android.content.Context>()
        every { context.filesDir } returns directory
        store = EventStore(context)
        val sender = sender(testScheduler)
        try {
            coEvery { repository.submitBatch(any()) } throws IOException("offline")
            sender.enqueue(event(0)); runCurrent()
            for (i in 1..499) { sender.enqueue(event(i)); runCurrent() }
            advanceTimeBy(2000); runCurrent() // 0 fails again and rotates behind 1..499.
            sender.enqueue(event(500)); runCurrent()
            val restored = EventStore(context).loadAll().map { it.eventId }.toSet()
            assertEquals((0..500).filter { it != 1 }.map(Int::toString).toSet(), restored)
        } finally {
            sender.cancel()
            directory.deleteRecursively()
        }
    }

}
