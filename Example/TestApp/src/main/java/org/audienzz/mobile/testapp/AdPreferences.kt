package org.audienzz.mobile.testapp

import android.content.Context
import android.widget.Toast
import androidx.core.content.edit
import org.audienzz.mobile.testapp.adapter.BannerOriginalLazyAdapter.Companion.HOLDER_TYPE_HTML_BANNER_ADS
import org.audienzz.mobile.testapp.adapter.BannerOriginalLazyAdapter.Companion.HOLDER_TYPE_INTERSTITIAL_ADS
import org.audienzz.mobile.testapp.adapter.BannerOriginalLazyAdapter.Companion.HOLDER_TYPE_REWARDED_AD
import org.audienzz.mobile.testapp.adapter.BannerOriginalLazyAdapter.Companion.HOLDER_TYPE_UNFILLED_AD

object AdPreferences {

    private const val PREFERENCES = "SHOW_AD_PREFERENCES"
    private const val PREFERENCES_KEY_PREFIX = "SHOW_AD_"

    fun getAllAdTypes() = listOf(
        HOLDER_TYPE_HTML_BANNER_ADS,
//        HOLDER_TYPE_VIDEO_BANNER_AD, // - will be implement in the next versions
//        HOLDER_TYPE_MULTIFORMAT_ADS, // - will be implement in the next versions
        HOLDER_TYPE_INTERSTITIAL_ADS,
        HOLDER_TYPE_REWARDED_AD,
        HOLDER_TYPE_UNFILLED_AD,
//        HOLDER_TYPE_IN_STREAM_AD, // - will be implement in the next versions
//        HOLDER_TYPE_IN_NATIVE_STYLES_AD, // - will be implement in the next versions
//        HOLDER_TYPE_IN_APP_AD, //- will be implement in the next versions
//        HOLDER_TYPE_RENDER_HTML_AD, // - will be implement in the next versions
//        HOLDER_TYPE_RENDER_VIDEO_AD, // - will be implement in the next versions
//        HOLDER_TYPE_RENDER_INTERSTITIAL_AD, // - will be implement in the next versions
//        HOLDER_TYPE_RENDER_REWARDED_AD, // - will be implement in the next versions
//        HOLDER_TYPE_RENDER_NATIVE_AD, // - will be implement in the next versions
    )

    fun getEnabledAdTypes(context: Context) =
        getAllAdTypes()
            .filter {
                isAdEnabled(context, it)
            }

    fun setAllAdTypesEnabled(context: Context, enabled: Boolean) {
        getAllAdTypes().forEach {
            setAdEnabled(context, it, enabled)
        }
    }

    private fun getPreferencesKey(viewType: Int) = PREFERENCES_KEY_PREFIX + viewType

    fun setAdEnabled(context: Context, viewType: Int, enabled: Boolean) {
        getPreferences(context).edit(commit = true) {
            putBoolean(getPreferencesKey(viewType), enabled)
        }
        Toast.makeText(
            context,
            R.string.relaunch_app_to_apply_changes,
            Toast.LENGTH_SHORT,
        ).show()
    }

    fun isAdEnabled(context: Context, viewType: Int) =
        getPreferences(context).getBoolean(getPreferencesKey(viewType), true)

    private fun getPreferences(context: Context) =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
}

