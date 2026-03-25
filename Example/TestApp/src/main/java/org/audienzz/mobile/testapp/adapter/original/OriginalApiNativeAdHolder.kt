package org.audienzz.mobile.testapp.adapter.original

import android.view.ViewGroup
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.admanager.AdManagerAdView
import org.audienzz.mobile.AudienzzNativeAdUnit
import org.audienzz.mobile.AudienzzNativeEventTracker
import org.audienzz.mobile.AudienzzPrebidMobile
import org.audienzz.mobile.original.AudienzzAdViewHandler
import org.audienzz.mobile.testapp.R
import org.audienzz.mobile.testapp.adapter.BaseAdHolder
import org.audienzz.mobile.testapp.utils.NativeAdUtils

class OriginalApiNativeAdHolder(parent: ViewGroup) : BaseAdHolder(parent) {

    override val titleRes = R.string.original_api_native_styles_title

    private var nativeAdUnit: AudienzzNativeAdUnit? = null

    override fun createAds() {
        AudienzzPrebidMobile.getAdUnitConfig(NATIVE_CONFIG_ID) { config ->
            config ?: return@getAdUnitConfig

            val placementId = config.prebidConfig.placementId
            val gamPath = config.gamConfig.adUnitPath

            nativeAdUnit = AudienzzNativeAdUnit(placementId).apply {
                setContextType(AudienzzNativeAdUnit.ContextType.SOCIAL_CENTRIC)
                setPlacementType(AudienzzNativeAdUnit.PlacementType.CONTENT_FEED)
                setContextSubType(AudienzzNativeAdUnit.ContextSubtype.GENERAL_SOCIAL)
            }
            addNativeAssets(nativeAdUnit)

            val gamView = AdManagerAdView(adContainer.context)
            gamView.adUnitId = gamPath
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
        private const val NATIVE_CONFIG_ID = "46"
    }
}
