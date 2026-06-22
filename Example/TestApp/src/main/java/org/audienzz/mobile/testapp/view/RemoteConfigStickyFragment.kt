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
import org.audienzz.mobile.AudienzzRemoteBannerView
import org.audienzz.mobile.AudienzzRemoteConfigInterstitial
import org.audienzz.mobile.AudienzzStickyAdWrapperView
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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        scrollView = view.findViewById(R.id.remoteConfigScrollView)

        // Section 1 — plain banner (non-sticky)
        loadBannerInto(view.findViewById(R.id.bannerContainer1), BANNER_CONFIG_ID)

        // Section 2 — sticky banner
        loadStickyBannerInto(view.findViewById(R.id.stickyContainer1), BANNER_CONFIG_ID)

        // Section 3 — adaptive banner (non-sticky)
        loadBannerInto(view.findViewById(R.id.bannerContainer2), ADAPTIVE_CONFIG_ID)

        // Section 4 — sticky banner
        loadStickyBannerInto(view.findViewById(R.id.stickyContainer2), BANNER_CONFIG_ID)

        view.findViewById<Button>(R.id.btnLoadInterstitial).setOnClickListener {
            loadInterstitial()
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

    private fun loadInterstitial() {
        interstitial?.destroy()
        interstitial = AudienzzRemoteConfigInterstitial(requireContext(), INTERSTITIAL_CONFIG_ID)
        interstitial?.loadAd()
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
