package org.audienzz.mobile.repository

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import okhttp3.ResponseBody.Companion.toResponseBody
import org.audienzz.mobile.api.config.PrebidServerConfig
import org.audienzz.mobile.api.config.PublisherConfig
import org.audienzz.mobile.api.config.RemoteConfigApi
import org.audienzz.mobile.event.database.entity.PublisherConfigEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import retrofit2.HttpException
import retrofit2.Response

/** ws-sdk-config moved to `sdks/{sdkId}`; `publishers/` stays only for released SDKs. */
@RunWith(RobolectricTestRunner::class)
class SdkConfigRouteTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false; explicitNulls = false }
    private val api = mockk<RemoteConfigApi>()
    private val stored = mutableListOf<PublisherConfigEntity>()
    private val repository = RemoteConfigRepositoryImpl(
        api,
        mockk(relaxed = true),
        mockk(relaxed = true) { coEvery { insertPublisherConfig(capture(stored)) } returns Unit },
        json,
    )
    private val config = PublisherConfig(id = 35, sdkId = "35",
        prebidServerConfig = PrebidServerConfig(url = "https://pbs.test", accountId = 1))
    private fun http(code: Int) = HttpException(Response.error<Any>(code, "".toResponseBody()))

    @Test fun readsTheSdksRoute() = runTest {
        coEvery { api.getSdkConfig("35") } returns config
        coEvery { api.getSdkAdUnitConfigs("35") } returns JsonArray(emptyList())
        repository.refreshConfig("35")
        coVerify(exactly = 0) { api.getPublisherConfig(any()) }
        coVerify(exactly = 0) { api.getAdUnitConfigs(any()) }
        assertEquals("35", json.decodeFromString<PublisherConfig>(stored.single().data).sdkId)
    }

    @Test fun aServerWithoutTheSdksRouteFallsBackToPublishers() = runTest {
        coEvery { api.getSdkConfig("35") } throws http(404)
        coEvery { api.getSdkAdUnitConfigs("35") } throws http(404)
        coEvery { api.getPublisherConfig("35") } returns config
        coEvery { api.getAdUnitConfigs("35") } returns JsonArray(emptyList())
        repository.refreshConfig("35")
        assertEquals(1, stored.size)
        coVerify { api.getAdUnitConfigs("35") }
    }

    @Test fun anyOtherFailureIsNotRetriedOnTheOldRoute() = runTest {
        coEvery { api.getSdkConfig("35") } throws http(500)
        repository.refreshConfig("35")
        coVerify(exactly = 0) { api.getPublisherConfig(any()) }
        assertEquals(0, stored.size)
    }
}
