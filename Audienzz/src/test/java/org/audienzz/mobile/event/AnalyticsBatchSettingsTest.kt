package org.audienzz.mobile.event

import android.util.Log
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import org.audienzz.mobile.api.config.PublisherConfig
import org.audienzz.mobile.manager.RemoteConfigManager
import org.audienzz.mobile.repository.RemoteConfigRepository
import org.junit.*
import org.junit.Assert.*

class AnalyticsBatchSettingsTest {
    private val json = Json { ignoreUnknownKeys = true }
    private fun config(value: String? = null): PublisherConfig {
        val field = value?.let { ",\"analyticsBatchSize\":" + it } ?: ""
        return json.decodeFromString("""{"id":35,"prebidServer":{"url":"https://example.test","accountId":1}""" + field + "}")
    }

    @Before fun setUp() {
        AnalyticsBatchSettings.applyBackendConfig(null)
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
    }
    @After fun tearDown() { AnalyticsBatchSettings.applyBackendConfig(null); unmockkStatic(Log::class) }

    @Test fun optionalFieldToleratesMalformedBackendData() {
        for (value in listOf(null, "null", "\"\"", "\"  \"", "0", "-2", "false", "{}", "[]", "3.5")) {
            val parsed = config(value)
            assertEquals(value, 10, AnalyticsBatchSettings.resolve(parsed.analyticsBatchSize))
            assertEquals(35, parsed.id)
        }
        assertEquals(1, config("1").analyticsBatchSize)
        assertEquals(7, config("\" 7 \"").analyticsBatchSize)
        assertEquals(15, config("999").analyticsBatchSize)
    }

    @Test fun fieldSurvivesPublisherCacheEncoding() {
        val cached = json.decodeFromString<PublisherConfig>(json.encodeToString(PublisherConfig.serializer(), config("8")))
        assertEquals(8, cached.analyticsBatchSize)
    }

    @Test fun remoteInitializationAppliesCachedThenFreshPolicy() = runBlocking {
        withTimeout(5000) {
            val repository = mockk<RemoteConfigRepository>()
            val started = CompletableDeferred<Unit>()
            val finish = CompletableDeferred<Unit>()
            var refreshed = false
            coEvery { repository.getPublisherConfig("35") } coAnswers { config(if (refreshed) "15" else "8") }
            coEvery { repository.refreshConfig("35") } coAnswers {
                started.complete(Unit); finish.await(); refreshed = true
            }
            coEvery { repository.getAdUnitConfig(any()) } returns null
            val manager = RemoteConfigManager(repository)
            manager.initialize("35")
            started.await()
            assertEquals(8, AnalyticsBatchSettings.current())
            finish.complete(Unit)
            manager.getAdUnitConfig("missing") // Waits on the real initial-refresh job.
            assertEquals(15, AnalyticsBatchSettings.current())
            coEvery { repository.getPublisherConfig("35") } returns config()
            coEvery { repository.refreshConfig("35") } returns Unit
            manager.initialize("35")
            manager.getAdUnitConfig("missing")
            assertEquals(10, AnalyticsBatchSettings.current())
        }
    }
}
