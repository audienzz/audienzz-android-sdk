package org.audienzz.mobile.testapp.adapter

import android.view.ViewGroup
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.admanager.AdManagerAdView
import org.audienzz.mobile.AudienzzNativeAdUnit
import org.audienzz.mobile.AudienzzNativeDataAsset
import org.audienzz.mobile.AudienzzNativeEventTracker
import org.audienzz.mobile.AudienzzNativeImageAsset
import org.audienzz.mobile.AudienzzNativeTitleAsset
import org.audienzz.mobile.original.AudienzzAdViewHandler
import org.audienzz.mobile.testapp.R

class GamOriginApiNativeStyleAdHolder(parent: ViewGroup) : AdHolder(parent) {

    override val titleRes = R.string.gam_original_native_styles_title

    private var nativeAdUnit: AudienzzNativeAdUnit? = null

    override fun createAds() {
        nativeAdUnit = AudienzzNativeAdUnit(CONFIG_ID).apply {
            setContextType(AudienzzNativeAdUnit.ContextType.SOCIAL_CENTRIC)
            setPlacementType(AudienzzNativeAdUnit.PlacementType.CONTENT_FEED)
            setContextSubType(AudienzzNativeAdUnit.ContextSubtype.GENERAL_SOCIAL)
        }
        addNativeAssets(nativeAdUnit)

        val gamView = AdManagerAdView(adContainer.context)
        gamView.adUnitId = AD_UNIT_ID
        gamView.setAdSizes(AdSize.FLUID)
        adContainer.addView(gamView)

        nativeAdUnit?.let {
            AudienzzAdViewHandler(
                adView = gamView,
                adUnit = it,
            ).load(
                callback = { request, resultCode ->
                    showFetchErrorDialog(adContainer.context, resultCode)
                    gamView.loadAd(request)
                },
            )
        }
    }

    private fun addNativeAssets(adUnit: AudienzzNativeAdUnit?) {
        val title = AudienzzNativeTitleAsset()
        title.len = 90
        title.isRequired = true
        adUnit?.addAsset(title)

        val icon = AudienzzNativeImageAsset(20, 20, 20, 20)
        icon.imageType = AudienzzNativeImageAsset.ImageType.ICON
        icon.isRequired = true
        adUnit?.addAsset(icon)

        val image = AudienzzNativeImageAsset(200, 200, 200, 200)
        image.imageType = AudienzzNativeImageAsset.ImageType.MAIN
        image.isRequired = true
        adUnit?.addAsset(image)

        val data = AudienzzNativeDataAsset()
        data.len = 90
        data.dataType = AudienzzNativeDataAsset.DataType.SPONSORED
        data.isRequired = true
        adUnit?.addAsset(data)

        val body = AudienzzNativeDataAsset()
        body.isRequired = true
        body.dataType = AudienzzNativeDataAsset.DataType.DESC
        adUnit?.addAsset(body)

        val cta = AudienzzNativeDataAsset()
        cta.isRequired = true
        cta.dataType = AudienzzNativeDataAsset.DataType.CTATEXT
        adUnit?.addAsset(cta)

        val methods = ArrayList<AudienzzNativeEventTracker.EventTrackingMethod>()
        methods.add(AudienzzNativeEventTracker.EventTrackingMethod.IMAGE)

        val tracker = AudienzzNativeEventTracker(
            AudienzzNativeEventTracker.EventType.IMPRESSION,
            methods,
        )
        adUnit?.addEventTracker(tracker)
    }

    companion object {

        private const val AD_UNIT_ID = "/21808260008/prebid-demo-original-native-styles"
        private const val CONFIG_ID = "prebid-demo-banner-native-styles"
    }
}

