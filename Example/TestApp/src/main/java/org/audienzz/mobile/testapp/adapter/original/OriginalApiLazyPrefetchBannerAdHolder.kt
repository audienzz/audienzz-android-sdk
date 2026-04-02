package org.audienzz.mobile.testapp.adapter.original

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

import android.view.ViewGroup
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.admanager.AdManagerAdView
import org.audienzz.mobile.AudienzzBannerAdUnit
import org.audienzz.mobile.AudienzzPrebidMobile
import org.audienzz.mobile.original.AudienzzAdViewHandler
import org.audienzz.mobile.testapp.R
import org.audienzz.mobile.testapp.adapter.BaseAdHolder
import org.audienzz.mobile.testapp.constants.SizeConstants

/**
 * Demonstrates the correct lazy-load + smart-refresh pattern for a banner ad placed inside a
 * [androidx.recyclerview.widget.RecyclerView].
 *
 * ## Why withLazyLoading = false here?
 *
 * [AudienzzAdViewHandler.load] accepts a `prefetchMarginDp` parameter that triggers the Prebid
 * request N dp before the view enters the viewport. However, this only works reliably in
 * fixed-layout containers such as [android.widget.ScrollView] or
 * [androidx.core.widget.NestedScrollView].
 *
 * In a RecyclerView, ViewHolders are created and bound **just before** the item scrolls into
 * view (typically the item immediately following the last visible one). By the time
 * [createAds] is called from [onBind], the view is already within a few dp of the screen edge,
 * so any prefetch margin fires immediately and has no practical effect.
 *
 * The correct RecyclerView strategy is to use `withLazyLoading = false`: load the ad
 * immediately when the ViewHolder is bound. RecyclerView's own item prefetch
 * (`LinearLayoutManager.setInitialPrefetchItemCount`) controls how many items ahead are
 * pre-bound, giving you the equivalent of a prefetch buffer.
 *
 * ## Smart Refresh
 *
 * [AudienzzAdViewHandler.enableSmartRefresh] is safe and effective in RecyclerView: it pauses
 * auto-refresh when the item scrolls off-screen and force-refreshes when it returns if the ad
 * has become stale (elapsed ≥ refresh interval).
 */
class OriginalApiLazyPrefetchBannerAdHolder(parent: ViewGroup) : BaseAdHolder(parent) {

    override val titleRes = R.string.original_api_lazy_prefetch_banner_title

    private var adUnit: AudienzzBannerAdUnit? = null

    override fun createAds() {
        AudienzzPrebidMobile.getAdUnitConfig(BANNER_CONFIG_ID) { config ->
            config ?: return@getAdUnitConfig

            val placementId = config.prebidConfig.placementId
            val gamPath = config.gamConfig.adUnitPath

            adUnit = AudienzzBannerAdUnit(
                placementId,
                SizeConstants.MEDIUM_BANNER_WIDTH,
                SizeConstants.MEDIUM_BANNER_HEIGHT,
            ).apply {
                setAutoRefreshInterval(AUTO_REFRESH_SECONDS)
            }

            val adView = AdManagerAdView(adContainer.context).apply {
                adUnitId = gamPath
                setAdSizes(AdSize(SizeConstants.MEDIUM_BANNER_WIDTH, SizeConstants.MEDIUM_BANNER_HEIGHT))
            }

            adContainer.addView(adView)
            addBottomMargin(adView)

            val handler = AudienzzAdViewHandler(
                adView = adView,
                adUnit = adUnit!!,
            )

            // RecyclerView: withLazyLoading = false — load immediately on bind.
            // prefetchMarginDp has no effect inside RecyclerView because the ViewHolder
            // is created close to the screen by design. RecyclerView's own prefetch
            // (setInitialPrefetchItemCount) is the correct mechanism to pre-bind items ahead.
            handler.load(
                withLazyLoading = false,
                callback = { request, resultCode ->
                    showFetchErrorDialog(adContainer.context, resultCode)
                    adView.loadAd(request)
                },
            )

            // Smart refresh works correctly in RecyclerView: pauses auto-refresh when the
            // item is scrolled off-screen, resumes (or force-refreshes if stale) on return.
            handler.enableSmartRefresh()
        }
    }

    override fun onAttach() {
        adUnit?.resumeAutoRefresh()
    }

    override fun onDetach() {
        adUnit?.stopAutoRefresh()
    }

    companion object {
        private const val BANNER_CONFIG_ID = "46"
        private const val AUTO_REFRESH_SECONDS = 60
    }
}
