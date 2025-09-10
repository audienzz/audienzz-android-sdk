package org.audienzz.mobile.testapp.utils

import org.audienzz.mobile.AudienzzNativeAdUnit
import org.audienzz.mobile.AudienzzNativeAsset
import org.audienzz.mobile.AudienzzNativeDataAsset
import org.audienzz.mobile.AudienzzNativeImageAsset
import org.audienzz.mobile.AudienzzNativeParameters
import org.audienzz.mobile.AudienzzNativeTitleAsset
import org.audienzz.mobile.testapp.constants.SizeConstants

object NativeAdUtils {
    private fun createNativeAssetsList(): List<AudienzzNativeAsset> {
        val assets = mutableListOf<AudienzzNativeAsset>()

        val title = AudienzzNativeTitleAsset()
        title.len = SizeConstants.NATIVE_AD_TEXT_LEN
        title.isRequired = true
        assets.add(title)

        val icon = AudienzzNativeImageAsset(
            SizeConstants.NATIVE_AD_ICON_SIZE,
            SizeConstants.NATIVE_AD_ICON_SIZE,
            SizeConstants.NATIVE_AD_ICON_SIZE,
            SizeConstants.NATIVE_AD_ICON_SIZE,
        )
        icon.imageType = AudienzzNativeImageAsset.ImageType.ICON
        icon.isRequired = true
        assets.add(icon)

        val image = AudienzzNativeImageAsset(
            SizeConstants.NATIVE_AD_IMAGE_SIZE,
            SizeConstants.NATIVE_AD_IMAGE_SIZE,
            SizeConstants.NATIVE_AD_IMAGE_SIZE,
            SizeConstants.NATIVE_AD_IMAGE_SIZE,
        )
        image.imageType = AudienzzNativeImageAsset.ImageType.MAIN
        image.isRequired = true
        assets.add(image)

        val data = AudienzzNativeDataAsset()
        data.len = SizeConstants.NATIVE_AD_TEXT_LEN
        data.dataType = AudienzzNativeDataAsset.DataType.SPONSORED
        data.isRequired = true
        assets.add(data)

        val body = AudienzzNativeDataAsset()
        body.isRequired = true
        body.dataType = AudienzzNativeDataAsset.DataType.DESC
        assets.add(body)

        val cta = AudienzzNativeDataAsset()
        cta.isRequired = true
        cta.dataType = AudienzzNativeDataAsset.DataType.CTATEXT
        assets.add(cta)

        return assets
    }

    fun createNativeParameters() =
        AudienzzNativeParameters(createNativeAssetsList())

    fun addNativeAssets(adUnit: AudienzzNativeAdUnit?) {
        createNativeAssetsList().forEach { asset ->
            adUnit?.addAsset(asset)
        }
    }
}
