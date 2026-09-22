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
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        scrollView = view.findViewById(R.id.remoteConfigScrollView)

        // Create the ads only once initialization has finished.
        //
        // A slot that is already on screen fires its lazy trigger immediately, so building it
        // before Prebid is ready raced initialization and lost: Prebid answered "SDK wasn't
        // initialized. Context is null.", the auction died, and nothing re-armed the trigger — so
        // the FIRST banner stayed empty for the whole session while the ones further down the page
        // (which only fire when you scroll to them) loaded normally.
        App.whenSdkReady {
            // The view can be gone by the time init lands.
            val root = this.view ?: return@whenSdkReady

            // Section 1 — plain banner (non-sticky)
            loadBannerInto(root.findViewById(R.id.bannerContainer1), BANNER_CONFIG_ID)

            // Section 2 — sticky banner
            loadStickyBannerInto(root.findViewById(R.id.stickyContainer1), BANNER_CONFIG_ID)

            // Section 3 — adaptive banner (non-sticky)
            loadBannerInto(root.findViewById(R.id.bannerContainer2), ADAPTIVE_CONFIG_ID)

            // Section 4 — sticky banner
            loadStickyBannerInto(root.findViewById(R.id.stickyContainer2), BANNER_CONFIG_ID)

            // The first page impression has to follow the ads, not precede them.
            AudienzzPrebidMobile.pageImpression(this)
        }

        view.findViewById<Button>(R.id.btnLoadInterstitial).text = "Show interstitial"
        view.findViewById<Button>(R.id.btnLoadInterstitial).setOnClickListener {
            showInterstitial()
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
    private fun showInterstitial() {
        if (interstitial == null) {
            interstitial = AudienzzRemoteConfigInterstitial(requireContext(), INTERSTITIAL_CONFIG_ID)
        }
        interstitial?.prefetchAndShow()
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
