package org.audienzz.mobile.event.preferences

import android.content.SharedPreferences
import javax.inject.Inject

class EventPreferencesImpl @Inject constructor(private val preferences: SharedPreferences) :
    EventPreferences {

    override fun getVisitorId() = preferences.getString(VISITOR_ID, null)

    override fun setVisitorId(visitorId: String) {
        preferences.edit()
            .putString(VISITOR_ID, visitorId)
            .apply()
    }

    companion object {

        private const val VISITOR_ID = "VISITOR_ID"
    }
}
