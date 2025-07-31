Audienzz Android SDK
========

## Overview

A mobile advertising SDK that combines header bidding capabilities from Prebid Mobile with Google's advertising ecosystem through a unified interface.
The implementation includes lazy loading functionality to optimize application performance by deferring ad initialization until needed.

## Underlying Technologies

### Prebid Mobile SDK

Prebid Mobile is an open-source framework that enables header bidding within mobile applications.
It conducts real-time auctions where multiple demand sources compete for ad inventory placement.

Functionality:

- Real-time auction management between demand partners
- Communication with Prebid Server for bid processing
- Support for banner, native, and video ad formats
- Ad rendering from winning auction results

### Google Ads SDK (Google Mobile Ads SDK)

The Google Mobile Ads SDK provides access to Google's advertising networks including AdMob and Google 
Ad Manager. It handles ad serving and mediation across multiple ad networks.

Functionality:

- Banner, interstitial, native, and rewarded video ad formats
- Network mediation through Google's platform
- Performance analytics and reporting
- Privacy compliance features

Minimum Supported Android Version
========

The Audienzz Android SDK requires a minimum Android version of **API 24 (Android 7.0, Nougat)** or higher.

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

You can check for latest version on maven [Audienzz SDK](https://central.sonatype.com/artifact/com.audienzz/sdk)

Getting started
=======

Initialize SDK
-------
First of all, SDK needs to be initialized with context. It's done asynchronously, so after callback
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
CompanyId is provided by Audienzz, usually - it is id of the company in ad console.
You can always check sdk initialization status by checking `isSdkInitialized` property.

Lazy Loading
-------
Sometimes application doesn't need to load an ad once the screen (activity/fragment)
is instantiated. Instead of that it might be more optimal to start loading when the ad is actually
presented to user.

It can be done in several ways, depending on ad type:

*   `audienzzAdViewHandler.load(withLazyLoading = true, ...)`
*   `view.lazyLoadAd(adHandler = audienzzInterstitialAdHandler, ...)`

In this way the `load()` or `fetchDemand()` will be postponed until the view is shown on the screen.

The `loadAd()` method, available on classes like `AudienzzAdViewHandler` and `AudienzzInterstitialAdHandler`, initiates the ad loading process.
When `lazyLoading` is enabled, the SDK intelligently delays this process until the ad view is about to become visible to the user,
optimizing resource usage and improving performance. 
It is done with 'ViewTreeObserver.OnPreDrawListener' which triggers ad loading when the view becomes visible. 

API Reference
========

This section provides a detailed reference for the public API of the Audienzz SDK.

### `AudienzzBannerAdUnit`

Ad unit used for loading banner ads.

**Properties:**

| Name               | Type                        | Description                                          |
|--------------------|-----------------------------|------------------------------------------------------|
| `pbAdSlot`         | `String?`                   | The ad slot for Prebid.                              |
| `gpid`             | `String?`                   | The Google Publisher ID.                             |
| `impOrtbConfig`    | `String?`                   | Imp object OpenRTB configuration for the impression. |
| `bannerParameters` | `AudienzzBannerParameters?` | Banner parameters                                    |
| `videoParameters`  | `AudienzzVideoParameters?`  | Video parameters                                     |

**Constructors:**

| Name                   | Parameters                                                                                      | Description                                                                                              |
|------------------------|-------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------|
| `AudienzzBannerAdUnit` | `configId: String`, `width: Int`, `height: Int`, `adUnitFormats: EnumSet<AudienzzAdUnitFormat>` | Creates a new `AudienzzBannerAdUnit` with specified adUnitFormats. ConfigId - refers to prebid config id |
| `AudienzzBannerAdUnit` | `configId: String`, `width: Int`, `height: Int`                                                 | Creates a new `AudienzzBannerAdUnit`. ConfigId - refers to prebid config id                              |


**Methods:**

| Name                     | Description                                                |
|--------------------------|------------------------------------------------------------|
| `setAutoRefreshInterval` | Sets the auto-refresh interval for the ad unit in seconds. |
| `resumeAutoRefresh`      | Resumes auto-refresh for the ad unit.                      |
| `stopAutoRefresh`        | Stops auto-refresh for the ad unit.                        |
| `destroy`                | Destroys the ad unit and releases resources.               |
| `fetchDemand`            | Fetches demand for the ad unit.                            |
| `addAdditionalSize`      | Adds an additional ad size to format                       |

### `AudienzzInterstitialAdUnit`

Ad unit used for loading interstitial ads.

**Properties:**

| Name               | Type                        | Description                                                 |
|--------------------|-----------------------------|-------------------------------------------------------------|
| `pbAdSlot`         | `String?`                   | The ad slot for Prebid.                                     |
| `gpid`             | `String?`                   | The Google Publisher ID.                                    |
| `impOrtbConfig`    | `String?`                   | Imp object OpenRTB configuration for the impression.        |
| `bannerParameters` | `AudienzzBannerParameters?` | Banner parameters                                           |
| `videoParameters`  | `AudienzzVideoParameters?`  | Video parameters                                            |
| `formats`          | `Set<AudienzzAdUnitFormat>` | The set of supported ad formats for ad unit (Banner, Video) |

**Constructors:**

| Name                         | Parameters                                                       | Description                                                                                                         |
|------------------------------|------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------|
| `AudienzzInterstitialAdUnit` | `configId: String, adUnitFormats: EnumSet<AudienzzAdUnitFormat>` | Creates a new `AudienzzInterstitialAdUnit` with specified adUnitFormats. ConfigId - refers to prebid config id      |
| `AudienzzInterstitialAdUnit` | `configId: String, minWidthPerc: Int, minHeightPerc: Int`        | Creates a new `AudienzzInterstitialAdUnit` with a minimum size in percentage. ConfigId - refers to prebid config id |
| `AudienzzInterstitialAdUnit` | `configId: String`                                               | Creates a new `AudienzzInterstitialAdUnit`. ConfigId - refers to prebid config id                                   |


**Methods:**

| Name                     | Description                                                |
|--------------------------|------------------------------------------------------------|
| `setAutoRefreshInterval` | Sets the auto-refresh interval for the ad unit in seconds. |
| `resumeAutoRefresh`      | Resumes auto-refresh for the ad unit.                      |
| `stopAutoRefresh`        | Stops auto-refresh for the ad unit.                        |
| `destroy`                | Destroys the ad unit and releases resources.               |
| `fetchDemand`            | Fetches demand for the ad unit.                            |
| `setMinSizePercentage`   | Sets ad size in percentage to format                       |

### `AudienzzBannerParameters`

This class is used to set banner-specific parameters.

**Constructors:**

| Name                       | Parameters | Description                               |
|----------------------------|------------|-------------------------------------------|
| `AudienzzBannerParameters` |            | Creates a new `AudienzzBannerParameters`. |

**Properties:**

| Name                              | Type                         | Description                                      |
|-----------------------------------|------------------------------|--------------------------------------------------|
| `api`                             | `List<AudienzzSignals.Api>?` | The list of supported API frameworks.            |
| `interstitialMinWidthPercentage`  | `Int?`                       | The minimum width percentage for interstitials.  |
| `interstitialMinHeightPercentage` | `Int?`                       | The minimum height percentage for interstitials. |
| `adSizes`                         | `Set<AudienzzAdSize>?`       | The set of ad sizes.                             |

### `AudienzzVideoParameters`

This class is used to configure video-specific parameters for an ad request.

**Constructors:**

| Name                      | Parameters            | Description                                                                  |
|---------------------------|-----------------------|------------------------------------------------------------------------------|
| `AudienzzVideoParameters` | `mimes: List<String>` | Creates a new `AudienzzVideoParameters` with a list of supported MIME types. |

**Properties:**

| Name             | Type                                    | Description                                                                    |
|------------------|-----------------------------------------|--------------------------------------------------------------------------------|
| `api`            | `List<AudienzzSignals.Api>?`            | The list of supported API frameworks.                                          |
| `maxBitrate`     | `Int?`                                  | The maximum bitrate in Kbps.                                                   |
| `minBitrate`     | `Int?`                                  | The minimum bitrate in Kbps.                                                   |
| `maxDuration`    | `Int?`                                  | The maximum video ad duration in seconds.                                      |
| `minDuration`    | `Int?`                                  | The minimum video ad duration in seconds.                                      |
| `mimes`          | `List<String>?`                         | The list of supported content MIME types.                                      |
| `playbackMethod` | `List<AudienzzSignals.PlaybackMethod>?` | The allowed playback methods.                                                  |
| `protocols`      | `List<AudienzzSignals.Protocols>?`      | The supported video bid response protocols.                                    |
| `startDelay`     | `AudienzzSignals.StartDelay?`           | The start delay in seconds for pre-roll, mid-roll, or post-roll ad placements. |
| `placement`      | `AudienzzSignals.Placement?`            | The placement type for the impression.                                         |
| `linearity`      | `Int?`                                  | The linearity of the ad.                                                       |
| `adSize`         | `AudienzzAdSize?`                       | The size of the ad.                                                            |

### `AudienzzPrebidMobile`

This object contains methods to initialize the SDK and configure global settings.

**Properties:**

| Name                                     | Type                                    | Description                                                                             |
|------------------------------------------|-----------------------------------------|-----------------------------------------------------------------------------------------|
| `AUTO_REFRESH_DELAY_MIN`                 | `Int`                                   | Read-only value - Minimum refresh interval allowed (30 seconds).                        |
| `AUTO_REFRESH_DELAY_MAX`                 | `Int`                                   | Read-only value - Maximum refresh interval allowed (120 seconds).                       |
| `SCHEME_HTTPS`                           | `String`                                | Read-only value - HTTPS scheme definition.                                              |
| `SCHEME_HTTP`                            | `String`                                | Read-only value - HTTP scheme definition.                                               |
| `SDK_VERSION`                            | `String`                                | Read-only value - The version of the SDK.                                               |
| `SDK_NAME`                               | `String`                                | Read-only value - The name of the SDK.                                                  |
| `MRAID_VERSION`                          | `String`                                | Read-only value - The MRAID version implemented.                                        |
| `NATIVE_VERSION`                         | `String`                                | Read-only value - The Native Ads version implemented.                                   |
| `OMSDK_VERSION`                          | `String`                                | Read-only value - The Open Measurement SDK version.                                     |
| `TESTED_GOOGLE_SDK_VERSION`              | `String`                                | Read-only value - The latest tested Google SDK version that is supported.               |
| `isUseCacheForReportingWithRenderingApi` | `Boolean`                               | Whether to use cache for reporting with the rendering API.                              |
| `timeoutMillis`                          | `Int`                                   | The timeout for bid requests in milliseconds.                                           |
| `prebidServerAccountId`                  | `String`                                | The Prebid server account ID.                                                           |
| `audienzzHost`                           | `AudienzzHost`                          | Enum which defines the currently used Audienzz host, by default - APPNEXUS.             |
| `isShareGeoLocation`                     | `Boolean`                               | Whether to share the user's geolocation.                                                |
| `externalUserIds`                        | `List<AudienzzExternalUserId>`          | A list of external user IDs.                                                            |
| `customerHeaders`                        | `Map<String, String>?`                  | Custom headers to be sent with requests.                                                |
| `storeAuctionResponse`                   | `String?`                               | The stored auction response.                                                            |
| `storedBidResponses`                     | `Map<String, String>`                   | The stored bid responses.                                                               |
| `isPbsDebug`                             | `Boolean`                               | Whether PBS debug mode is enabled.                                                      |
| `enabledAssignNativeAssetId`             | `Boolean`                               | Whether to assign a native asset ID.                                                    |
| `isSdkInitialized`                       | `Boolean`                               | `true` if the SDK is initialized.                                                       |
| `logLevel`                               | `AudienzzLogLevel`                      | The log level for the SDK.                                                              |
| `customLogger`                           | `AudienzzLogUtil.AudienzzPrebidLogger?` | A custom logger.                                                                        |
| `customStatusEndpoint`                   | `String?`                               | A custom status endpoint for the Prebid server.                                         |
| `isIncludeWinnersFlag`                   | `Boolean`                               | Whether to receive additional info about winners in response.                           |
| `isIncludeBidderKeysFlag`                | `Boolean`                               | Whether to receive additional info about bidders in response.                           |
| `pbsConfig`                              | `AudienzzPBSConfig?`                    | The PBS configuration.                                                                  |
| `createFactoryTimeout`                   | `Int`                                   | The creative factory timeout - time to parse and render banner ads (By default 6000ms). |

**Methods:**

| Name                                | Parameters                                                                                               | Description                                  |
|-------------------------------------|----------------------------------------------------------------------------------------------------------|----------------------------------------------|
| `initializeSdk`                     | `context: Context`, `companyId: String`, `sdkInitializationListener: AudienzzSdkInitializationListener?` | Initializes the SDK.                         |
| `addStoredBidResponse`              | `bidder: String`, `responseId: String`                                                                   | Adds a stored bid response.                  |
| `clearStoredBidResponses`           |                                                                                                          | Clears all stored bid responses.             |
| `checkGoogleMobileAdsCompatibility` | `googleAdsVersion: String`                                                                               | Checks compatibility with Google Mobile Ads. |
| `registerPluginRenderer`            | `prebidMobilePluginRenderer: AudienzzPrebidMobilePluginRenderer`                                         | Registers a plugin renderer.                 |
| `unregisterPluginRenderer`          | `prebidMobilePluginRenderer: AudienzzPrebidMobilePluginRenderer`                                         | Unregisters a plugin renderer.               |
| `containsPluginRenderer`            | `prebidMobilePluginRenderer: AudienzzPrebidMobilePluginRenderer`                                         | Checks if a plugin renderer is registered.   |


### `AudienzzAdViewHandler`

This class handles the loading of ads for a given `AdManagerAdView`.

**Constructors:**

| Name                    | Parameters                                          | Description                            |
|-------------------------|-----------------------------------------------------|----------------------------------------|
| `AudienzzAdViewHandler` | `adView: AdManagerAdView`, `adUnit: AudienzzAdUnit` | Creates a new `AudienzzAdViewHandler`. |

**Methods:**

| Name   | Parameters                                                                                                               | Description  |
|--------|--------------------------------------------------------------------------------------------------------------------------|--------------|
| `load` | `withLazyLoading: Boolean`, `request: AdManagerAdRequest`, `callback: (AdManagerAdRequest, AudienzzResultCode?) -> Unit` | Loads an ad. |               

### `AudienzzTargetingParams`

This object is used to set targeting parameters for ad requests.

**Properties:**

| Name                    | Type                       | Description                                                         |
|-------------------------|----------------------------|---------------------------------------------------------------------|
| `userLatLng`            | `Pair<Float, Float>?`      | The user's latitude and longitude.                                  |
| `userKeywords`          | `String?`                  | The user's keywords. (Added to the OpenRTB user object as keywords) |
| `keywordSet`            | `Set<String>`              | Used to retrieve current user keywords.                             |
| `publisherName`         | `String?`                  | The name of the publisher.                                          |
| `domain`                | `String`                   | The domain of the app.                                              |
| `storeUrl`              | `String`                   | The store URL of the app.                                           |
| `accessControlList`     | `Set<String>`              | The access control list.                                            |
| `omidPartnerName`       | `String?`                  | The OMID partner name.                                              |
| `omidPartnerVersion`    | `String?`                  | The OMID partner version.                                           |
| `isSubjectToCOPPA`      | `Boolean?`                 | Whether the user is subject to COPPA.                               |
| `isSubjectToGDPR`       | `Boolean?`                 | Whether the user is subject to GDPR.                                |
| `gdprConsentString`     | `String?`                  | The GDPR consent string.                                            |
| `purposeConsents`       | `String?`                  | The GDPR purpose consents.                                          |
| `bundleName`            | `String?`                  | The bundle name of the app.                                         |
| `extDataDictionary`     | `Map<String, Set<String>>` | The extended data dictionary.                                       |
| `isDeviceAccessConsent` | `Boolean?`                 | Whether device access is consented.                                 |
| `userExt`               | `AudienzzExt?`             | The user's extended data.                                           |

**Methods:**

| Name                                | Parameters                                       | Description                                    |
|-------------------------------------|--------------------------------------------------|------------------------------------------------|
| `addUserKeyword`                    | `keyword: String`                                | Adds a user keyword.                           |
| `addUserKeywords`                   | `keywords: Set<String>`                          | Adds a set of user keywords.                   |
| `removeUserKeyword`                 | `keyword: String`                                | Removes a user keyword.                        |
| `clearUserKeywords`                 |                                                  | Clears all user keywords.                      |
| `setExternalUserIds`                | `externalUserIds: List<AudienzzExternalUserId>?` | Sets the external user IDs.                    |
| `getExternalUserIds`                |                                                  | Gets the external user IDs.                    |
| `addExtData`                        | `key: String, value: String`                     | Adds extended data.                            |
| `updateExtData`                     | `key: String, value: Set<String>`                | Updates extended data.                         |
| `removeExtData`                     | `key: String`                                    | Removes extended data.                         |
| `clearExtData`                      |                                                  | Clears all extended data.                      |
| `addBidderToAccessControlList`      | `bidderName: String`                             | Adds a bidder to the access control list.      |
| `removeBidderFromAccessControlList` | `bidderName: String`                             | Removes a bidder from the access control list. |
| `clearAccessControlList`            |                                                  | Clears the access control list.                |
| `getPurposeConsent`                 | `index: Int`                                     | Gets the purpose consent for a given index.    |
| `getGlobalOrtbConfig`               |                                                  | Gets the global ORTB configuration.            |
| `setGlobalOrtbConfig`               | `ortbConfig: String`                             | Sets the global ORTB configuration.            |

Examples
========

### Banner Ad 
Here is minimum example of configuring and loading banner ad:

```kotlin
// Create a banner ad unit with a specified width and height (for example 300 width and 50 height)
val audienzzAdUnit = AudienzzBannerAdUnit(PREBID_CONFIG_ID, width, height)

// Create Google Ad Manager(GAM) ad view
val gamAdView = AdManagerAdView(context)

// Set GAM ad unit id path to the GAM ad view
gamAdView.adUnitId = GAM_AD_UNIT_ID_PATH

// Set ad unit size to the GAM ad view - same as size for ad unit (for example 300 width and 50 height)
gamAdView.setAdSizes(AdSize(width, height))

// Create banner parameters for AudienzzBannerAdUnit
val audienzzBannerParameters = AudienzzBannerParameters()

// Set api's of the banner parameters to MRAID_3 and OMID_1
audienzzBannerParameters.api = listOf(AudienzzSignals.Api.MRAID_3, AudienzzSignals.Api.OMID_1)

// Set parameters to the banner ad unit
audienzzAdUnit.bannerParameters = audienzzBannerParameters

// Create AudienzzAdViewHandler, provide to it gamAdView and audienzzAdUnit instances, then call load method to start loading the ad
AudienzzAdViewHandler(
    adView = gamAdView,
    adUnit = audienzzAdUnit,
).load(callback = { gamRequest, audienzzResultCode ->
    // Handle the result code of prebid bid request if necessary and then load ad with GAM request returned after prebid bid request
    gamAdView.loadAd(gamRequest)
})
```

### Interstitial Ad
Here is minimum example of configuring and loading interstitial ad:

```kotlin
// Create an interstitial ad unit with a minimum size in percentage (for example 80% width and 60% height)
val audienzzAdUnit = AudienzzInterstitialAdUnit(PREBID_CONFIG_ID, minWidthPercents, minHeightPercents)

// Create an interstitial ad handler, provide to it audienzzAdUnit instance and GAM_AD_UNIT_ID_PATH
val audienzzInterstitialAdHandler = AudienzzInterstitialAdHandler(audienzzAdUnit, GAM_AD_UNIT_ID_PATH)

// Here we use button as a trigger for loading ad by setting onClickListener to it 
button?.setOnClickListener {
    // Invoke load method on interstitial ad handler
    audienzzInterstitialAdHandler.load(
        // Set ad load listener to react to ad successfully loading or failing to load
        adLoadCallback = createAdLoadListener(),
        // Set a listener reacting to the result of prebid bid request 
        resultCallback = { audienzzResultCode, gamRequest, adLoadListener ->
            // Handle the result code of prebid bid request if necessary and then load ad with GAM request returned after prebid bid request
            AdManagerInterstitialAd.load(
                context,
                GAM_AD_UNIT_ID_PATH,
                gamRequest,
                adLoadListener,
            )
        },
    )
}

// Create a listener reacting to ad successfully loading or failing to load
private fun createAdLoadListener(): AdManagerInterstitialAdLoadCallback {
    return object : AdManagerInterstitialAdLoadCallback() {
        override fun onAdLoaded(interstitialAd: AdManagerInterstitialAd) {
            super.onAdLoaded(interstitialAd)
            // Show interstitial ad if it was successfully loaded
            (context as? AppCompatActivity)?.let { interstitialAd.show(it) }
        }

        override fun onAdFailedToLoad(loadAdError: LoadAdError) {
            super.onAdFailedToLoad(loadAdError)
            // Handle the error or log it if necessary
        }
    }
}
```

You can find more examples of practical implementation here:

[Examples](Example/TestApp/src/main/java/org/audienzz/mobile/testapp/adapter)

Troubleshooting
========

Scrollbar
--------
Sometimes there is a problem with the appearance of the scroll bar inside of banner ads.
To avoid this behavior - call `AudienzzAdViewUtils.hideScrollBar(adView: AdManagerAdView)` in `onAdLoaded()` method, like so:

```kotlin
override fun onAdLoaded() {
    super.onAdLoaded()
    AudienzzAdViewUtils.hideScrollBar(adView)
}
```

Unfilled ads
-----------
In order to handle unfilled ads it is advised to build your logic around `onAdFailedToLoad()` method.
There you receive `LoadAdError` object, which contains details about the error. When it has code:3 and message "No fill" - it is an unfilled ad.
Example of handling such cases is shown [here](Example/TestApp/src/main/java/org/audienzz/mobile/testapp/adapter/UnfilledAdHolder.kt)

License
========

    Copyright 2025 Audienzz AG.
    
    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at
    
       http://www.apache.org/licenses/LICENSE-2.0
    
    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.