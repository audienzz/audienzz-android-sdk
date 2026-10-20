package org.audienzz.mobile.api.config

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The config's own `sdkId` is what analytics reports as `sdk_id`. */
class SdkIdConfigTest {
    // Same settings as NetworkModule.provideJson (network + publisher cache).
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false; explicitNulls = false }
    private fun config(sdkId: String) = json.decodeFromString<PublisherConfig>(
        """{"id":35,$sdkId"prebidServer":{"url":"https://example.test","accountId":1}}""",
    )

    @Test fun readsAString() = assertEquals("35", config(""""sdkId":"35",""").sdkId)
    @Test fun readsANumber() = assertEquals("35", config(""""sdkId":35,""").sdkId)
    @Test fun missingOrBlankIsNull() {
        assertNull(config("").sdkId)
        assertNull(config(""""sdkId":" ",""").sdkId)
        assertNull(config(""""sdkId":null,""").sdkId)
    }
    @Test fun survivesThePublisherCache() {
        val cached = json.decodeFromString<PublisherConfig>(
            json.encodeToString(PublisherConfig.serializer(), config(""""sdkId":"sdk-7",""")),
        )
        assertEquals("sdk-7", cached.sdkId)
        assertNull(json.decodeFromString<PublisherConfig>(
            json.encodeToString(PublisherConfig.serializer(), config(""))).sdkId)
    }
}
