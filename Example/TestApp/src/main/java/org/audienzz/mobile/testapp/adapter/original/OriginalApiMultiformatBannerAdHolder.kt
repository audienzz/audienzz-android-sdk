package org.audienzz.mobile.testapp.adapter.original

import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.admanager.AdManagerAdRequest
import com.google.android.gms.ads.admanager.AdManagerAdView
import com.google.android.gms.ads.formats.AdManagerAdViewOptions
import com.google.android.gms.ads.formats.OnAdManagerAdViewLoadedListener
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeCustomFormatAd
import com.google.common.collect.Lists
import org.audienzz.mobile.AudienzzAdSize
import org.audienzz.mobile.AudienzzBannerAdUnit
import org.audienzz.mobile.AudienzzBannerParameters
import org.audienzz.mobile.AudienzzNativeAdUnit
import org.audienzz.mobile.AudienzzNativeEventTracker
import org.audienzz.mobile.AudienzzNativeParameters
import org.audienzz.mobile.AudienzzPrebidNativeAd
import org.audienzz.mobile.AudienzzPrebidNativeAdListener
import org.audienzz.mobile.AudienzzSignals
import org.audienzz.mobile.AudienzzVideoParameters
import org.audienzz.mobile.addentum.AudienzzAdViewUtils
import org.audienzz.mobile.addentum.AudienzzPbFindSizeListener
import org.audienzz.mobile.api.data.AudienzzAdUnitFormat
import org.audienzz.mobile.api.original.AudienzzPrebidAdUnit
import org.audienzz.mobile.api.original.AudienzzPrebidRequest
import org.audienzz.mobile.original.AudienzzAdViewHandler
import org.audienzz.mobile.original.AudienzzMultiformatAdHandler
import org.audienzz.mobile.testapp.R
import org.audienzz.mobile.testapp.adapter.BaseAdHolder
import org.audienzz.mobile.testapp.constants.SizeConstants
import org.audienzz.mobile.testapp.utils.NativeAdUtils
import java.util.EnumSet
import java.util.Random

class OriginalApiMultiformatBannerAdHolder(parent: ViewGroup) : BaseAdHolder(parent) {

    override val titleRes = R.string.original_api_multiformat_title

    private var adUnit: AudienzzBannerAdUnit? = null
    private var adUnitMultiformat: AudienzzPrebidAdUnit? = null

    override fun createAds() {
        createBannerAd()
        createMultiformatAd()
    }

    private fun createBannerAd() {
        val configId = if (Random().nextBoolean()) {
            CONFIG_ID_BANNER
        } else {
            CONFIG_ID_VIDEO
        }

        adUnit = AudienzzBannerAdUnit(
            configId,
            SizeConstants.MEDIUM_BANNER_WIDTH,
            SizeConstants.MEDIUM_BANNER_HEIGHT,
            EnumSet.of(AudienzzAdUnitFormat.BANNER, AudienzzAdUnitFormat.VIDEO),
        )
        adUnit?.setAutoRefreshInterval(DEFAULT_REFRESH_TIME)

        val parameters = AudienzzBannerParameters()
        parameters.api = listOf(AudienzzSignals.Api.MRAID_3, AudienzzSignals.Api.OMID_1)
        adUnit?.bannerParameters = parameters
        adUnit?.videoParameters = AudienzzVideoParameters(listOf("video/mp4"))

        val adView = AdManagerAdView(adContainer.context).apply {
            adUnitId = AD_UNIT_ID
            setAdSizes(
                AdSize(
                    SizeConstants.MEDIUM_BANNER_WIDTH,
                    SizeConstants.MEDIUM_BANNER_HEIGHT,
                ),
            )
            adListener = createGAMListener(this)
        }

        adContainer.addView(adView)
        addBottomMargin(adView)

        AudienzzAdViewHandler(
            adView = adView,
            adUnit = adUnit!!,
        ).load(callback = { request, resultCode ->
            showFetchErrorDialog(adContainer.context, resultCode)
            adView.loadAd(request)
        })
    }

    private fun createMultiformatAd() {
        val configId = listOf(CONFIG_ID_BANNER, CONFIG_ID_VIDEO, CONFIG_ID_NATIVE).random()
        adUnitMultiformat = AudienzzPrebidAdUnit(configId)

        val prebidRequest = AudienzzPrebidRequest().apply {
            setBannerParameters(createBannerParameters())
            setVideoParameters(createVideoParameters())
            setNativeParameters(createNativeParameters())
        }

        val gamRequestBuilder = AdManagerAdRequest.Builder()
        AudienzzMultiformatAdHandler(adUnitMultiformat!!, AD_UNIT_ID_MULTIFORMAT)
            .load(gamRequestBuilder, prebidRequest) { bidInfo ->
                showFetchErrorDialog(adContainer.context, bidInfo.resultCode)
                loadGam(gamRequestBuilder)
            }
    }

    private fun createGAMListener(adView: AdManagerAdView): AdListener {
        return object : AdListener() {
            override fun onAdLoaded() {
                super.onAdLoaded()
                AudienzzAdViewUtils.findPrebidCreativeSize(
                    adView,
                    object : AudienzzPbFindSizeListener {
                        override fun success(width: Int, height: Int) {
                            adView.setAdSizes(AdSize(width, height))
                        }

                        override fun failure(errorCode: Int) {
                            TODO("Not yet implemented")
                        }
                    },
                )
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                super.onAdFailedToLoad(error)
                showAdLoadingErrorDialog(adContainer.context, error)
            }
        }
    }

    private fun loadGam(gamRequestBuilder: AdManagerAdRequest.Builder) {
        val onBannerLoaded = OnAdManagerAdViewLoadedListener { adView ->
            showBannerAd(adView)
        }

        val onNativeLoaded = NativeAd.OnNativeAdLoadedListener { nativeAd ->
            showNativeAd(nativeAd, adContainer)
        }

        val onPrebidNativeAdLoaded =
            NativeCustomFormatAd.OnCustomFormatAdLoadedListener { customNativeAd ->
                showPrebidNativeAd(customNativeAd)
            }

        val adLoader = AdLoader.Builder(adContainer.context, AD_UNIT_ID_MULTIFORMAT)
            .forAdManagerAdView(onBannerLoaded, AdSize.BANNER, AdSize.MEDIUM_RECTANGLE)
            .forNativeAd(onNativeLoaded)
            .forCustomFormatAd(CUSTOM_FORMAT_ID, onPrebidNativeAdLoaded, null)
            .withAdManagerAdViewOptions(AdManagerAdViewOptions.Builder().build())
            .build()
        adLoader.loadAd(gamRequestBuilder.build())
    }

    private fun createBannerParameters() = AudienzzBannerParameters().apply {
        adSizes = mutableSetOf(
            AudienzzAdSize(
                SizeConstants.MEDIUM_BANNER_WIDTH,
                SizeConstants.MEDIUM_BANNER_HEIGHT,
            ),
        )
    }

    private fun createVideoParameters() = AudienzzVideoParameters(listOf("video/mp4")).apply {
        adSize = AudienzzAdSize(SizeConstants.VIDEO_BANNER_WIDTH, SizeConstants.VIDEO_BANNER_HEIGHT)
    }

    private fun createNativeParameters(): AudienzzNativeParameters {
        val nativeParameters = NativeAdUtils.createNativeParameters()
        nativeParameters.addEventTracker(
            AudienzzNativeEventTracker(
                AudienzzNativeEventTracker.EventType.IMPRESSION,
                arrayListOf(AudienzzNativeEventTracker.EventTrackingMethod.IMAGE),
            ),
        )
        nativeParameters.setContextType(AudienzzNativeAdUnit.ContextType.SOCIAL_CENTRIC)
        nativeParameters.setPlacementType(AudienzzNativeAdUnit.PlacementType.CONTENT_FEED)
        nativeParameters.setContextSubType(AudienzzNativeAdUnit.ContextSubtype.GENERAL_SOCIAL)

        return nativeParameters
    }

    private fun showBannerAd(adView: AdManagerAdView) {
        adContainer.addView(adView)
        AudienzzAdViewUtils.findPrebidCreativeSize(
            adView,
            object : AudienzzPbFindSizeListener {
                override fun success(width: Int, height: Int) {
                    adView.setAdSizes(AdSize(width, height))
                }

                override fun failure(errorCode: Int) {
                    logFindCreativeSizeError(errorCode)
                }
            },
        )
    }

    private fun showNativeAd(ad: NativeAd, wrapper: ViewGroup) {
        val nativeContainer = View.inflate(wrapper.context, R.layout.layout_native, null)

        val icon = nativeContainer.findViewById<ImageView>(R.id.imgIcon)
        val iconUrl = ad.icon?.uri?.toString()
        if (iconUrl != null) {
            downloadImage(iconUrl, icon)
        }

        val title = nativeContainer.findViewById<TextView>(R.id.tvTitle)
        title.text = ad.headline

        val image = nativeContainer.findViewById<ImageView>(R.id.imgImage)
        val imageUrl = ad.images.getOrNull(0)?.uri?.toString()
        if (imageUrl != null) {
            downloadImage(imageUrl, image)
        }

        val description = nativeContainer.findViewById<TextView>(R.id.tvDesc)
        description.text = ad.body

        val cta = nativeContainer.findViewById<Button>(R.id.btnCta)
        cta.text = ad.callToAction

        wrapper.addView(nativeContainer)
    }

    private fun showPrebidNativeAd(customNativeAd: NativeCustomFormatAd) {
        AudienzzAdViewUtils.findNative(
            customNativeAd,
            object : AudienzzPrebidNativeAdListener {
                override fun onPrebidNativeLoaded(ad: AudienzzPrebidNativeAd?) {
                    ad?.let(::inflatePrebidNativeAd)
                }

                override fun onPrebidNativeNotFound() {
                    Log.e("PrebidAdViewUtils", "Find native failed: native not found")
                }

                override fun onPrebidNativeNotValid() {
                    Log.e("PrebidAdViewUtils", "Find native failed: native not valid")
                }
            },
        )
    }

    private fun inflatePrebidNativeAd(ad: AudienzzPrebidNativeAd) {
        val nativeContainer = View.inflate(adContainer.context, R.layout.layout_native, null)

        val icon = nativeContainer.findViewById<ImageView>(R.id.imgIcon)
        downloadImage(ad.iconUrl, icon)

        val title = nativeContainer.findViewById<TextView>(R.id.tvTitle)
        title.text = ad.title

        val image = nativeContainer.findViewById<ImageView>(R.id.imgImage)
        downloadImage(ad.imageUrl, image)

        val description = nativeContainer.findViewById<TextView>(R.id.tvDesc)
        description.text = ad.description

        val cta = nativeContainer.findViewById<Button>(R.id.btnCta)
        cta.text = ad.callToAction

        ad.registerView(
            nativeContainer,
            Lists.newArrayList(icon, title, image, description, cta),
            null,
        )

        adContainer.addView(nativeContainer)
    }

    override fun onDetach() {
        adUnit?.destroy()
        adUnitMultiformat?.destroy()
    }

    companion object {
        private const val AD_UNIT_ID = "/21808260008/prebid-demo-original-banner-multiformat"
        private const val CONFIG_ID_BANNER = "prebid-demo-banner-300-250"
        private const val CONFIG_ID_VIDEO = "prebid-demo-video-outstream-original-api"
        private const val CONFIG_ID_NATIVE = "prebid-demo-banner-native-styles"
        private const val CUSTOM_FORMAT_ID = "12304464"
        private const val AD_UNIT_ID_MULTIFORMAT = "/21808260008/prebid-demo-multiformat"
    }
}
