package org.audienzz.mobile.testapp.adapter

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
import com.google.android.gms.ads.formats.OnAdManagerAdViewLoadedListener
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeCustomFormatAd
import com.google.common.collect.Lists
import org.audienzz.mobile.AudienzzNativeAdUnit
import org.audienzz.mobile.AudienzzNativeDataAsset
import org.audienzz.mobile.AudienzzNativeEventTracker
import org.audienzz.mobile.AudienzzNativeImageAsset
import org.audienzz.mobile.AudienzzNativeTitleAsset
import org.audienzz.mobile.AudienzzPrebidNativeAd
import org.audienzz.mobile.AudienzzPrebidNativeAdListener
import org.audienzz.mobile.addentum.AudienzzAdViewUtils
import org.audienzz.mobile.testapp.R

class GamRenderApiNativeAdHolder(parent: ViewGroup) : AdHolder(parent) {

    override val titleRes = R.string.gam_render_native_title

    private var adView: AdManagerAdView? = null
    private var unifiedNativeAd: NativeAd? = null
    private var adUnit: AudienzzNativeAdUnit? = null
    private var adLoader: AdLoader? = null

    override fun createAds() {
        adUnit = configureNativeAdUnit()
        val adRequest = AdManagerAdRequest.Builder().build()
        adLoader = createAdLoader(adContainer)
        adUnit?.fetchDemand(adRequest) { resultCode ->
            showFetchErrorDialog(adContainer.context, resultCode)
            adLoader?.loadAd(adRequest)
        }
    }

    private fun configureNativeAdUnit(): AudienzzNativeAdUnit {
        val adUnit = AudienzzNativeAdUnit(CONFIG_ID)

        adUnit.setContextType(AudienzzNativeAdUnit.ContextType.SOCIAL_CENTRIC)
        adUnit.setPlacementType(AudienzzNativeAdUnit.PlacementType.CONTENT_FEED)
        adUnit.setContextSubType(AudienzzNativeAdUnit.ContextSubtype.GENERAL_SOCIAL)

        val methods = ArrayList<AudienzzNativeEventTracker.EventTrackingMethod>()
        methods.add(AudienzzNativeEventTracker.EventTrackingMethod.IMAGE)
        methods.add(AudienzzNativeEventTracker.EventTrackingMethod.JS)

        try {
            val tracker = AudienzzNativeEventTracker(
                AudienzzNativeEventTracker.EventType.IMPRESSION,
                methods,
            )
            adUnit.addEventTracker(tracker)
        } catch (e: Exception) {
            showErrorDialog(
                adContainer.context,
                e.message.orEmpty(),
            )
            e.printStackTrace()
        }

        val title = AudienzzNativeTitleAsset()
        title.len = 90
        title.isRequired = true
        adUnit.addAsset(title)

        val icon = AudienzzNativeImageAsset(20, 20, 20, 20)
        icon.imageType = AudienzzNativeImageAsset.ImageType.ICON
        icon.isRequired = true
        adUnit.addAsset(icon)

        val image = AudienzzNativeImageAsset(200, 200, 200, 200)
        image.imageType = AudienzzNativeImageAsset.ImageType.MAIN
        image.isRequired = true
        adUnit.addAsset(image)

        val data = AudienzzNativeDataAsset()
        data.len = 90
        data.dataType = AudienzzNativeDataAsset.DataType.SPONSORED
        data.isRequired = true
        adUnit.addAsset(data)

        val body = AudienzzNativeDataAsset()
        body.isRequired = true
        body.dataType = AudienzzNativeDataAsset.DataType.DESC
        adUnit.addAsset(body)

        val cta = AudienzzNativeDataAsset()
        cta.isRequired = true
        cta.dataType = AudienzzNativeDataAsset.DataType.CTATEXT
        adUnit.addAsset(cta)

        return adUnit
    }

    private fun createAdLoader(wrapper: ViewGroup): AdLoader {
        val onGamAdLoaded = OnAdManagerAdViewLoadedListener { adManagerAdView: AdManagerAdView? ->
            Log.d("GamNative", "Gam loaded")
            adView = adManagerAdView
            wrapper.addView(adManagerAdView)
        }

        val onUnifiedAdLoaded = NativeAd.OnNativeAdLoadedListener { unifiedNativeAd: NativeAd ->
            Log.d("GamNative", "Unified native loaded")
            this.unifiedNativeAd = unifiedNativeAd
        }

        val onCustomAdLoaded =
            NativeCustomFormatAd.OnCustomFormatAdLoadedListener { nativeCustomTemplateAd: NativeCustomFormatAd? ->
                Log.d("GamNative", "Custom ad loaded")
                AudienzzAdViewUtils.findNative(
                    nativeCustomTemplateAd!!,
                    object :
                        AudienzzPrebidNativeAdListener {
                        override fun onPrebidNativeLoaded(ad: AudienzzPrebidNativeAd?) {
                            ad?.let { inflatePrebidNativeAd(it, wrapper) }
                        }

                        override fun onPrebidNativeNotFound() {
                            Log.e("GamNative", "onPrebidNativeNotFound")
                        }

                        override fun onPrebidNativeNotValid() {
                            Log.e("GamNative", "onPrebidNativeNotFound")
                        }
                    },
                )
            }

        return AdLoader.Builder(wrapper.context, AD_UNIT_ID)
            .forAdManagerAdView(onGamAdLoaded, AdSize.BANNER)
            .forNativeAd(onUnifiedAdLoaded)
            .forCustomFormatAd(
                CUSTOM_FORMAT_ID,
                onCustomAdLoaded,
            ) { _: NativeCustomFormatAd?, _: String? -> }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    super.onAdFailedToLoad(loadAdError)
                    showAdLoadingErrorDialog(adContainer.context, loadAdError)
                }
            })
            .build()
    }

    private fun inflatePrebidNativeAd(ad: AudienzzPrebidNativeAd, wrapper: ViewGroup) {
        val nativeContainer = View.inflate(wrapper.context, R.layout.layout_native, null)

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

        wrapper.addView(nativeContainer)
    }

    override fun onDetach() {
        super.onDetach()
        adView?.destroy()
        unifiedNativeAd?.destroy()
        adUnit?.stopAutoRefresh()
    }

    companion object {

        private const val AD_UNIT_ID = "/21808260008/apollo_custom_template_native_ad_unit"
        private const val CONFIG_ID = "prebid-demo-banner-native-styles"
        private const val CUSTOM_FORMAT_ID = "11934135"
    }
}
