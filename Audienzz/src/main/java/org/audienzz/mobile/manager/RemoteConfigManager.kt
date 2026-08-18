package org.audienzz.mobile.manager

import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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

    /** The in-flight initial network refresh, awaited once by readers on a cold-start cache miss. */
    private var initialRefreshJob: Job? = null

    fun initialize(publisherId: String) {
        Log.d(TAG, "Initializing RemoteConfigManager for publisher: $publisherId")
        initialRefreshJob = scope.launch {
            repository.refreshConfig(publisherId)
        }
    }

    suspend fun getAdUnitConfig(configId: String): RemoteAdUnitConfig? {
        Log.d(TAG, "Getting ad unit config for ID: $configId")
        var config = repository.getAdUnitConfig(configId)
        if (config == null) {
            // H8: on a cold start the cache is empty only because the initial network refresh is
            // still in flight. Await it once, then re-read — instead of returning null and leaving
            // the caller (e.g. AudienzzRemoteBannerView) to log-and-give-up with no retry.
            Log.d(TAG, "No cached config for ID: $configId — awaiting initial refresh, then retrying")
            initialRefreshJob?.join()
            config = repository.getAdUnitConfig(configId)
        }
        if (config == null) {
            Log.w(TAG, "No config found for ID: $configId (after awaiting initial refresh)")
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
