package org.audienzz.mobile

import androidx.annotation.WorkerThread

interface AudienzzPrebidNativeAdEventListener {

    /**
     * Callback method for Ad's click event
     */
    fun onAdClicked()

    /**
     * Callback method for Ad's click event
     */
    @WorkerThread fun onAdImpression()

    /**
     * Callback method for Ad's click event
     */
    fun onAdExpired()
}
