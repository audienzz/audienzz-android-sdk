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
import org.audienzz.mobile.testapp.constants.SizeConstants

class StickyAdFragment : Fragment() {

    private var stickyWrapper: AudienzzStickyAdWrapperView? = null
    private var adUnit: AudienzzBannerAdUnit? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_sticky_ad, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val scrollView = view.findViewById<NestedScrollView>(R.id.stickyNestedScrollView)
        val adContainer = view.findViewById<FrameLayout>(R.id.stickyAdContainer)

        if (AudienzzPrebidMobile.isSdkInitialized) {
            setupStickyBannerAd(scrollView, adContainer)
        } else {
            AudienzzPrebidMobile.initializeRemoteSdk(
                requireContext().applicationContext,
                PUBLISHER_ID,
                true,
            ) { status ->
                if (status == AudienzzInitializationStatus.SUCCEEDED) {
                    setupStickyBannerAd(scrollView, adContainer)
                }
            }
        }
    }

    private fun setupStickyBannerAd(
        scrollView: NestedScrollView,
        adContainer: FrameLayout,
    ) {
        val context = adContainer.context

        val bannerAdUnit = AudienzzBannerAdUnit(
            CONFIG_ID,
            SizeConstants.SMALL_BANNER_WIDTH,
            SizeConstants.SMALL_BANNER_HEIGHT,
        )
        adUnit = bannerAdUnit

        val adView = AdManagerAdView(context).apply {
            adUnitId = AD_UNIT_ID
            setAdSizes(AdSize(SizeConstants.SMALL_BANNER_WIDTH, SizeConstants.SMALL_BANNER_HEIGHT))
        }

        val wrapper = AudienzzStickyAdWrapperView(
            context = context,
            maxHeightDp = MAX_HEIGHT_DP,
        )
        wrapper.setAdView(adView)
        adContainer.addView(
            wrapper,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
        wrapper.attachToScrollView(scrollView)
        stickyWrapper = wrapper

        val parameters = AudienzzBannerParameters().apply {
            api = listOf(AudienzzSignals.Api.MRAID_3, AudienzzSignals.Api.OMID_1)
        }
        bannerAdUnit.bannerParameters = parameters
        bannerAdUnit.setAutoRefreshInterval(DEFAULT_REFRESH_SECONDS)

        AudienzzAdViewHandler(
            adView = adView,
            adUnit = bannerAdUnit,
        ).load { request, _ ->
            adView.loadAd(request)
        }
    }

    override fun onDestroyView() {
        stickyWrapper?.detachFromScrollView()
        adUnit?.stopAutoRefresh()
        stickyWrapper = null
        adUnit = null
        super.onDestroyView()
    }

    companion object {
        private const val PUBLISHER_ID = "81"
        private const val AD_UNIT_ID = "ca-app-pub-3940256099942544/2934735716"
        private const val CONFIG_ID = "33994718"
        private const val MAX_HEIGHT_DP = 450
        private const val DEFAULT_REFRESH_SECONDS = 60
    }
}
