package org.audienzz.mobile.repository

import org.audienzz.mobile.api.config.PublisherConfig
import org.audienzz.mobile.api.config.RemoteAdUnitConfig

interface RemoteConfigRepository {
    suspend fun refreshConfig(publisherId: String)
    suspend fun getAdUnitConfig(configId: String): RemoteAdUnitConfig?
    suspend fun getPublisherConfig(publisherId: String): PublisherConfig?
}
