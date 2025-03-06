package org.audienzz.mobile.util

import com.google.android.gms.ads.AdError

data class AudienzzFullScreenContentCallback(
    val onAdClickedAd: () -> Unit = {},
    val onAdDismissedFullScreen: () -> Unit = {},
    val onAdFailedToShowFullScreen: (AdError) -> Unit = {},
    val onAdImpression: () -> Unit = {},
    val onAdShowedFullScreen: () -> Unit = {},
)
