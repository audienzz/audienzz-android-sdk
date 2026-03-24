package org.audienzz.mobile.testapp.view

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import org.audienzz.mobile.AudienzzRemoteBannerView
import org.audienzz.mobile.AudienzzRemoteConfigInterstitial
import org.audienzz.mobile.testapp.R
import org.audienzz.mobile.testapp.databinding.RemoteConfigFragmentBinding

class RemoteConfigFragment : Fragment() {

    private lateinit var bindings: RemoteConfigFragmentBinding

    private var fixedBannerView: AudienzzRemoteBannerView? = null
    private var adaptiveBannerView: AudienzzRemoteBannerView? = null
    private var interstitial: AudienzzRemoteConfigInterstitial? = null

    private companion object {
        const val FIXED_BANNER_CONFIG_ID = "46"
        const val ADAPTIVE_BANNER_CONFIG_ID = "46"
        const val INTERSTITIAL_CONFIG_ID = "47"
        const val FIXED_BANNER_WIDTH_DP = 320
        const val FIXED_BANNER_HEIGHT_DP = 480
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        bindings = DataBindingUtil.inflate(
            inflater,
            R.layout.remote_config_fragment,
            container,
            false,
        )
        return bindings.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        loadFixedBanner()
        loadAdaptiveBanner()

        bindings.btnLoadInterstitial.setOnClickListener {
            loadInterstitial()
        }
    }

    private fun loadFixedBanner() {
        fixedBannerView?.destroy()
        bindings.fixedBannerContainer.removeAllViews()

        val bannerView = AudienzzRemoteBannerView(
            requireContext(),
            FIXED_BANNER_CONFIG_ID
        )

        fixedBannerView = bannerView

        val widthPx = dpToPx(FIXED_BANNER_WIDTH_DP)
        val heightPx = dpToPx(FIXED_BANNER_HEIGHT_DP)

        val params = FrameLayout.LayoutParams(
            widthPx,
            heightPx,
            Gravity.CENTER
        )

        bindings.fixedBannerContainer.addView(bannerView, params)
        bannerView.loadAd()
    }

    private fun loadAdaptiveBanner() {
        adaptiveBannerView?.destroy()
        bindings.adaptiveBannerContainer.removeAllViews()

        val bannerView = AudienzzRemoteBannerView(
            requireContext(),
            ADAPTIVE_BANNER_CONFIG_ID
        )

        adaptiveBannerView = bannerView

        val params = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        )

        bindings.adaptiveBannerContainer.addView(bannerView, params)
        bannerView.loadAd()
    }

    private fun loadInterstitial() {
        interstitial?.destroy()
        interstitial = AudienzzRemoteConfigInterstitial(
            requireContext(),
            INTERSTITIAL_CONFIG_ID
        )
        interstitial?.loadAd()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        fixedBannerView?.destroy()
        adaptiveBannerView?.destroy()
        interstitial?.destroy()
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}


