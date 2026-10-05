package org.audienzz.mobile.api.config

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/** The backend publisher config sends `gamConfig.appVolume`; the SDK once read `setAppVolume`. */
class GamConfigAppVolumeTest {
    // Same settings as NetworkModule.provideJson (network + publisher cache).
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        explicitNulls = false
    }

    private fun config(gamConfig: String) = json.decodeFromString<PublisherConfig>(
        """{"id":35,"prebidServer":{"url":"https://example.test","accountId":1},"gamConfig":$gamConfig}""",
    )

    @Test
    fun readsTheKeyTheBackendSends() {
        assertEquals(0.4f, config("""{"appVolume":0.4}""").gamConfig?.appVolume)
    }

    @Test
    fun stillReadsTheLegacyKey() {
        assertEquals(0.4f, config("""{"setAppVolume":0.4}""").gamConfig?.appVolume)
    }

    @Test
    fun absentVolumeIsMuted() {
        assertEquals(0f, config("{}").gamConfig?.appVolume)
    }

    @Test
    fun volumeSurvivesThePublisherCache() {
        val cached = json.decodeFromString<PublisherConfig>(
            json.encodeToString(PublisherConfig.serializer(), config("""{"appVolume":0.7}""")),
        )
        assertEquals(0.7f, cached.gamConfig?.appVolume)
    }
}
