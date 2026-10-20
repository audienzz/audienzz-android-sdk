package org.audienzz.mobile.event.preferences

import android.content.Context
import android.content.SharedPreferences
import javax.inject.Inject

class EventPreferencesImpl @Inject constructor(
    private val preferences: SharedPreferences,
    private val context: Context,
) : EventPreferences {

    override fun getVisitorId() = preferences.getString(VISITOR_ID, null)

    override fun setVisitorId(visitorId: String) {
        preferences.edit()
            .putString(VISITOR_ID, visitorId)
            .apply()
    }

    /**
     * Read straight from the app's default preferences, where IAB TCF requires every CMP to write
     * it. Prebid only exposes it once it has initialized, which is after the first page of a
     * session is usually reported.
     */
    override fun getIabTcString(): String? = try {
        context.getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE)
            .getString(IAB_TC_STRING, null)?.takeIf { it.isNotBlank() }
    } catch (e: ClassCastException) {
        null // A CMP that stored it as something other than a string.
    }

    companion object {

        private const val VISITOR_ID = "VISITOR_ID"
        private const val IAB_TC_STRING = "IABTCF_TCString"
    }
}
