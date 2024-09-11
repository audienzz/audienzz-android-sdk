package org.audienzz.mobile.event.id

import android.content.Context
import android.util.Log
import com.google.android.gms.ads.identifier.AdvertisingIdClient
import javax.inject.Inject

class AdIdProviderImpl @Inject constructor(
    private val context: Context,
) : AdIdProvider {

    private val deviceId by lazy {
        runCatching { AdvertisingIdClient.getAdvertisingIdInfo(context).id }
            .onFailure { Log.d(TAG, "Failed to get ad id", it) }
            .getOrDefault("empty")
    }

    override fun getAdId() = deviceId

    companion object {

        private const val TAG = "AdIdProvider"
    }
}
