package org.audienzz.mobile.testapp.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.audienzz.mobile.testapp.R
import org.audienzz.mobile.testapp.adapter.original.OriginalApiBannerAdHolder
import org.audienzz.mobile.testapp.adapter.original.OriginalApiInAppAdHolder
import org.audienzz.mobile.testapp.adapter.original.OriginalApiInStreamAdHolder
import org.audienzz.mobile.testapp.adapter.original.OriginalApiInterstitialAdHolder
import org.audienzz.mobile.testapp.adapter.original.OriginalApiMultiformatBannerAdHolder
import org.audienzz.mobile.testapp.adapter.original.OriginalApiNativeAdHolder
import org.audienzz.mobile.testapp.adapter.original.OriginalApiRewardedVideoAdHolder
import org.audienzz.mobile.testapp.adapter.original.OriginalApiUnfilledAdHolder
import org.audienzz.mobile.testapp.adapter.original.OriginalApiVideoBannerAdHolder
import org.audienzz.mobile.testapp.adapter.rendering.RenderingApiBannerAdHolder
import org.audienzz.mobile.testapp.adapter.rendering.RenderingApiInterstitialAdHolder
import org.audienzz.mobile.testapp.adapter.rendering.RenderingApiNativeAdHolder
import org.audienzz.mobile.testapp.adapter.rendering.RenderingApiRewardedVideoAdHolder
import org.audienzz.mobile.testapp.adapter.rendering.RenderingApiVideoBannerAdHolder
import org.audienzz.mobile.testapp.interfaces.Bindable
import java.security.InvalidParameterException

class AdsAdapter : ListAdapter<Int, RecyclerView.ViewHolder>(
    object : DiffUtil.ItemCallback<Int>() {
        override fun areItemsTheSame(oldItem: Int, newItem: Int): Boolean =
            oldItem == newItem

        override fun areContentsTheSame(oldItem: Int, newItem: Int): Boolean =
            oldItem == newItem
    },
) {

    override fun getItemViewType(position: Int): Int =
        getItem(position)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            HOLDER_TYPE_DEFAULT -> DefaultHolder(parent)
            HOLDER_TYPE_HTML_BANNER_ADS -> OriginalApiBannerAdHolder(parent)
            HOLDER_TYPE_VIDEO_BANNER_AD -> OriginalApiVideoBannerAdHolder(parent)
            HOLDER_TYPE_MULTIFORMAT_ADS -> OriginalApiMultiformatBannerAdHolder(parent)
            HOLDER_TYPE_INTERSTITIAL_ADS -> OriginalApiInterstitialAdHolder(parent)
            HOLDER_TYPE_REWARDED_AD -> OriginalApiRewardedVideoAdHolder(parent)
            HOLDER_TYPE_IN_STREAM_AD -> OriginalApiInStreamAdHolder(parent)
            HOLDER_TYPE_IN_NATIVE_STYLES_AD -> OriginalApiNativeAdHolder(parent)
            HOLDER_TYPE_IN_APP_AD -> OriginalApiInAppAdHolder(parent)
            HOLDER_TYPE_UNFILLED_AD -> OriginalApiUnfilledAdHolder(parent)
            HOLDER_TYPE_RENDER_HTML_AD -> RenderingApiBannerAdHolder(parent)
            HOLDER_TYPE_RENDER_VIDEO_AD -> RenderingApiVideoBannerAdHolder(parent)
            HOLDER_TYPE_RENDER_INTERSTITIAL_AD -> RenderingApiInterstitialAdHolder(parent)
            HOLDER_TYPE_RENDER_REWARDED_AD -> RenderingApiRewardedVideoAdHolder(parent)
            HOLDER_TYPE_RENDER_NATIVE_AD -> RenderingApiNativeAdHolder(parent)
            else -> throw InvalidParameterException()
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is Bindable) {
            holder.onBind(position)
        }
    }

    override fun onViewAttachedToWindow(holder: RecyclerView.ViewHolder) {
        super.onViewAttachedToWindow(holder)
        if (holder is BaseAdHolder) {
            holder.onAttach()
        }
    }

    override fun onViewDetachedFromWindow(holder: RecyclerView.ViewHolder) {
        super.onViewDetachedFromWindow(holder)
        if (holder is BaseAdHolder) {
            holder.onDetach()
        }
    }

    private inner class DefaultHolder(parent: ViewGroup) : Bindable, RecyclerView.ViewHolder(
        LayoutInflater.from(parent.context)
            .inflate(R.layout.item_lazy, parent, false),
    ) {

        private val tvTestCaseName = itemView.findViewById<TextView>(R.id.tvTestCaseName)

        override fun onBind(position: Int) {
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
        const val HOLDER_TYPE_UNFILLED_AD = 9

        // Render API
        const val HOLDER_TYPE_RENDER_HTML_AD = 10
        const val HOLDER_TYPE_RENDER_VIDEO_AD = 11
        const val HOLDER_TYPE_RENDER_INTERSTITIAL_AD = 12
        const val HOLDER_TYPE_RENDER_REWARDED_AD = 13
        const val HOLDER_TYPE_RENDER_NATIVE_AD = 14
    }
}
