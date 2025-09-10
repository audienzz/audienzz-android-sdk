package org.audienzz.mobile.testapp.adapter.original

import android.view.ViewGroup
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.admanager.AdManagerAdView
import org.audienzz.mobile.AudienzzNativeAdUnit
import org.audienzz.mobile.AudienzzNativeEventTracker
import org.audienzz.mobile.original.AudienzzAdViewHandler
import org.audienzz.mobile.testapp.R
import org.audienzz.mobile.testapp.adapter.BaseAdHolder
import org.audienzz.mobile.testapp.utils.NativeAdUtils

class OriginalApiNativeAdHolder(parent: ViewGroup) : BaseAdHolder(parent) {

    override val titleRes = R.string.original_api_native_styles_title

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
        NativeAdUtils.addNativeAssets(adUnit)

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

