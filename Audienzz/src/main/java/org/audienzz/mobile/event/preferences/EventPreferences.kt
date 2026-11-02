package org.audienzz.mobile.event.preferences

interface EventPreferences {

    fun getVisitorId(): String?

    fun setVisitorId(visitorId: String)

    /** The CMP's IAB TCF consent string (`IABTCF_TCString`), or null when none is stored. */
    fun getIabTcString(): String? = null
}
