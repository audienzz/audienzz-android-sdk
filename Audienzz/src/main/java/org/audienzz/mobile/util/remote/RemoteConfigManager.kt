package org.audienzz.mobile.util.remote

object RemoteConfigManager {
    private lateinit var publisherId: String
    private lateinit var remoteUrl: String
    var isInitialized = false
        private set

    @JvmStatic
    fun initialize(publisherId: String, remoteUrl: String) {
        this.publisherId = publisherId
        this.remoteUrl = remoteUrl
        this.isInitialized = true
    }
}
