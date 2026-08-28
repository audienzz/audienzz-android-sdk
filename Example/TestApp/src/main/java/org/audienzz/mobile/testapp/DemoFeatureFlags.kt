package org.audienzz.mobile.testapp

import android.content.Context

/**
 * Persisted demo toggles for the test app. Backed by SharedPreferences so the choice survives
 * relaunches. The home screen exposes a Smart Refresh v2 switch that writes this and restarts.
 */
object DemoFeatureFlags {
    private const val PREFS = "demo_feature_flags"
    private const val KEY_SR_V2 = "smartRefreshV2Enabled"

    /** Whether Smart Refresh v2 (screen-aware) is enabled. Defaults to true so the new behavior
     *  is visible out of the box. */
    fun isSmartRefreshV2Enabled(context: Context): Boolean =
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_SR_V2, true)

    fun setSmartRefreshV2Enabled(context: Context, enabled: Boolean) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SR_V2, enabled)
            // commit() (synchronous) — the toggle restarts the process via exit(0) immediately after,
            // which would drop an async apply() write before it reaches disk.
            .commit()
    }
}
