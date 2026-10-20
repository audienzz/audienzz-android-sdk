package org.audienzz.mobile.api.config

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/** `config.nativeConfig` is read leniently: a malformed value turns native off, not the config. */
class RemoteNativeConfigTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun parse(nativeConfig: String?): RemoteAdUnitConfig {
        val field = nativeConfig?.let { ""","nativeConfig":$it""" }.orEmpty()
        return json.decodeFromString(
            RemoteAdUnitConfig.serializer(),
            """{"id":7,"config":{"adType":"banner"$field},""" +
                """"gamConfig":{"adUnitPath":"/1/unit","adSizes":["300x250"]},""" +
                """"prebidConfig":{"placementId":"p","adSizes":["300x250"]}}""",
        )
    }

    @Test fun `absent is disabled`() {
        assertEquals(RemoteNativeConfig.DISABLED, parse(null).config.nativeConfig)
    }

    @Test fun `enabled without fluid`() {
        assertEquals(
            RemoteNativeConfig(enabled = true, fluid = false),
            parse("""{"enabled":true}""").config.nativeConfig,
        )
    }

    @Test fun `enabled with fluid`() {
        assertEquals(
            RemoteNativeConfig(enabled = true, fluid = true),
            parse("""{"enabled":true,"fluid":true}""").config.nativeConfig,
        )
    }

    @Test fun `fluid alone does nothing`() {
        val config = parse("""{"enabled":false,"fluid":true}""")
        assertEquals(RemoteNativeConfig.DISABLED, config.config.nativeConfig)
    }

    @Test fun `malformed values are off and the rest of the config still parses`() {
        val malformed = listOf(
            "true",
            "\"yes\"",
            "null",
            "[]",
            """{"enabled":"true"}""",
            """{"enabled":1}""",
        )
        malformed.forEach { raw ->
            val config = parse(raw)
            assertEquals(raw, RemoteNativeConfig.DISABLED, config.config.nativeConfig)
            assertEquals(raw, "/1/unit", config.gamConfig.adUnitPath)
        }
    }
}
