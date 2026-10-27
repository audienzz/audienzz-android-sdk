package org.audienzz.mobile.original

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.admanager.AdManagerAdView
import org.audienzz.mobile.util.AudienzzDiagnostics

/**
 * Sizes a banner to the Prebid creative it is showing, when that differs from the GAM creative it
 * was served in.
 *
 * The Prebid line item's GAM creative has its own size, and GAM sizes the view to it: a 300x250 bid
 * served through a 300x600 creative was left smaller and off-centre in a 300x600 slot, and a 300x600
 * bid served through a 300x250 creative was cut off. The view is resized to the winning `hb_size`
 * only once both hold for the CURRENT load: Google finished loading it, and the Prebid creative
 * rendered. Acting on the app event alone would resize the previous creative, and acting without it
 * would resize a creative GAM served itself.
 *
 * "Prebid rendered" is GAM's `Prebid` app event, or — because some bidders' creatives never
 * dispatch it (nexx360's do not) — `hb_size` found in the creative's own markup, which a creative
 * GAM served itself does not contain. The markup is read here rather than through Prebid's
 * `AdViewUtils.findPrebidCreativeSize`, which also rescales the WebView and reads the page once:
 * at `onAdLoaded` it is often not rendered yet.
 */
internal class PrebidCreativeSizer(private val adView: AdManagerAdView) {

    /** Off leaves every size to GAM. On unless the ad config or the publisher turns it off. */
    var enabled: Boolean = true

    /**
     * Answers the Prebid size in [View]'s creative markup, or null when it has none (yet). A seam
     * for tests: the real one needs a rendered page.
     */
    internal var findSizeInMarkup: (View, (AdSize?) -> Unit) -> Unit = ::readHbSizeFromMarkup

    /** How long after `onAdLoaded` the markup is looked for. */
    internal var probeTimeoutMs = PROBE_TIMEOUT_MS

    /** Pause between markup reads: the creative renders after `onAdLoaded`. */
    internal var probeRetryMs = PROBE_RETRY_MS

    private val handler = Handler(Looper.getMainLooper())

    /** Bumped for every Google request, so a late markup read cannot act on a newer load. */
    private var generation = 0
    private var prebidSize: AdSize? = null
    private var prebidRendered = false
    private var googleLoaded = false
    private var probing = false

    /** The sizes the view requested before it was resized; the next request asks for them again. */
    private var sizesBeforeResize: Array<AdSize>? = null

    /** Whether the current load was resized. Read-only, for tests. */
    internal val resized: Boolean get() = sizesBeforeResize != null

    /** A new Google request is starting: forget the previous delivery. */
    fun beginLoad(hbSize: String?) {
        generation++
        probing = false
        prebidRendered = false
        googleLoaded = false
        // A previous resize narrowed the view to one size. Request every configured size again.
        sizesBeforeResize?.let { adView.setAdSizes(*it) }
        sizesBeforeResize = null
        prebidSize = if (enabled) parseSize(hbSize) else null
    }

    /** Google finished loading the current request. */
    fun onGoogleLoaded() {
        googleLoaded = true
        if (apply(via = "appEvent") || prebidRendered) return
        probeMarkup()
    }

    /** GAM's `Prebid` app event: the Prebid line item's creative rendered. */
    fun onPrebidRendered() {
        prebidRendered = true
        probing = false
        apply(via = "appEvent")
    }

    /** The banner was released or destroyed: nothing in flight may act any more. */
    fun cancel() {
        generation++
        probing = false
    }

    private fun probeMarkup() {
        if (!enabled || prebidSize == null) return
        val load = generation
        val deadline = SystemClock.uptimeMillis() + probeTimeoutMs
        probing = true
        lateinit var attempt: () -> Unit
        attempt = attempt@{
            if (load != generation || !probing) return@attempt
            findSizeInMarkup(adView) { size ->
                if (load != generation || !probing) return@findSizeInMarkup
                if (size != null) {
                    probing = false
                    prebidRendered = true
                    prebidSize = size
                    apply(via = "markup")
                } else if (SystemClock.uptimeMillis() + probeRetryMs <= deadline) {
                    handler.postDelayed({ attempt() }, probeRetryMs)
                } else {
                    probing = false
                    AudienzzDiagnostics.log("slot", "prebidSizeMissing", "adUnit" to adView.adUnitId)
                }
            }
        }
        attempt()
    }

    /**
     * Returns true when the view was sized to the Prebid creative. The single gate for every order
     * the signals arrive in. Both directions: smaller fixes the off-centre gap, larger the cut-off
     * creative.
     */
    private fun apply(via: String): Boolean {
        if (!enabled || sizesBeforeResize != null) return false
        if (!prebidRendered || !googleLoaded) return false
        val target = prebidSize ?: return false
        val current = adView.adSize ?: return false
        if (current.width <= 0 || current.height <= 0) return false
        if (target.width == current.width && target.height == current.height) return false
        sizesBeforeResize = adView.adSizes?.copyOf() ?: arrayOf(current)
        adView.setAdSizes(target)
        AudienzzDiagnostics.log(
            "slot",
            "prebidResize",
            "adUnit" to adView.adUnitId,
            "from" to "${current.width}x${current.height}",
            "to" to "${target.width}x${target.height}",
            "via" to via,
        )
        return true
    }

    internal companion object {
        private const val PROBE_TIMEOUT_MS = 1_000L
        private const val PROBE_RETRY_MS = 200L
        private val HB_SIZE_IN_MARKUP = Regex("""hb_size\W+(\d+)x(\d+)""")

        fun parseSize(value: String?): AdSize? {
            val parts = value?.lowercase()?.split('x') ?: return null
            if (parts.size != 2) return null
            val width = parts[0].trim().toIntOrNull() ?: return null
            val height = parts[1].trim().toIntOrNull() ?: return null
            return if (width > 0 && height > 0) AdSize(width, height) else null
        }

        /** The `hb_size` in a creative's markup, or null. The markup may be JSON-escaped. */
        fun sizeInMarkup(markup: String?): AdSize? {
            val match = markup?.let { HB_SIZE_IN_MARKUP.find(it) } ?: return null
            val width = match.groupValues[1].toIntOrNull() ?: return null
            val height = match.groupValues[2].toIntOrNull() ?: return null
            return if (width > 0 && height > 0) AdSize(width, height) else null
        }

        private fun readHbSizeFromMarkup(view: View, answer: (AdSize?) -> Unit) {
            val webView = findWebView(view) ?: return answer(null)
            webView.evaluateJavascript("document.body.innerHTML") { markup ->
                answer(sizeInMarkup(markup))
            }
        }

        private fun findWebView(view: View): WebView? {
            if (view is WebView) return view
            if (view !is ViewGroup) return null
            for (i in 0 until view.childCount) {
                findWebView(view.getChildAt(i))?.let { return it }
            }
            return null
        }
    }
}
