package org.audienzz.mobile.event.network.mapper

import android.content.Context
import android.content.pm.PackageManager
import org.audienzz.BuildConfig
import org.audienzz.mobile.event.entity.EventDomain
import org.audienzz.mobile.event.entity.EventType
import org.audienzz.mobile.event.network.entity.EventNetwork
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject

internal class EventNetworkMapper @Inject constructor(
    private val context: Context,
) {
    private val dateFormatter =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

    // App metadata is constant for the process — resolve once.
    private val appPackageName: String = context.packageName
    private val appVersion: String? = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull()
    private val appTitle: String? = runCatching {
        context.applicationInfo.loadLabel(context.packageManager).toString()
    }.getOrNull()
    private val userAgent: String? = System.getProperty("http.agent")

    // Envelope device fields — known directly on mobile (more reliable than parsing the UA).
    private val osName: String = "Android"
    private val deviceCategory: String =
        if (context.resources.configuration.smallestScreenWidthDp >= 600) "Tablet" else "Smartphone"
    // No real browser in-app; the creative renders in the system WebView.
    private val browserName: String = "Android WebView"

    fun toNetwork(event: EventDomain): EventNetwork {
        val metrics = context.resources.displayMetrics
        val screenWidthDp = (metrics.widthPixels / metrics.density).toInt()
        val screenHeightDp = (metrics.heightPixels / metrics.density).toInt()
        val zoneOffsetSeconds = TimeZone.getDefault().getOffset(event.timestamp) / 1000

        return EventNetwork(
            eventType = event.eventType?.nameString.orEmpty(),
            companyId = event.companyId,
            source = "android-sdk",
            eventId = event.uuid.orEmpty(),
            pageImpressionId = event.pageImpressionId,
            sessionId = event.sessionId,
            sessionStartTimestamp = event.sessionStartTimestamp,
            sessionSeq = event.sessionSequence ?: 0,
            eventTimestamp = dateFormatter.format(Date(event.timestamp)),
            locale = Locale.getDefault().toLanguageTag(),
            zoneOffsetSeconds = zoneOffsetSeconds,
            screenHeight = screenHeightDp,
            screenWidth = screenWidthDp,
            viewportHeight = screenHeightDp,
            viewportWidth = screenWidthDp,
            deviceId = event.deviceId,
            userAgent = userAgent,
            osName = osName,
            deviceCategory = deviceCategory,
            browserName = browserName,
            sdkName = SDK_NAME,
            sdkVersion = BuildConfig.AUDIENZZ_SDK_VERSION,
            appPackageName = appPackageName,
            appVersion = appVersion,
            appTitle = appTitle,
            screenName = event.screenName,
            pageUrl = null,
            visitorId = event.visitorId,
            attributes = buildAttributes(event),
        )
    }

    private fun buildAttributes(event: EventDomain): Map<String, String> = buildMap {
        event.adUnitId?.let { put("ad_unit_id", it) }
        event.resultCode?.let { put("result_code", it) }
        event.sizes?.let { put("sizes", it) }
        event.adType?.let { put("ad_type", it.nameString) }
        event.adSubtype?.let { put("ad_subtype", it.nameString) }
        event.apiType?.let { put("api_type", it.nameString) }
        event.isAutorefresh?.let { put("autorefresh", it.toString()) }
        event.autorefreshTime?.let { put("autorefresh_time", it.toString()) }
        event.isRefresh?.let { put("refresh", it.toString()) }
        event.errorMessage?.let { put("error_message", it) }
        event.bidderCode?.let { put("bidder_code", it) }
        event.winnerBidderCode?.let { put("winner_bidder_code", it) }
        event.timeToRespond?.let { put("time_to_respond", it.toString()) }
        event.priceBucket?.let { put("price_bucket", it) }
        event.hbSize?.let { put("hb_size", it) }
        event.hbFormat?.let { put("hb_format", it) }
        // Round to 6 dp so the emitted value is clean (1.425, not 1.4249999999999998) and matches iOS.
        event.cpm?.let { put("cpm", (Math.round(it * 1_000_000.0) / 1_000_000.0).toString()) }
        event.currency?.let { put("currency", it) }
        event.creativeId?.let { put("creative_id", it) }
        event.auctionId?.let { put("auction_id", it) }
        event.adId?.let { put("ad_id", it) }
        // Web-clickstream parity attributes.
        event.adUnitCode?.let { put("ad_unit_code", it) }   // Prebid configId
        event.websiteId?.let { put("website_id", it) }       // remote-config publisherId
        event.winnerType?.let { put("winner_type", it) }
        event.mediaType?.let { put("media_type", it) }
        event.mediaTypes?.let { put("media_types", it) }
        event.size?.let { put("size", it) }
        event.slotReload?.let { put("slot_reload", it.toString()) }
        event.consentString?.let { put("consent_string", it) }
        // Transport is constant for the SDK (single POST per event, like the web XHR beacon).
        put("transport", "xhr")
        // Viewability events carry the tracker version.
        if (event.eventType == EventType.VIEWABILITY_START ||
            event.eventType == EventType.VIEWABILITY_SUCCESS
        ) {
            put("tracker_version", VIEWABILITY_TRACKER_VERSION)
        }
    }

    companion object {
        private const val SDK_NAME = "android"
        private const val VIEWABILITY_TRACKER_VERSION = "1.0.0"
    }
}
