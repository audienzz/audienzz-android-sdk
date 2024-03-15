package org.prebid.mobile.prebidkotlindemo.view.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.admanager.AdManagerAdRequest
import com.google.android.gms.ads.admanager.AdManagerAdView
import org.prebid.mobile.BannerAdUnit
import org.prebid.mobile.BannerParameters
import org.prebid.mobile.Signals
import org.prebid.mobile.addendum.AdViewUtils
import org.prebid.mobile.addendum.PbFindSizeError
import org.prebid.mobile.prebidkotlindemo.R
import org.prebid.mobile.prebidkotlindemo.utils.Settings
import java.security.InvalidParameterException

class BannerOriginalLazyAdapter : ListAdapter<String, RecyclerView.ViewHolder>(
    object : DiffUtil.ItemCallback<String>() {
        override fun areItemsTheSame(oldItem: String, newItem: String): Boolean {
            return oldItem == newItem
        }

        override fun areContentsTheSame(oldItem: String, newItem: String): Boolean {
            return oldItem == newItem
        }
    }) {

    override fun getItemViewType(position: Int): Int {
        return if (position > 0 && position % 40 == 0) HOLDER_TYPE_AD else HOLDER_TYPE_DEFAULT
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            HOLDER_TYPE_DEFAULT -> DefaultHolder(parent)
            HOLDER_TYPE_AD -> AdHolder(parent)
            else -> throw InvalidParameterException()
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is DefaultHolder) {
            holder.onBind(position)
        } else if (holder is AdHolder) {
            holder.onBind(position)
        }
    }

    override fun onViewAttachedToWindow(holder: RecyclerView.ViewHolder) {
        super.onViewAttachedToWindow(holder)
        if (holder is AdHolder) {
            holder.onAttach()
        }
    }

    override fun onViewDetachedFromWindow(holder: RecyclerView.ViewHolder) {
        super.onViewDetachedFromWindow(holder)
        if (holder is AdHolder) {
            holder.onDetach()
        }
    }

    private inner class DefaultHolder(parent: ViewGroup) : RecyclerView.ViewHolder(
        LayoutInflater.from(parent.context)
            .inflate(R.layout.item_lazy, parent, false)
    ) {

        private val tvTestCaseName = itemView.findViewById<TextView>(R.id.tvTestCaseName)

        fun onBind(position: Int) {
            tvTestCaseName.text = "Simple text #$position"
        }
    }

    private inner class AdHolder(parent: ViewGroup) : RecyclerView.ViewHolder(
        LayoutInflater.from(parent.context)
            .inflate(R.layout.item_native_lazy, parent, false)
    ) {

        private var adUnit: BannerAdUnit? = null

        protected val adWrapperView = itemView.findViewById<FrameLayout>(R.id.frameAdWrapper)

        protected val refreshTimeSeconds: Int
            get() = Settings.get().refreshTimeSeconds

        init {
            createAd()
        }

        /**
         * Code was copied from GamOriginalApiDisplayBanner320x50Activity
         */
        private fun createAd() {
            val adView = AdManagerAdView(adWrapperView.context)
            adView.adUnitId = AD_UNIT_ID
            adView.setAdSizes(AdSize(WIDTH, HEIGHT))
            adView.adListener = object : AdListener() {
                override fun onAdLoaded() {
                    super.onAdLoaded()
                    AdViewUtils.findPrebidCreativeSize(
                        adView,
                        object : AdViewUtils.PbFindSizeListener {
                            override fun success(width: Int, height: Int) {
                                adView.setAdSizes(AdSize(width, height))
                            }

                            override fun failure(error: PbFindSizeError) {}
                        })
                }
            }
            adWrapperView.addView(adView)

            val request = AdManagerAdRequest.Builder().build()
            adUnit = BannerAdUnit(CONFIG_ID, WIDTH, HEIGHT)

            val parameters = BannerParameters()
            parameters.api = listOf(Signals.Api.MRAID_3, Signals.Api.OMID_1)
            adUnit?.bannerParameters = parameters

            adUnit?.setAutoRefreshInterval(refreshTimeSeconds)
            adUnit?.fetchDemand(request) { adView.loadAd(request) }
        }

        fun onAttach() {
            adUnit?.resumeAutoRefresh()
        }

        fun onDetach() {
            adUnit?.stopAutoRefresh()
        }

        fun onBind(position: Int) {
            // none
        }
    }

    companion object {

        private const val HOLDER_TYPE_DEFAULT = 0
        private const val HOLDER_TYPE_AD = 1

        const val AD_UNIT_ID = "/21808260008/prebid_demo_app_original_api_banner"
        const val CONFIG_ID = "prebid-demo-banner-320-50"
        const val WIDTH = 320
        const val HEIGHT = 50
    }
}
