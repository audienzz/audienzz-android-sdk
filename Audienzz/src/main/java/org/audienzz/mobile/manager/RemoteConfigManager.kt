package org.audienzz.mobile.manager

import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.audienzz.mobile.api.config.PublisherConfig
import org.audienzz.mobile.api.config.RemoteAdUnitConfig
import org.audienzz.mobile.repository.RemoteConfigRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RemoteConfigManager @Inject constructor(
    private val repository: RemoteConfigRepository,
) {

    private val scope = CoroutineScope(
        Dispatchers.IO + SupervisorJob() + CoroutineExceptionHandler { _, throwable ->
            Log.e(TAG, "CoroutineScope exception", throwable)
        },
    )

    fun initialize(publisherId: String) {
        Log.d(TAG, "Initializing RemoteConfigManager for publisher: $publisherId")
        scope.launch {
            repository.refreshConfig(publisherId)
        }
    }

    suspend fun getAdUnitConfig(configId: String): RemoteAdUnitConfig? {
        Log.d(TAG, "Getting ad unit config for ID: $configId")
        val config = repository.getAdUnitConfig(configId)
        if (config == null) {
            Log.w(TAG, "No config found for ID: $configId")
        } else {
            Log.d(TAG, "Config found for ID: $configId")
        }
        return config
    }

    suspend fun getPublisherConfig(publisherId: String): PublisherConfig? =
        repository.getPublisherConfig(publisherId)

    companion object {
        private const val TAG = "RemoteConfigManager"
    }
}
