package org.audienzz.mobile.testapp.view

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.admanager.AdManagerAdView
import org.audienzz.mobile.AudienzzBannerAdUnit
import org.audienzz.mobile.AudienzzPrebidMobile
import org.audienzz.mobile.AudienzzTargetingParams
import org.audienzz.mobile.api.data.AudienzzInitializationStatus
import org.audienzz.mobile.original.AudienzzAdViewHandler
import org.audienzz.mobile.testapp.App
import org.audienzz.mobile.testapp.R
import org.audienzz.mobile.testapp.adapter.BaseAdHolder
import org.audienzz.mobile.testapp.adapter.original.OriginalApiLazyPrefetchBannerAdHolder
import org.audienzz.mobile.util.remote.RemoteConfigManager

/**
 * Minimal tab for verifying that the `api` signals (MRAID_1/2/3, OMID_1) appear correctly
 * in the Prebid bid request body in Charles. Shows non-remote (Original API) banners backed
 * by [AudienzzBannerAdUnit] + [AudienzzAdViewHandler] — no [org.audienzz.mobile.AudienzzRemoteBannerView].
 */
class NonRemoteBannersFragment : Fragment() {

    private lateinit var progressBar: View
    private lateinit var recyclerView: RecyclerView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val root = inflater.inflate(R.layout.fragment_non_remote_banners, container, false)
        progressBar = root.findViewById(R.id.progressBar)
        recyclerView = root.findViewById(R.id.list)
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView.layoutManager = LinearLayoutManager(requireContext()).apply {
            initialPrefetchItemCount = 3
        }

        if (AudienzzPrebidMobile.isSdkInitialized) {
            onSdkReady()
        } else {
            progressBar.isVisible = true
            initSdk()
        }
    }

    private fun initSdk() {
        RemoteConfigManager.initialize(
            publisherId = PUBLISHER_ID,
            remoteUrl = "https://api.adnz.co/api/ws-sdk-config/public/v1",
        )
        AudienzzPrebidMobile.isPbsDebug = true
        AudienzzPrebidMobile.initializeRemoteSdk(
            requireContext().applicationContext,
            PUBLISHER_ID,
            true,
        ) { status ->
            if (status == AudienzzInitializationStatus.SUCCEEDED) {
                AudienzzTargetingParams.bundleName = requireContext().packageName
                AudienzzTargetingParams.storeUrl =
                    "https://play.google.com/store/apps/details?id=${requireContext().packageName}"
                Log.d(App.TAG, "NonRemoteBannersFragment: SDK initialized")
            } else {
                Log.e(App.TAG, "NonRemoteBannersFragment: SDK init error: $status")
            }
            progressBar.isVisible = false
            onSdkReady()
        }
    }

    private fun onSdkReady() {
        val adapter = NonRemoteBannersAdapter()
        recyclerView.adapter = adapter
    }

    // ---------------------------------------------------------------------------
    // Minimal adapter — one item per banner variant we want to verify
    // ---------------------------------------------------------------------------

    private inner class NonRemoteBannersAdapter :
        RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        override fun getItemCount(): Int = ITEMS.size
        override fun getItemViewType(position: Int): Int = ITEMS[position]

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int,
        ): RecyclerView.ViewHolder = when (viewType) {
            TYPE_STANDARD -> NonRemoteBannerHolder(parent)
            TYPE_LAZY_PREFETCH -> OriginalApiLazyPrefetchBannerAdHolder(parent)
            else -> error("Unknown viewType $viewType")
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            if (holder is org.audienzz.mobile.testapp.interfaces.Bindable) {
                holder.onBind(position)
            }
        }

        override fun onViewAttachedToWindow(holder: RecyclerView.ViewHolder) {
            super.onViewAttachedToWindow(holder)
            if (holder is BaseAdHolder) holder.onAttach()
        }

        override fun onViewDetachedFromWindow(holder: RecyclerView.ViewHolder) {
            super.onViewDetachedFromWindow(holder)
            if (holder is BaseAdHolder) holder.onDetach()
        }
    }

    // ---------------------------------------------------------------------------
    // Simple non-remote banner holder — immediate load, no lazy loading
    // ---------------------------------------------------------------------------

    private inner class NonRemoteBannerHolder(parent: ViewGroup) : BaseAdHolder(parent) {

        override val titleRes = R.string.non_remote_banner_title

        private var adUnit: AudienzzBannerAdUnit? = null
        private var handler: AudienzzAdViewHandler? = null

        override fun createAds() {
            AudienzzPrebidMobile.getAdUnitConfig(BANNER_CONFIG_ID) { config ->
                config ?: return@getAdUnitConfig

                val unit = AudienzzBannerAdUnit(
                    config.prebidConfig.placementId,
                    BANNER_WIDTH,
                    BANNER_HEIGHT,
                ).apply {
                    setAutoRefreshInterval(AUTO_REFRESH_SECONDS)
                }
                adUnit = unit

                // Log the api signals so they're visible in Logcat alongside Charles
                Log.d(TAG, "banner api signals: ${unit.bannerParameters?.api}")

                val adView = AdManagerAdView(adContainer.context).apply {
                    adUnitId = config.gamConfig.adUnitPath
                    setAdSizes(AdSize(BANNER_WIDTH, BANNER_HEIGHT))
                }
                adContainer.addView(adView)
                addBottomMargin(adView)

                handler = AudienzzAdViewHandler(adView = adView, adUnit = unit)
                handler?.load(
                    withLazyLoading = false,
                    callback = { request, resultCode ->
                        showFetchErrorDialog(adContainer.context, resultCode)
                        adView.loadAd(request)
                    },
                )
                handler?.enableSmartRefresh()
            }
        }

        override fun onAttach() {
            adUnit?.resumeAutoRefresh()
        }

        override fun onDetach() {
            handler?.disableSmartRefresh()
            adUnit?.stopAutoRefresh()
        }
    }

    companion object {
        private const val TAG = "NonRemoteBanners"
        private const val PUBLISHER_ID = "35"
        private const val BANNER_CONFIG_ID = "46"
        private const val BANNER_WIDTH = 300
        private const val BANNER_HEIGHT = 250
        private const val AUTO_REFRESH_SECONDS = 60

        private const val TYPE_STANDARD = 0
        private const val TYPE_LAZY_PREFETCH = 1

        private val ITEMS = listOf(TYPE_STANDARD, TYPE_LAZY_PREFETCH)
    }
}
