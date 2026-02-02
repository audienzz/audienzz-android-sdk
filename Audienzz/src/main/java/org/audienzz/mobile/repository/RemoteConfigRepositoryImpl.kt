package org.audienzz.mobile.repository

import android.util.Log
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.audienzz.mobile.api.config.PublisherConfig
import org.audienzz.mobile.api.config.RemoteAdUnitConfig
import org.audienzz.mobile.api.config.RemoteConfigApi
import org.audienzz.mobile.event.database.dao.PublisherConfigDao
import org.audienzz.mobile.event.database.dao.RemoteConfigDao
import org.audienzz.mobile.event.database.entity.PublisherConfigEntity
import org.audienzz.mobile.event.database.entity.RemoteConfigEntity
import javax.inject.Inject

internal class RemoteConfigRepositoryImpl @Inject constructor(
    private val api: RemoteConfigApi,
    private val dao: RemoteConfigDao,
    private val publisherConfigDao: PublisherConfigDao,
    private val json: Json,
) : RemoteConfigRepository {

    override suspend fun refreshConfig(publisherId: String) {
        Log.d(TAG, "Starting config refresh for publisher: $publisherId")
        try {
            // Fetch Publisher Config
            Log.d(TAG, "Fetching publisher config...")
            val publisherConfig = api.getPublisherConfig(publisherId)
            Log.d(TAG, "Publisher config fetched successfully")
            publisherConfigDao.insertPublisherConfig(
                PublisherConfigEntity(
                    publisherId = publisherId,
                    data = json.encodeToString(publisherConfig),
                    timestamp = System.currentTimeMillis(),
                ),
            )

            // Fetch Ad Unit Configs
            Log.d(TAG, "Fetching ad unit configs...")
            val adUnitConfigs = api.getAdUnitConfigs(publisherId)
            Log.d(TAG, "Fetched ${adUnitConfigs.size} ad unit configs")
            val entities = adUnitConfigs.map { config ->
                Log.d(
                    TAG,
                    "Config: $config",
                )
                RemoteConfigEntity(
                    configId = config.id.toString(),
                    adUnitId = config.gamConfig.adUnitPath,
                    adType = config.config.adType,
                    data = json.encodeToString(config),
                    timestamp = System.currentTimeMillis(),
                )
            }
            dao.insertAdConfigs(entities)
            Log.d(TAG, "Config refreshed successfully for publisher: $publisherId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to refresh config for publisher: $publisherId", e)
        }
    }

    override suspend fun getAdUnitConfig(configId: String): RemoteAdUnitConfig? {
        Log.v(TAG, "Querying database for config ID: $configId")
        val entity = dao.getAdConfig(configId)
        if (entity == null) {
            Log.w(TAG, "No entity found in database for config ID: $configId")
            return null
        }

        Log.d(TAG, "Entity found for config ID: $configId, attempting to parse JSON")
        return try {
            val config = json.decodeFromString<RemoteAdUnitConfig>(entity.data)
            Log.d(TAG, "Successfully parsed config for ID: $configId")
            config
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse cached config for id: $configId", e)
            Log.e(TAG, "Raw data: ${entity.data}")
            null
        }
    }

    override suspend fun getPublisherConfig(publisherId: String): PublisherConfig? {
        return publisherConfigDao.getPublisherConfig(publisherId)?.let { entity ->
            try {
                json.decodeFromString<PublisherConfig>(entity.data)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse cached publisher config for id: $publisherId", e)
                null
            }
        }
    }

    companion object {
        private const val TAG = "RemoteConfigRepository"
    }
}
