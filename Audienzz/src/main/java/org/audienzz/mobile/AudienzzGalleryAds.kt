package org.audienzz.mobile

import android.content.Context
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.LoadAdError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.audienzz.mobile.api.config.RemoteAdUnitConfig
import org.audienzz.mobile.di.MainComponent
import org.audienzz.mobile.util.AudienzzDiagnostics
import kotlin.math.abs

/** One ad placement in a paged screen, for [AudienzzGalleryAds]. */
data class AudienzzGalleryAdSlot(
    /** Stable identity of the slot within its gallery; unique among the gallery's slots. */
    val slotKey: String,
    /** Remote ad config id, as for [AudienzzRemoteBannerView]. */
    val adConfigId: String,
    /** Index of the page (ePaper tile) that shows this slot. */
    val pageIndex: Int,
)

/**
 * Preloads the ads of a horizontally paged screen (an ePaper: one page per printed page) a few
 * pages before the reader reaches them.
 *
 * A pager builds or binds a page only when it is about to show it, so an ad on a later page is
 * requested too late if it is requested when its page is built, and requesting every ad page when
 * the screen opens buys ads for pages most readers never reach. This object owns the slots' ads
 * instead of the pages: each slot requests its ad once the reader is within its preload distance,
 * keeps it while its page is bound, recycled and bound again, and [bind] puts it into the page.
 *
 * - **Ad config:** each slot is a remote banner ad config (ad unit, sizes, Prebid, refresh,
 *   `gamConfig.includeFluid`). The slot loads as soon as it is in range; the ad config's
 *   `lazyLoad` and `prefetchDistanceDp` do not apply.
 * - **Preload distance:** the ad config's `config.preloadDistancePages`, else
 *   [DEFAULT_PRELOAD_DISTANCE_PAGES]: a slot requests its ad when the reader is that many pages
 *   away or closer, in either direction, counted from the fractional position while swiping.
 * - **Refresh:** `config.refreshTimeSeconds`, as for a remote banner, counted only while the ad
 *   is on screen.
 * - **Pages:** pass the [screen] you report to [AudienzzPrebidMobile.pageImpression] for the
 *   gallery (its Activity, Fragment or route key). Leaving that screen and coming back (an article
 *   opened on top, the app sent to the background) does not reload the ads: they are paused and
 *   kept, and refresh resumes where it stopped.
 *
 * ```kotlin
 * val galleryAds = AudienzzGalleryAds(
 *     context,
 *     listOf(
 *         AudienzzGalleryAdSlot("epaper-1", adConfigId = "123", pageIndex = 4),
 *         AudienzzGalleryAdSlot("epaper-2", adConfigId = "124", pageIndex = 12),
 *     ),
 *     screen = this, // the Fragment/Activity reported to pageImpression
 * )
 * viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
 *     override fun onPageScrolled(position: Int, offset: Float, offsetPx: Int) {
 *         galleryAds.updatePosition(position + offset)
 *     }
 * })
 * // In the adapter, for an ad page: galleryAds.bind("epaper-1", holder.adContainer)
 * // In onDestroyView / onDestroy: galleryAds.destroy()
 * ```
 */
class AudienzzGalleryAds internal constructor(
    private val context: Context,
    slots: List<AudienzzGalleryAdSlot>,
    private val screen: Any,
    private val configLookup: suspend (String) -> RemoteAdUnitConfig?,
    private val bannerFactory: (Context, String) -> AudienzzRemoteBannerView,
) {

    /** Ad events per slot. All callbacks run on the main thread. */
    interface Listener {
        /**
         * The slot's ad view exists: bind it if its page is already shown. Delivered on the next
         * main-loop turn, outside the scroll callback that triggered it, so an adapter update is
         * safe here.
         */
        fun onAdViewCreated(slotKey: String) {}

        /** A creative arrived (also after each refresh). */
        fun onAdLoaded(slotKey: String) {}

        /**
         * A request failed (no-fill, network), or the slot has no remote configuration
         * (`error == null`). The host can use it to drop the ad page.
         */
        fun onAdFailedToLoad(slotKey: String, error: LoadAdError?) {}
    }

    var listener: Listener? = null

    private val slots: List<AudienzzGalleryAdSlot> = slots.toList()
    private val banners = mutableMapOf<String, AudienzzRemoteBannerView>()

    /** Resolved preload distance per ad config id; absent until its config has been read. */
    private val distances = mutableMapOf<String, Int>()

    /** Ad config ids without a remote configuration. */
    private val missingConfigs = mutableSetOf<String>()
    private var position: Float? = null
    private var destroyed = false

    // Dispatched, not immediate: configs resolve after the constructing call returns, so a
    // listener set right after construction still hears about a missing config.
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    init {
        val duplicate = this.slots.groupBy { it.slotKey }.entries.firstOrNull { it.value.size > 1 }
        require(duplicate == null) { "AudienzzGalleryAds: duplicate slotKey \"${duplicate?.key}\"" }
        scope.launch { resolveConfigs() }
    }

    constructor(context: Context, slots: List<AudienzzGalleryAdSlot>, screen: Any) : this(
        context,
        slots,
        screen,
        configLookup = { id ->
            withContext(Dispatchers.IO) { MainComponent.remoteConfigManager?.getAdUnitConfig(id) }
        },
        bannerFactory = { ctx, id -> AudienzzRemoteBannerView(ctx, id) },
    )

    /**
     * The reader's position in pages, fractional while swiping (ViewPager2:
     * `position + positionOffset` from `onPageScrolled`). Slots within their preload distance
     * request their ad.
     */
    fun updatePosition(page: Float) {
        position = page
        evaluate()
    }

    /** The slot's ad view, or null while the slot has no ad (not yet in range, no config). */
    fun adView(slotKey: String): View? = banners[slotKey]

    /**
     * Puts the slot's ad into [container], the view of its page, taking it out of the page it was
     * in before. Returns false while the slot has no ad yet; [Listener.onAdViewCreated] says when
     * to bind again. A [FrameLayout] container centers the ad vertically; it spans the width.
     */
    fun bind(slotKey: String, container: ViewGroup): Boolean {
        val view = banners[slotKey] ?: return false
        if (view.parent === container) return true
        (view.parent as? ViewGroup)?.removeView(view)
        val params = if (container is FrameLayout) {
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            )
        } else {
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
        container.addView(view, params)
        return true
    }

    /** Releases every slot's ad. Call when the gallery's screen is gone for good. */
    fun destroy() {
        if (destroyed) return
        destroyed = true
        scope.cancel()
        banners.values.forEach { banner ->
            (banner.parent as? ViewGroup)?.removeView(banner)
            banner.destroy()
        }
        banners.clear()
    }

    private suspend fun resolveConfigs() {
        for (adConfigId in slots.map { it.adConfigId }.distinct()) {
            val config = try {
                configLookup(adConfigId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to read remote config $adConfigId", e)
                null
            }
            if (destroyed) return
            if (config == null) {
                Log.e(TAG, "Remote config not found for id=$adConfigId")
                missingConfigs += adConfigId
                slots.filter { it.adConfigId == adConfigId }
                    .forEach { listener?.onAdFailedToLoad(it.slotKey, null) }
            } else {
                distances[adConfigId] =
                    config.config.preloadDistancePages ?: DEFAULT_PRELOAD_DISTANCE_PAGES
            }
        }
        evaluate()
    }

    private fun evaluate() {
        val page = position ?: return
        if (destroyed) return
        slots.filter { it.slotKey !in banners && it.adConfigId !in missingConfigs }
            .forEach { slot ->
                // Unknown until its config has been read; re-evaluated then.
                val distance = distances[slot.adConfigId]
                // A hair of tolerance: a settled pager can report 3.9999998 for 4.
                if (distance != null && abs(slot.pageIndex - page) <= distance + POSITION_TOLERANCE) {
                    create(slot, page, distance)
                }
            }
    }

    private fun create(slot: AudienzzGalleryAdSlot, page: Float, distance: Int) {
        AudienzzDiagnostics.log(
            "gallery",
            "preload",
            "slot" to slot.slotKey,
            "config" to slot.adConfigId,
            "pageIndex" to slot.pageIndex,
            "readerPage" to "%.2f".format(page),
            "distance" to distance,
        )
        val banner = bannerFactory(context, slot.adConfigId)
        banner.galleryMode = true
        banner.setScreen(screen)
        banner.setAdListener(object : AdListener() {
            override fun onAdLoaded() {
                if (!destroyed) listener?.onAdLoaded(slot.slotKey)
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                if (!destroyed) listener?.onAdFailedToLoad(slot.slotKey, error)
            }
        })
        banners[slot.slotKey] = banner
        banner.loadAd()
        // Posted, not called inline: this runs inside the host's scroll callback (ViewPager2's
        // onPageScrolled), where a RecyclerView refuses adapter changes such as notifyItemChanged.
        scope.launch { if (!destroyed) listener?.onAdViewCreated(slot.slotKey) }
    }

    companion object {
        private const val TAG = "AudienzzGalleryAds"
        private const val POSITION_TOLERANCE = 1e-4f

        /** Preload distance when the ad config has no `config.preloadDistancePages`. */
        const val DEFAULT_PRELOAD_DISTANCE_PAGES = 2
    }
}
