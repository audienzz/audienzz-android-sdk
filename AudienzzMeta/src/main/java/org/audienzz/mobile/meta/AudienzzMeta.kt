package org.audienzz.mobile.meta

import com.facebook.ads.AdSettings
import com.google.ads.mediation.facebook.FacebookMediationAdapter

/**
 * Optional Meta Audience Network mediation.
 *
 * Adding `com.audienzz:sdk-meta` is the whole integration: Google Mobile Ads finds the Meta adapter
 * on the classpath and initializes it with the rest of the SDK, and the core SDK marks every GAM
 * request with `au_meta=1` so the Ad Manager yield group holding Meta can target it. The backend
 * publisher config (`mediation.meta.enabled = false`) turns that key off without an app release.
 *
 * Nothing here has to be called. These are the privacy settings Meta reads from its own SDK rather
 * than from Google's request configuration.
 */
object AudienzzMeta {

    /** Meta's option name for Limited Data Use (US state privacy laws). */
    private const val LIMITED_DATA_USE = "LDU"

    /** Google packs an adapter's last two version parts into micro: 6.21.0.2 → 2, 6.21.1.3 → 103. */
    private const val MICRO_SPLIT = 100

    /** Version of the bundled Google mediation adapter, e.g. `6.21.0.2`, for logs and support. */
    @JvmStatic
    val adapterVersion: String
        get() = FacebookMediationAdapter().versionInfo.let {
            val micro = it.microVersion
            "${it.majorVersion}.${it.minorVersion}.${micro / MICRO_SPLIT}.${micro % MICRO_SPLIT}"
        }

    /**
     * Turns on Meta's Limited Data Use for US state privacy laws.
     *
     * With the default `0, 0` Meta geolocates the user and applies LDU only where a law requires
     * it. Pass Meta's country and state codes to apply it explicitly. Call before ads load.
     */
    @JvmStatic
    @JvmOverloads
    fun enableLimitedDataUse(country: Int = 0, state: Int = 0) {
        AdSettings.setDataProcessingOptions(arrayOf(LIMITED_DATA_USE), country, state)
    }

    /** Turns Limited Data Use back off. */
    @JvmStatic
    fun disableLimitedDataUse() {
        AdSettings.setDataProcessingOptions(arrayOf())
    }
}
