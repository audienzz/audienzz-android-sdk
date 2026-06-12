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

    /** Explicit publisher override. Set via [setAutomaticPpidEnabled]. */
    private var clientOverride: Boolean? = null
    /** Backend-configured value. Set internally after remote config fetch. */
    private var backendEnabled: Boolean? = null
    /** Publisher-provided PPID value (e.g. a hashed user account ID). */
    private var customPpid: String? = null

    /** Resolved enable state: client override → backend value → default (true). */
    private val isEnabled: Boolean get() = clientOverride ?: backendEnabled ?: true

    /** Check if automatic PPID is enabled */
    fun isAutomaticPpidEnabled() = isEnabled

    /** Explicitly enables or disables automatic PPID. Takes priority over the backend value. */
    fun setAutomaticPpidEnabled(isAutomaticPpidEnabled: Boolean) {
        clientOverride = isAutomaticPpidEnabled
    }

    /** Sets a publisher-provided PPID value (e.g. a hashed user account ID).
     *  When set, this value is sent on every ad request instead of the auto-generated UUID.
     *  Pass null to clear and fall back to UUID generation. */
    fun setCustomPpid(ppid: String?) {
        customPpid = ppid
    }

    /** Called internally after remote config is fetched. Not part of the public API. */
    internal fun setBackendPpidEnabled(enabled: Boolean?) {
        backendEnabled = enabled
    }

    /** Returns the PPID to attach to ad requests, or null if PPID is disabled or consent is missing. */
    fun getPpid(): String? {
        if (!isEnabled) {
            Log.d(TAG, "Automatic PPID is disabled")
            return null
        } else if (TargetingParams.getPurposeConsents()?.isEmpty() ?: false) {
            Log.d(TAG, "Consent missing, cannot get PPID")
            return null
        }

        // Use publisher-provided PPID if set
        customPpid?.let { return it }

        // Fallback: auto-generate / rotate UUID
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

    companion object {
        private const val TAG = "PPIDManager"
        private const val MONTH_AGO = 12
        private const val PPID_SHARED_PREFERENCES_KEY = "audienzz_ppid_string"
        private const val PPID_SHARED_PREFERENCES_TIMESTAMP_KEY = "audienzz_ppid_timestamp"
    }
}
