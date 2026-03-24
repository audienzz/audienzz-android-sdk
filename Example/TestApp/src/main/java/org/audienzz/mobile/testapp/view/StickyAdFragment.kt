package org.audienzz.mobile.testapp.view

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.admanager.AdManagerAdView
import org.audienzz.mobile.AudienzzBannerAdUnit
import org.audienzz.mobile.AudienzzBannerParameters
import org.audienzz.mobile.AudienzzPrebidMobile
import org.audienzz.mobile.AudienzzSignals
import org.audienzz.mobile.AudienzzStickyAdWrapperView
import org.audienzz.mobile.api.data.AudienzzInitializationStatus
import org.audienzz.mobile.original.AudienzzAdViewHandler
import org.audienzz.mobile.testapp.R

class StickyAdFragment : Fragment() {

    private val stickyWrappers = mutableListOf<AudienzzStickyAdWrapperView>()
    private val adUnits = mutableListOf<AudienzzBannerAdUnit>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_sticky_ad, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val scrollView = view.findViewById<NestedScrollView>(R.id.stickyNestedScrollView)
        val adContainers = listOf(
            view.findViewById<FrameLayout>(R.id.stickyAdContainer1),
            view.findViewById<FrameLayout>(R.id.stickyAdContainer2),
            view.findViewById<FrameLayout>(R.id.stickyAdContainer3),
            view.findViewById<FrameLayout>(R.id.stickyAdContainer4),
            view.findViewById<FrameLayout>(R.id.stickyAdContainer5),
        )

        if (AudienzzPrebidMobile.isSdkInitialized) {
            setupStickyBannerAds(scrollView, adContainers)
        } else {
            AudienzzPrebidMobile.initializeRemoteSdk(
                requireContext().applicationContext,
                PUBLISHER_ID,
                true,
            ) { status ->
                if (status == AudienzzInitializationStatus.SUCCEEDED) {
                    setupStickyBannerAds(scrollView, adContainers)
                }
            }
        }
    }

    private fun setupStickyBannerAds(
        scrollView: NestedScrollView,
        adContainers: List<FrameLayout>,
    ) {
        adContainers.forEach { container ->
            setupStickyBannerAd(scrollView, container)
        }
    }

    private fun setupStickyBannerAd(scrollView: NestedScrollView, adContainer: FrameLayout) {
        AudienzzPrebidMobile.getAdUnitConfig(BANNER_CONFIG_ID) { config ->
            config ?: return@getAdUnitConfig

            val context = adContainer.context
            val primarySize = config.prebidConfig.adSizes.firstOrNull() ?: return@getAdUnitConfig

            val bannerAdUnit = AudienzzBannerAdUnit(
                config.prebidConfig.placementId,
                primarySize.width,
                primarySize.height,
            ).also { adUnits += it }

            val adView = AdManagerAdView(context).apply {
                adUnitId = config.gamConfig.adUnitPath
                setAdSizes(AdSize(primarySize.width, primarySize.height))
            }

            val wrapper = AudienzzStickyAdWrapperView(
                context = context,
                maxHeightDp = MAX_HEIGHT_DP,
            ).apply {
                isVisibilityGateEnabled = false
                setAdView(adView)
            }

            adContainer.addView(
                wrapper,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
            wrapper.attachToScrollView(scrollView)
            stickyWrappers += wrapper

            val parameters = AudienzzBannerParameters().apply {
                api = listOf(AudienzzSignals.Api.MRAID_3, AudienzzSignals.Api.OMID_1)
            }
            bannerAdUnit.bannerParameters = parameters
            bannerAdUnit.setAutoRefreshInterval(DEFAULT_REFRESH_SECONDS)

            AudienzzAdViewHandler(adView = adView, adUnit = bannerAdUnit)
                .load { request, _ -> adView.loadAd(request) }
        }
    }

    override fun onDestroyView() {
        stickyWrappers.forEach { it.detachFromScrollView() }
        stickyWrappers.clear()
        adUnits.forEach { it.stopAutoRefresh() }
        adUnits.clear()
        super.onDestroyView()
    }

    companion object {
        private const val PUBLISHER_ID = "35"
        private const val BANNER_CONFIG_ID = "46"
        private const val MAX_HEIGHT_DP = 450
        private const val DEFAULT_REFRESH_SECONDS = 60
    }
}
