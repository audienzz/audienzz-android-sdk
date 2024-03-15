package org.prebid.mobile.prebidkotlindemo.view.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.prebid.mobile.AdSize
import org.prebid.mobile.api.rendering.BannerView
import org.prebid.mobile.eventhandlers.GamBannerEventHandler
import org.prebid.mobile.prebidkotlindemo.R
import org.prebid.mobile.prebidkotlindemo.utils.Settings
import java.security.InvalidParameterException

class BannerRenderingLazyAdapter : ListAdapter<String, RecyclerView.ViewHolder>(
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

        private var adView: BannerView? = null

        protected val adWrapperView = itemView.findViewById<FrameLayout>(R.id.frameAdWrapper)

        protected val refreshTimeSeconds: Int
            get() = Settings.get().refreshTimeSeconds

        init {
            createAd()
        }

        /**
         * Code was copied from GamRenderingApiDisplayBanner320x50Activity
         */
        private fun createAd() {
            val eventHandler =
                GamBannerEventHandler(itemView.context, AD_UNIT_ID, AdSize(WIDTH, HEIGHT))
            adView = BannerView(itemView.context, CONFIG_ID, eventHandler)
            adWrapperView.addView(adView)
            adView?.setAutoRefreshDelay(refreshTimeSeconds)
            adView?.loadAd()
        }

        fun onBind(position: Int) {
            adView?.loadAd()
        }
    }

    companion object {

        private const val HOLDER_TYPE_DEFAULT = 0
        private const val HOLDER_TYPE_AD = 1

        const val AD_UNIT_ID = "/21808260008/prebid_oxb_320x50_banner"
        const val CONFIG_ID = "prebid-demo-banner-320-50"
        const val WIDTH = 320
        const val HEIGHT = 50
    }
}
