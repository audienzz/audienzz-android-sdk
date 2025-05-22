package org.audienzz.mobile.testapp.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.audienzz.mobile.testapp.R
import java.security.InvalidParameterException

class BannerOriginalLazyAdapter : ListAdapter<Int, RecyclerView.ViewHolder>(
    object : DiffUtil.ItemCallback<Int>() {
        override fun areItemsTheSame(oldItem: Int, newItem: Int): Boolean {
            return oldItem == newItem
        }

        override fun areContentsTheSame(oldItem: Int, newItem: Int): Boolean {
            return oldItem == newItem
        }
    }) {

    override fun getItemViewType(position: Int): Int =
        getItem(position)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            HOLDER_TYPE_DEFAULT -> DefaultHolder(parent)
            HOLDER_TYPE_HTML_BANNER_ADS -> GamOriginalApiHtmlBannerAdsHolder(parent)
            HOLDER_TYPE_VIDEO_BANNER_AD -> GamOriginalApiVideoBannerAdHolder(parent)
            HOLDER_TYPE_MULTIFORMAT_ADS -> GamOriginalApiMultiformatBannerAdsHolder(parent)
            HOLDER_TYPE_INTERSTITIAL_ADS -> GamOriginalApiInterstitialAdHolder(parent)
            HOLDER_TYPE_REWARDED_AD -> GamOriginalApiRewardedVideoAdHolder(parent)
            HOLDER_TYPE_IN_STREAM_AD -> GamOriginApiInStreamAdHolder(parent)
            HOLDER_TYPE_IN_NATIVE_STYLES_AD -> GamOriginApiNativeStyleAdHolder(parent)
            HOLDER_TYPE_IN_APP_AD -> GamOriginApiInAppAdHolder(parent)
            HOLDER_TYPE_RENDER_HTML_AD -> GamRenderApiHtmlBannerAdHolder(parent)
            HOLDER_TYPE_RENDER_VIDEO_AD -> GamRenderApiVideoBannerAdHolder(parent)
            HOLDER_TYPE_RENDER_INTERSTITIAL_AD -> GamRenderApiInterstitialAdHolder(parent)
            HOLDER_TYPE_RENDER_REWARDED_AD -> GamRenderApiRewardedVideoAdHolder(parent)
            HOLDER_TYPE_RENDER_NATIVE_AD -> GamRenderApiNativeAdHolder(parent)
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
            tvTestCaseName.text = itemView.resources.getString(R.string.simple_text, position)
        }
    }

    companion object {

        // Original API
        const val HOLDER_TYPE_DEFAULT = 0
        const val HOLDER_TYPE_HTML_BANNER_ADS = 1
        const val HOLDER_TYPE_VIDEO_BANNER_AD = 2
        const val HOLDER_TYPE_MULTIFORMAT_ADS = 3
        const val HOLDER_TYPE_INTERSTITIAL_ADS = 4
        const val HOLDER_TYPE_REWARDED_AD = 5
        const val HOLDER_TYPE_IN_STREAM_AD = 6
        const val HOLDER_TYPE_IN_NATIVE_STYLES_AD = 7
        const val HOLDER_TYPE_IN_APP_AD = 8

        // Render API
        const val HOLDER_TYPE_RENDER_HTML_AD = 9
        const val HOLDER_TYPE_RENDER_VIDEO_AD = 10
        const val HOLDER_TYPE_RENDER_INTERSTITIAL_AD = 11
        const val HOLDER_TYPE_RENDER_REWARDED_AD = 12
        const val HOLDER_TYPE_RENDER_NATIVE_AD = 13
    }
}
