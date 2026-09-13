package org.audienzz.mobile.util

import android.content.SharedPreferences
import android.util.Log
import org.prebid.mobile.TargetingParams
import java.util.Calendar
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PpidManager @Inject constructor(private val preferences: SharedPreferences) {
    /**
     * Provide a publisher-owned PPID (e.g. a hashed e-mail address).
     * When set this always takes precedence over the SDK-generated UUID.
     * Pass `null` to clear and fall back to the generated UUID.
     */
    fun setPublisherPpid(ppid: String?) {
        publisherPpid = ppid
    }

    /**
     * Returns the active PPID:
     *   1. Publisher-supplied PPID (if set via [setPublisherPpid]).
     *   2. SDK-generated UUID (persisted, rotated every 12 months).
     *   3. `null` only when consent is missing.
     *
     * A PPID is always sent otherwise — there is no opt-out switch. A missing PPID costs frequency
     * capping and cross-session targeting, so the SDK generates and persists one rather than
     * leaving the field empty.
     */
    fun getPpid(): String? {
        if (TargetingParams.getPurposeConsents()?.isEmpty() ?: false) {
            Log.d(TAG, "Consent missing, cannot get PPID")
            return null
        }

        publisherPpid?.let { return it }

        var ppid = getPpidFromSharedPreferences()
        val ppidTimestamp = getPpidTimestamp()

        return if (ppid != null && ppidTimestamp != 0L) {
            if (isOlderThanYear(ppidTimestamp)) {
                Log.d(TAG, "PPID timestamp is older than 12 months, generating new one")
                ppid = UUID.randomUUID().toString()
                storePpidToSharedPreferences(ppid)
                ppid
            } else {
                ppid
            }
        } else {
            Log.d(TAG, "PPID is null or timestamp is null, generating new one")
            ppid = UUID.randomUUID().toString()
            storePpidToSharedPreferences(ppid)
            ppid
        }
    }

    private fun getPpidFromSharedPreferences(): String? =
        preferences.getString(PPID_SHARED_PREFERENCES_KEY, null)

    private fun getPpidTimestamp(): Long =
        preferences.getLong(PPID_SHARED_PREFERENCES_TIMESTAMP_KEY, 0L)

    private fun storePpidToSharedPreferences(ppid: String) {
        val timestamp = System.currentTimeMillis()
        with(preferences.edit()) {
            putString(PPID_SHARED_PREFERENCES_KEY, ppid)
            putLong(PPID_SHARED_PREFERENCES_TIMESTAMP_KEY, timestamp)
            apply()
        }
    }

    private fun isOlderThanYear(timestamp: Long): Boolean {
        val currentTime = System.currentTimeMillis()
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = currentTime
        calendar.add(Calendar.MONTH, -MONTH_AGO)
        val oneYearAgo = calendar.timeInMillis
        return timestamp < oneYearAgo
    }

    companion object Companion {
        @Volatile
        private var publisherPpid: String? = null

        private const val TAG = "PPIDManager"
        private const val MONTH_AGO = 12
        private const val PPID_SHARED_PREFERENCES_KEY = "audienzz_ppid_string"
        private const val PPID_SHARED_PREFERENCES_TIMESTAMP_KEY = "audienzz_ppid_timestamp"
    }
}
