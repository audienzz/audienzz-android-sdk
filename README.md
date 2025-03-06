Audienzz Android SDK
========

A wrapper for [Prebid Mobile SDK](https://github.com/prebid/prebid-mobile-android) with support of
ads lazy loading.

Download using Gradle
========

```gradle
repositories {
  mavenCentral()
}

dependencies {
  implementation 'com.audienzz:sdk:{latest_version}}'
}
```

Getting started
=======

Initialize SDK
-------
First of all, SDK needs to be intialized with context. It's done asynchronously, so after callback
is triggered with `SUCCEEDED` status, SDK is ready to use.

```kotlin
AudienzzPrebidMobile.initializeSdk(applicationContext, COMPANY_ID) { status ->
    if (status == AudienzzInitializationStatus.SUCCEEDED) {
        Log.d(App.TAG, "SDK was initialized successfully")
    } else {
        Log.e(App.TAG, "Error during SDK initialization: $status")
    }
}
```

Lazy Loading
-------
Sometimes application doesn't need to load an ad once the screen (activity/fragment)
is instantiated. Instead of that it might be more optimal to start loading when the ad is actually
presented to user, for example scroll reached the required view.

It can be done in several ways, depending on ad type:

* `audienzzBannerView.loadAd(lazyLoading = true)`
* `audienzzAdViewHandler.load(withLazyLoading = true, ...)`
* `view.lazyLoadAd(adHandler = audienzzInterstitialAdHandler, ...)`

In this way the `load()` or `fetchDemand()` will be postponed until the view is shown on the screen.

Examples
========
GAM Original API Banner
--------
Here is minimum example of GAM Original API ad:

```
val adUnit300x250 = AudienzzBannerAdUnit(CONFIG_ID_300_250, width, height)
val adView = AdManagerAdView(context) // or inflate from layout
adView.adUnitId = unitId
adView.setAdSizes(AdSize(width, height))
val parameters = AudienzzBannerParameters()
parameters.api = listOf(AudienzzSignals.Api.MRAID_3, AudienzzSignals.Api.OMID_1)
adUnit.bannerParameters = parameters
AudienzzAdViewHandler(
    adView = adView,
    adUnit = adUnit,
).load(callback = { request, resultCode ->
    adView.loadAd(request)
})
```

You can find more examples of practical implementation here:

[Examples](Example/TestApp/src/main/java/org/audienzz/mobile/testapp/adapter)

Troubleshooting
========
Sometimes there is a problem with the appearance of the scroll in banner ads.
To avoid this behavior - call AudienzzAdViewUtils.hideScrollBar(adView: AdManagerAdView) in onAdLoaded():

```kotlin
override fun onAdLoaded() {
    super.onAdLoaded()
    AudienzzAdViewUtils.hideScrollBar(adView)
}
```

License
========

    Copyright 2024 Audienzz AG.
    
    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at
    
       http://www.apache.org/licenses/LICENSE-2.0
    
    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.