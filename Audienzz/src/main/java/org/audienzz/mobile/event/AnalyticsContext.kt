package org.audienzz.mobile.event

/** Analytics identity is independent of the Prebid account/schain seller. Read once per event. */
internal object AnalyticsContext {
    data class Snapshot(val publisherId: String?, val environment: String)
    @Volatile private var context = Snapshot(null, "production")

    @Synchronized fun configure(publisherId: String?, environment: String): Boolean {
        if (environment !in setOf("production", "staging", "test")) return false
        context = Snapshot(publisherId?.trim()?.takeIf { it.isNotEmpty() }, environment)
        return true
    }

    @Synchronized fun setPublisherId(publisherId: String?) {
        context = context.copy(publisherId = publisherId?.trim()?.takeIf { it.isNotEmpty() })
    }

    fun snapshot(): Snapshot = context
}
