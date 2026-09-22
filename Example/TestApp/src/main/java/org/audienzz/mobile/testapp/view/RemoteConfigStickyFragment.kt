package org.audienzz.mobile.testapp.view

/*
    Copyright 2025 Audienzz AG

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
*/

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.LoadAdError
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import org.audienzz.mobile.AudienzzPrebidMobile
import org.audienzz.mobile.AudienzzRemoteBannerView
import org.audienzz.mobile.AudienzzRemoteConfigInterstitial
import org.audienzz.mobile.AudienzzStickyAdWrapperView
import org.audienzz.mobile.util.AudienzzDiagnostics
import org.audienzz.mobile.testapp.App
import org.audienzz.mobile.testapp.R

/**
 * Demonstrates all remote-config-driven banner variants in a single scrollable page:
 *
 *  - **Banner** — a standard non-sticky [AudienzzRemoteBannerView] that loads from remote config.
 *  - **Sticky Banner** — an [AudienzzRemoteBannerView] wrapped inside
 *    [AudienzzStickyAdWrapperView]: stays pinned within its reserved area while scrolling past,
 *    then exits naturally at the bottom of the container.
 *  - **Adaptive Banner** — a full-width non-sticky [AudienzzRemoteBannerView] using the adaptive
 *    config (config id [ADAPTIVE_CONFIG_ID]).
 *  - **Interstitial** — loaded on demand via the button at the bottom of the page.
 *
 * All ad sizes, refresh intervals, and prefetch distances come from remote config — no hard-coded
 * values in the fragment.
 */
class RemoteConfigStickyFragment : Fragment() {

    private val remoteBannerViews = mutableListOf<AudienzzRemoteBannerView>()
    private val stickyWrappers = mutableListOf<AudienzzStickyAdWrapperView>()
    private var interstitial: AudienzzRemoteConfigInterstitial? = null
    private var interstitialStatus: TextView? = null
    private var scrollView: NestedScrollView? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_remote_config_sticky, container, false)

    override fun onResume() {
        super.onResume()
        if (AudienzzPrebidMobile.isSdkInitialized) {
            AudienzzPrebidMobile.pageImpression(this)
        } else {
            // Cold start: onResume runs before initialization finishes, so this screen would
            // otherwise never report its FIRST page impression — only later ones, after navigating.
            App.whenSdkReady { if (isResumed) AudienzzPrebidMobile.pageImpression(this) }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        scrollView = view.findViewById(R.id.remoteConfigScrollView)

        // Built immediately, deliberately — a publisher should not have to wait for initialization
        // before creating ads, and since the SDK defers an auction until Prebid is ready this is
        // exactly the case that used to leave the first (above-the-fold) banner empty for the whole
        // session. Leaving it un-gated here keeps that regression visible in the demo app.

        // Section 1 — plain banner (non-sticky)
        loadBannerInto(view.findViewById(R.id.bannerContainer1), BANNER_CONFIG_ID)

        // Section 2 — sticky banner
        loadStickyBannerInto(view.findViewById(R.id.stickyContainer1), BANNER_CONFIG_ID)

        // Section 3 — adaptive banner (non-sticky)
        loadBannerInto(view.findViewById(R.id.bannerContainer2), ADAPTIVE_CONFIG_ID)

        // Section 4 — sticky banner
        loadStickyBannerInto(view.findViewById(R.id.stickyContainer2), BANNER_CONFIG_ID)

        interstitialStatus = view.findViewById(R.id.interstitialStatus)
        setInterstitialStatus("not loaded")
        // One button per verb. `prefetch` must never present on its own and `show` must never
        // fetch — behaviour a single combined button cannot demonstrate, and which the status line
        // makes visible: prefetch alone should move it to "ready to show" and nothing more.
        view.findViewById<Button>(R.id.btnPrefetchInterstitial).setOnClickListener {
            setInterstitialStatus("loading…")
            ensureInterstitial().prefetch()
        }
        view.findViewById<Button>(R.id.btnShowInterstitial).setOnClickListener {
            val ad = ensureInterstitial()
            if (!ad.isReady) {
                // Reported rather than silently queued: `show` takes an opportunity or skips it.
                setInterstitialStatus("not ready — nothing to show (prefetch first)")
                return@setOnClickListener
            }
            if (!ad.show(requireActivity())) setInterstitialStatus("opportunity skipped")
        }
        view.findViewById<Button>(R.id.btnPrefetchAndShowInterstitial).setOnClickListener {
            setInterstitialStatus("loading… (will show when ready)")
            ensureInterstitial().prefetchAndShow()
        }

        // One under every ad slot. They all open the same screen; which one you tapped is recorded
        // so a captured log says WHICH slot's leave-and-return you were exercising — with four
        // identical buttons the log would otherwise be ambiguous.
        listOf(
            R.id.btnOpenAdScreen1 to "banner1",
            R.id.btnOpenAdScreen2 to "sticky1",
            R.id.btnOpenAdScreen3 to "banner2",
            R.id.btnOpenAdScreen4 to "sticky2",
        ).forEach { (buttonId, slot) ->
            view.findViewById<Button>(buttonId).setOnClickListener {
                AudienzzDiagnostics.log("app", "openAdScreen", "from" to slot)
                startActivity(
                    android.content.Intent(requireContext(), RemoteConfigAdActivity::class.java),
                )
            }
        }
    }

    // ── Ad loading helpers ─────────────────────────────────────────────────────────────────────

    private fun loadBannerInto(container: FrameLayout, configId: String) {
        val banner = AudienzzRemoteBannerView(requireContext(), configId)
        remoteBannerViews += banner
        container.removeAllViews()
        container.addView(
            banner,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
        banner.loadAd()
    }

    private fun loadStickyBannerInto(container: FrameLayout, configId: String) {
        val banner = AudienzzRemoteBannerView(requireContext(), configId)
        remoteBannerViews += banner

        val sticky = AudienzzStickyAdWrapperView(
            context = requireContext(),
            maxHeightDp = STICKY_MAX_HEIGHT_DP,
        ).apply {
            isVisibilityGateEnabled = false
            setAdView(banner)
        }
        stickyWrappers += sticky

        container.removeAllViews()
        container.addView(
            sticky,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ),
        )

        scrollView?.let { sticky.attachToScrollView(it) }
        banner.loadAd()
    }

    /**
     * One tap, one interstitial: [AudienzzRemoteConfigInterstitial.prefetchAndShow] presents as
     * soon as the load completes, or presents inventory already in hand.
     *
     * This button used to do two different things depending on state — the first tap prefetched,
     * the second showed — which read as "nothing happened" the first time, and needed a third tap
     * if you were quick enough to beat the load.
     *
     * The owner is kept across taps rather than rebuilt: repeated taps coalesce onto the request
     * in flight and reuse ready inventory, and cannot present twice. Rebuilding it each time would
     * throw away a load already paid for.
     *
     * The two-step flow (`prefetch()` now, `show(activity, eligible)` at a moment you choose) is
     * the one to use in a real app, where you decide when an interstitial is appropriate. It is
     * exercised in the managed test screens.
     */
    private fun setInterstitialStatus(text: String) {
        interstitialStatus?.text = text
        AudienzzDiagnostics.log("app", "interstitialStatus", "state" to text)
    }

    /**
     * Built once and kept, so `prefetch` then `show` act on the same ad — rebuilding between the
     * two would throw away the inventory the prefetch just paid for.
     */
    private fun ensureInterstitial(): AudienzzRemoteConfigInterstitial {
        interstitial?.let { return it }
        val ad = AudienzzRemoteConfigInterstitial(
            requireContext(),
            INTERSTITIAL_CONFIG_ID,
            object : AudienzzRemoteConfigInterstitial.Events {
                override fun onLoaded() = setInterstitialStatus("ready to show")
                override fun onFailed(loadError: LoadAdError) =
                    setInterstitialStatus("load failed: ${loadError.message}")
                override fun onOpened() = setInterstitialStatus("showing")
                override fun onClosed() = setInterstitialStatus("closed — not loaded")
                override fun onClicked() = setInterstitialStatus("clicked")
                override fun onFailedToShow(adError: AdError) =
                    setInterstitialStatus("failed to show: ${adError.message}")
                override fun onError(reason: String) = setInterstitialStatus("error: $reason")
            },
        )
        interstitial = ad
        return ad
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────────────────────

    override fun onDestroyView() {
        stickyWrappers.forEach { it.detachFromScrollView() }
        stickyWrappers.clear()
        remoteBannerViews.forEach { it.destroy() }
        remoteBannerViews.clear()
        interstitial?.destroy()
        interstitial = null
        scrollView = null
        super.onDestroyView()
    }

    // ── Constants ─────────────────────────────────────────────────────────────────────────────

    private companion object {
        const val BANNER_CONFIG_ID = "46"
        const val ADAPTIVE_CONFIG_ID = "48"
        const val INTERSTITIAL_CONFIG_ID = "47"
        const val STICKY_MAX_HEIGHT_DP = 300
    }
}
