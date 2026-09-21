package org.audienzz.mobile.event.id

import android.content.Context
import android.util.Log
import com.google.android.gms.ads.identifier.AdvertisingIdClient
import javax.inject.Inject

/**
 * The Google advertising identifier, or nothing.
 *
 * `null` means "not available" and the field is omitted from the payload: analytics must work
 * without it. Nothing is substituted — not the PPID, not an app-scoped id, not a fingerprint.
 *
 * Three things this deliberately does NOT do, each of which it used to:
 *
 *  * It does not report the string `"empty"` as a device identity. A placeholder in an id column is
 *    worse than an absent one: it aggregates, joins and counts as if it were a device.
 *  * It does not report the all-zero UUID, which is what the platform returns when the user has
 *    limited ad tracking. That is a sentinel, not an identity.
 *  * It does not cache for the process lifetime. A `by lazy` read meant consent granted after the
 *    first event was never picked up, and — worse — consent *revoked* after an authorized event
 *    left the SDK emitting an identifier the user had withdrawn. The value is re-read, with a short
 *    TTL only because `AdvertisingIdClient` is a blocking binder call that must not run per event
 *    on a hot path.
 */
class AdIdProviderImpl @Inject constructor(
    private val context: Context,
) : AdIdProvider {

    @Volatile private var cached: String? = null
    @Volatile private var cachedAtMillis: Long = 0

    internal var now: () -> Long = { System.currentTimeMillis() }

    override fun getAdId(): String? {
        val age = now() - cachedAtMillis
        if (cachedAtMillis != 0L && age < TTL_MILLIS) return cached
        cached = read()
        cachedAtMillis = now()
        return cached
    }

    private fun read(): String? = runCatching {
        val info = AdvertisingIdClient.getAdvertisingIdInfo(context)
        // Limit-ad-tracking is an explicit withdrawal; the platform also zeroes the id, but the
        // flag is the authoritative signal and is checked first.
        if (info.isLimitAdTrackingEnabled) null else usableAdId(info.id)
    }.onFailure { Log.d(TAG, "Failed to get ad id", it) }.getOrNull()

    companion object {

        private const val TAG = "AdIdProvider"

        /** Short enough that a revocation is reflected within a session, long enough to stay cheap. */
        private const val TTL_MILLIS = 60_000L

        private const val ZERO_AD_ID = "00000000-0000-0000-0000-000000000000"

        /** The rule that turns a raw advertising id into either an identity or nothing. */
        @JvmStatic
        fun usableAdId(raw: String?): String? {
            val value = raw?.trim()?.lowercase()
            if (value.isNullOrEmpty()) return null
            if (value == ZERO_AD_ID) return null
            return value
        }
    }
}
