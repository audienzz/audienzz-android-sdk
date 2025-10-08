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
    /** Check if automatic PPID is enabled */
    fun isAutomaticPpidEnabled() = isAutomaticPpidEnabled

    /** Used to enable or disable automatic PPID usage */
    fun setAutomaticPpidEnabled(isAutomaticPpidEnabled: Boolean) {
        PpidManager.isAutomaticPpidEnabled = isAutomaticPpidEnabled
    }

    /** Used to obtain PPID if automaticPpid is enabled */
    fun getPpid(): String? {
        if (!isAutomaticPpidEnabled) {
            Log.d(TAG, "Automatic PPID is disabled")
            return null
        } else if (TargetingParams.getPurposeConsents()?.isEmpty() ?: false) {
            Log.d(TAG, "Consent missing, cannot get PPID")
            return null
        } else {
            var ppid = getPpidFromSharedPreferences()
            val ppidTimestamp = getPpidTimestamp()

            if (ppid != null && ppidTimestamp != 0L) {
                if (isOlderThenYear(ppidTimestamp)) {
                    Log.d(TAG, "PPID timestamp is older than 12 months, generating new one")
                    ppid = UUID.randomUUID().toString()
                    storePpidToSharedPreferences(ppid)
                    return ppid
                } else {
                    return ppid
                }
            } else {
                Log.d(TAG, "PPID is null or timestamp is null, generating new one")
                ppid = UUID.randomUUID().toString()
                storePpidToSharedPreferences(ppid)
                return ppid
            }
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

    private fun isOlderThenYear(timestamp: Long): Boolean {
        val currentTime = System.currentTimeMillis()
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = currentTime
        calendar.add(Calendar.MONTH, -MONTH_AGO)
        val oneYearAgo = calendar.timeInMillis
        return timestamp < oneYearAgo
    }

    companion object Companion {
        private var isAutomaticPpidEnabled = false

        private const val TAG = "PPIDManager"
        private const val MONTH_AGO = 12
        private const val PPID_SHARED_PREFERENCES_KEY = "audienzz_ppid_string"
        private const val PPID_SHARED_PREFERENCES_TIMESTAMP_KEY = "audienzz_ppid_timestamp"
    }
}
