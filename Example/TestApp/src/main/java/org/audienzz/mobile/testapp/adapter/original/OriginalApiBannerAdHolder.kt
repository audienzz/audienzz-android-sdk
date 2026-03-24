package org.audienzz.mobile.testapp.adapter.original

import android.view.ViewGroup
import org.audienzz.mobile.AudienzzRemoteBannerView
import org.audienzz.mobile.testapp.R
import org.audienzz.mobile.testapp.adapter.BaseAdHolder

class OriginalApiBannerAdHolder(parent: ViewGroup) : BaseAdHolder(parent) {
    override val titleRes = R.string.original_api_banner_title

    private var bannerView: AudienzzRemoteBannerView? = null

    override fun createAds() {
        val view = AudienzzRemoteBannerView(adContainer.context, BANNER_CONFIG_ID)
        bannerView = view
        adContainer.addView(view)
        view.loadAd()
    }

    override fun onAttach() {
        bannerView?.onResume()
    }

    override fun onDetach() {
        bannerView?.onPause()
    }

    companion object {
        private const val TAG = "Original API BannerAd"
        private const val BANNER_CONFIG_ID = "46"
    }
}
