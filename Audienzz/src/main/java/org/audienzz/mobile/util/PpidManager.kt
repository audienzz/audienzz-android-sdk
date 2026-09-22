package org.audienzz.mobile.util

import android.content.SharedPreferences
import android.util.Log
import org.audienzz.mobile.AudienzzPrebidMobile
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
     *   1. `null` when the backend has switched PPIDs off for this publisher.
     *   2. Publisher-supplied PPID (if set via [setPublisherPpid]).
     *   3. SDK-generated UUID, persisted and rotated every 12 months.
     *
     * **`ppidEnabled` in the publisher config is the only thing that suppresses a PPID.** It is a
     * top-level boolean on `GET /publishers/{id}`, and absent means enabled. A missing PPID costs
     * frequency capping and cross-session targeting, so the SDK generates and persists one rather
     * than leaving the field empty.
     *
     * Two gates were removed to make that true:
     *
     *  * An empty TCF `purposeConsents` string used to suppress the PPID. That check fired on
     *    *unknown* consent (no CMP yet) but not on an explicit denial such as `0000000000`, which
     *    is a nonempty string — so it suppressed the ambiguous case and allowed the clear one.
     *    Consent is not gated here at all now; if it should be, it needs a real purpose check
     *    rather than a test for emptiness, and that is a policy decision.
     *  * `automaticPpidEnabled` used to suppress the generated UUID. The backend sends no such
     *    field on any endpoint the SDK calls, so it never did anything; it has been deleted from
     *    the model, the public API and the bridges.
     */
    fun getPpid(): String? {
        // The only switch. Per-publisher, backend-owned, and it suppresses the publisher's own
        // identifier too — honouring it only for the generated UUID would miss the point.
        if (!AudienzzPrebidMobile.isPpidEnabled()) {
            Log.d(TAG, "PPID disabled by the publisher config (ppidEnabled = false)")
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
