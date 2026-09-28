package org.audienzz.mobile.event

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.audienzz.mobile.di.module.NetworkModule
import org.audienzz.mobile.event.entity.EventDomain
import org.audienzz.mobile.event.entity.EventType
import org.audienzz.mobile.event.id.AdIdProvider
import org.audienzz.mobile.event.id.CompanyIdProvider
import org.audienzz.mobile.event.network.mapper.EventNetworkMapper
import org.audienzz.mobile.event.preferences.EventPreferences
import org.audienzz.mobile.event.repository.remote.RemoteEventRepositoryImpl
import org.audienzz.mobile.util.AppForegroundMonitor
import org.audienzz.mobile.util.AudienzzDiagnostics
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.util.concurrent.CopyOnWriteArrayList

/** Exercises enrichment, persistence, batching, Retrofit and serialization together. No live POSTs. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AnalyticsTransportTest {
    @Test
    fun `events without an advertising ID reach the collector route after 15 seconds`() = verifyDelivery(false)

    @Test
    fun `an HTML HTTP failure retries the same batch and a 204 drains it`() = verifyDelivery(true)

    private fun verifyDelivery(failFirst: Boolean) = runTest {
        val context = RuntimeEnvironment.getApplication()
        val store = EventStore(context)
        store.removeOldest(Int.MAX_VALUE)
        val module = NetworkModule()
        val json = module.provideJson()
        val sent = CompletableDeferred<Unit>()
        val bodies = CopyOnWriteArrayList<JsonArray>()
        val diagnostics = CopyOnWriteArrayList<String>()
        val oldSink = AudienzzDiagnostics.sink
        val oldEnabled = AudienzzDiagnostics.isEnabled
        AudienzzDiagnostics.isEnabled = true
        AudienzzDiagnostics.sink = { diagnostics.add(it) }
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            // Deliberately do not call proceed(): fixture data must never leave this process.
            val request = chain.request()
            assertEquals("https://api.adnz.co/api/ws-clickstream-collector/submit/batch", request.url.toString())
            assertEquals("POST", request.method)
            val buffer = Buffer()
            requireNotNull(request.body).writeTo(buffer)
            bodies.add(json.parseToJsonElement(buffer.readUtf8()) as JsonArray)
            val status = if (failFirst && bodies.size == 1) 403 else 204
            if (status == 204) sent.complete(Unit)
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(status).message("fixture")
                .body((if (status == 403) "<html>Forbidden</html>" else "").toResponseBody())
                .build()
        }.build()
        val api = module.provideAuthApiService(module.provideJsonConverterFactory(json), client)
        val dispatcher = StandardTestDispatcher(testScheduler)
        val batcher = EventBatcher(RemoteEventRepositoryImpl(api), store, dispatcher)
        val preferences = mockk<EventPreferences>(relaxed = true)
        every { preferences.getVisitorId() } returns "fixture-visitor"
        val adId = mockk<AdIdProvider>()
        every { adId.getAdId() } returns null
        val company = mockk<CompanyIdProvider>()
        every { company.getCompanyId() } returns "fixture-company"
        val logger = EventLoggerImpl(batcher, EventNetworkMapper(context), preferences, adId, company, dispatcher)
        try {
            runCurrent() // Finish restoring the (empty) persistent queue first.
            logger.onScreenResumed("fixture-screen")
            logger.logEvent(EventDomain(eventType = EventType.AD_IMPRESSION))
            runCurrent()
            assertEquals(2, store.count())
            advanceTimeBy(14_999)
            runCurrent()
            assertTrue("A partial batch should wait for its deadline", bodies.isEmpty())
            advanceTimeBy(1)
            runCurrent()
            sent.await() // runTest drives the consumer while OkHttp replies on its own thread.
            // Wait for acknowledgement processing too, not merely arrival at the interceptor.
            withContext(Dispatchers.Default) {
                withTimeout(5_000) { while (store.count() != 0) delay(10) }
            }
            assertEquals(if (failFirst) 2 else 1, bodies.size)
            val events = bodies.last().map { it.jsonObject }
            assertEquals(listOf("pageImpression", "adImpression"), events.map { it["event_type"]?.jsonPrimitive?.content })
            assertTrue(events.all { it["device_id"] == null })
            assertTrue(events.all { !it["event_id"]?.jsonPrimitive?.content.isNullOrBlank() })
            if (failFirst) assertEquals(bodies.first(), bodies.last())
            assertTrue(diagnostics.any { it.startsWith("AUDZ analytics sending") })
            assertTrue(diagnostics.any { it.startsWith("AUDZ analytics sent") })
            if (failFirst) assertTrue(diagnostics.any { it.startsWith("AUDZ analytics failed") && it.contains("status=403") })
            assertFalse(diagnostics.any { it.contains("fixture-") || it.contains("device_id") })
        } finally {
            logger.cancel()
            batcher.cancel()
            AppForegroundMonitor.removeListener(batcher)
            client.dispatcher.executorService.shutdownNow()
            client.connectionPool.evictAll()
            store.removeOldest(Int.MAX_VALUE)
            AudienzzDiagnostics.sink = oldSink
            AudienzzDiagnostics.isEnabled = oldEnabled
        }
    }
}
