package org.audienzz.mobile

import org.audienzz.mobile.api.config.RemotePrebidConfig
import org.audienzz.mobile.api.data.AudienzzAdUnitFormat
import org.audienzz.mobile.event.entity.AdSubtype
import org.json.JSONObject
import org.prebid.mobile.BannerParameters
import org.prebid.mobile.InterstitialAdUnit
import org.prebid.mobile.Signals
import org.prebid.mobile.VideoParameters
import org.prebid.mobile.api.data.AdUnitFormat
import java.util.EnumSet

/**
 * What an interstitial's bid request advertises: the media formats it asks for, and the OpenRTB
 * API frameworks its renderer supports.
 *
 * Backend-controlled only — the ad config's `prebidConfig.format` and `prebidConfig.apis` — and
 * the same on every platform. There is no publisher override: an interstitial constructor,
 * `bannerParameters.api`, `videoParameters.api` and `impOrtbConfig` all lose to this. A hand-built
 * interstitial has no ad config and always gets [DEFAULT].
 *
 * Validation (mirrored exactly by iOS and pinned by the same table in both test suites):
 * - `format` must be exactly `banner`, `video` or `bannerAndVideo`; anything else, including a
 *   missing or non-string value, is `bannerAndVideo`.
 * - `apis` keeps, in backend order and without duplicates, only the frameworks the renderer
 *   supports ([3, 5, 6, 7]: MRAID 1, MRAID 2, MRAID 3, OMID 1). If nothing usable is left — the key
 *   is missing, not an array, empty, or holds only unsupported or non-integer values — it is the
 *   full supported list. An interstitial never goes out without an `api`.
 *
 * None of this can fail a load: every input resolves to something sendable.
 */
internal data class InterstitialCapabilities(val format: Format, val apis: List<Int>) {

    internal enum class Format(val wire: String) {
        BANNER("banner"),
        VIDEO("video"),
        BANNER_AND_VIDEO("bannerAndVideo"),
        ;

        val includesBanner get() = this != VIDEO
        val includesVideo get() = this != BANNER
    }

    val audienzzFormats: EnumSet<AudienzzAdUnitFormat>
        get() = EnumSet.noneOf(AudienzzAdUnitFormat::class.java).apply {
            if (format.includesBanner) add(AudienzzAdUnitFormat.BANNER)
            if (format.includesVideo) add(AudienzzAdUnitFormat.VIDEO)
        }

    private val prebidFormats: EnumSet<AdUnitFormat>
        get() = EnumSet.noneOf(AdUnitFormat::class.java).apply {
            if (format.includesBanner) add(AdUnitFormat.BANNER)
            if (format.includesVideo) add(AdUnitFormat.VIDEO)
        }

    val prebidApis: List<Signals.Api>
        get() = apis.mapNotNull {
            when (it) {
                3 -> Signals.Api.MRAID_1
                5 -> Signals.Api.MRAID_2
                6 -> Signals.Api.MRAID_3
                7 -> Signals.Api.OMID_1
                else -> null
            }
        }

    /** The analytics subtype for this format, as the other ad types report it. */
    val adSubtype: AdSubtype
        get() = when (format) {
            Format.BANNER -> AdSubtype.HTML
            Format.VIDEO -> AdSubtype.VIDEO
            Format.BANNER_AND_VIDEO -> AdSubtype.MULTIFORMAT
        }

    /**
     * Writes these capabilities onto the ad unit, just before its request.
     *
     * Everything else already on the unit is kept: its sizes and minimum size percentages, and a
     * publisher's other video settings (duration, bitrate, protocols, …). Only the formats and the
     * API lists are replaced. A video request without video parameters gets the SDK's interstitial
     * defaults, so it is always playable.
     */
    fun apply(adUnit: InterstitialAdUnit) {
        adUnit.configuration.setAdUnitFormats(prebidFormats)
        adUnit.bannerParameters = (adUnit.bannerParameters ?: BannerParameters()).apply { api = prebidApis }
        if (format.includesVideo) {
            val video = adUnit.videoParameters?.takeIf { !it.mimes.isNullOrEmpty() } ?: defaultVideoParameters()
            video.api = prebidApis
            adUnit.videoParameters = video
        }
        adUnit.impOrtbConfig = sanitizedImpOrtb(adUnit.impOrtbConfig, format)
    }

    companion object {
        /**
         * MRAID 1, MRAID 2, MRAID 3 and OMID 1: what Google's renderer supports for the creatives an
         * interstitial can receive. VPAID (1, 2) and ORMMA (4) are not, and are never advertised.
         */
        val SUPPORTED_APIS = listOf(3, 5, 6, 7)

        val DEFAULT = InterstitialCapabilities(Format.BANNER_AND_VIDEO, SUPPORTED_APIS)

        fun resolve(format: String?, apis: List<Int>?): InterstitialCapabilities {
            val resolvedFormat = Format.values().firstOrNull { it.wire == format } ?: DEFAULT.format
            val resolvedApis = apis.orEmpty().filter { it in SUPPORTED_APIS }.distinct()
            return InterstitialCapabilities(resolvedFormat, resolvedApis.ifEmpty { SUPPORTED_APIS })
        }

        fun resolve(prebidConfig: RemotePrebidConfig?) =
            resolve(prebidConfig?.format, prebidConfig?.apis)

        /**
         * The video parameters an interstitial sends when nobody supplied any: MP4 over VAST 2.0,
         * muted autoplay, interstitial placement.
         */
        fun defaultVideoParameters() = VideoParameters(listOf("video/mp4")).apply {
            protocols = listOf(Signals.Protocols.VAST_2_0)
            playbackMethod = listOf(Signals.PlaybackMethod.AutoPlaySoundOff)
            placement = Signals.Placement.Interstitial
        }

        /**
         * Removes from a publisher's imp-level ORTB what would override these capabilities.
         *
         * Prebid deep-merges the imp ORTB into every impression, so a `banner.api` or `video.api`
         * there replaced the backend list, and a `video` object added video to a banner-only
         * request. Those keys go; everything else is kept. Unparseable ORTB is dropped: Prebid
         * would reject it anyway.
         */
        fun sanitizedImpOrtb(ortb: String?, format: Format): String? {
            if (ortb.isNullOrEmpty()) return ortb
            val imp = try {
                JSONObject(ortb)
            } catch (_: Exception) {
                return null
            }
            for ((key, allowed) in listOf("banner" to format.includesBanner, "video" to format.includesVideo)) {
                if (!imp.has(key)) continue
                val obj = imp.optJSONObject(key)
                if (!allowed || obj == null) imp.remove(key) else obj.remove("api")
            }
            return imp.toString()
        }
    }
}

/**
 * Marks entry points that exist for the Flutter and React Native bridges only. A bridge whose
 * remote interstitials read the ad config itself uses them to hand over the backend values; they
 * are not publisher settings.
 */
@RequiresOptIn(
    level = RequiresOptIn.Level.ERROR,
    message = "Bridge-only. Interstitial formats and API frameworks are backend-controlled.",
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.FUNCTION)
annotation class AudienzzBridgeApi
