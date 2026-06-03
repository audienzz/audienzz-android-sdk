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
AudienzzPrebidMobile.initializeSdk(applicationContext, COMPANY_ID, enablePpid = false) { status ->
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
It is done with `ViewTreeObserver.OnPreDrawListener` which triggers ad loading when the view is within range.

### Prefetch Margin

The correct prefetch mechanism depends on the scroll container your ad lives in:

| Container | Prefetch mechanism | How to configure |
|---|---|---|
| `ScrollView` / `NestedScrollView` | Distance-based (dp) | `prefetchMarginDp` on `load()` |
| `RecyclerView` | Item-count-based | `LinearLayoutManager.setInitialPrefetchItemCount(n)` |

**Why they differ:** In a `ScrollView` all views are laid out and attached to the view hierarchy upfront. The SDK's `ViewTreeObserver.OnPreDrawListener` can therefore detect "this view is now within Ndp of the visible area" at exactly the right scroll position and fire `fetchDemand` precisely N dp ahead.

In a `RecyclerView` views are created and bound on-demand — only just before an item scrolls into view (typically 1 item ahead). By the time `onBindViewHolder` runs and `load()` is called, the view is already within ~40 dp of the viewport regardless of the `prefetchMarginDp` value, so the margin fires immediately and has no practical effect.

#### ScrollView / NestedScrollView

Use `withLazyLoading = true` with `prefetchMarginDp` to control how far ahead loading starts:

```kotlin
// Default — start loading 200 dp before the view enters the viewport
AudienzzAdViewHandler(adView = gamAdView, adUnit = audienzzAdUnit)
    .load(withLazyLoading = true, callback = { request, _ -> gamAdView.loadAd(request) })

// Custom margin — start loading 600 dp ahead
AudienzzAdViewHandler(adView = gamAdView, adUnit = audienzzAdUnit)
    .load(withLazyLoading = true, prefetchMarginDp = 600, callback = { request, _ -> gamAdView.loadAd(request) })

// Exact visibility — load only when the view is actually on screen
AudienzzAdViewHandler(adView = gamAdView, adUnit = audienzzAdUnit)
    .load(withLazyLoading = true, prefetchMarginDp = 0, callback = { request, _ -> gamAdView.loadAd(request) })
```

#### RecyclerView

Use `withLazyLoading = false` to load immediately on bind, and control how many items ahead RecyclerView pre-binds with `setInitialPrefetchItemCount`:

```kotlin
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

// In your RecyclerView.Adapter
override fun onBindViewHolder(holder: AdViewHolder, position: Int) {
    AudienzzAdViewHandler(adView = holder.adView, adUnit = adUnit)
        .load(withLazyLoading = false, callback = { request, _ -> holder.adView.loadAd(request) })
}

// Increase how many items RecyclerView pre-binds ahead of the viewport (default is 2)
(recyclerView.layoutManager as? LinearLayoutManager)?.setInitialPrefetchItemCount(4)
```

Smart Refresh
-------
Smart Refresh makes banner auto-refresh viewport-aware: refresh is paused while the ad is off-screen, and resumes intelligently when it returns.

When the ad scrolls back into view the SDK checks how long it was hidden:
- **Stale** (hidden ≥ refresh interval) → a new ad is fetched immediately, then normal auto-refresh resumes.
- **Not stale** (hidden < refresh interval) → the remaining time is waited before the next fetch, then normal auto-refresh resumes.

Enable it by calling `enableSmartRefresh()` on the `AudienzzAdViewHandler` after calling `load()`:

```kotlin
// Set an auto-refresh interval — required for smart refresh to have any effect
audienzzAdUnit.setAutoRefreshInterval(30) // seconds (min 30, max 120)

val handler = AudienzzAdViewHandler(
    adView = gamAdView,
    adUnit = audienzzAdUnit,
)
handler.load(callback = { gamRequest, _ -> gamAdView.loadAd(gamRequest) })
handler.enableSmartRefresh()
```

When the fragment or activity is destroyed, disable smart refresh to remove the internal `ViewTreeObserver` listener and avoid memory leaks:

```kotlin
override fun onDestroyView() {
    super.onDestroyView()
    handler.disableSmartRefresh()
    audienzzAdUnit.destroy()
}
```

> **Note:** `enableSmartRefresh()` has no effect if no auto-refresh interval is set on the ad unit (i.e. `setAutoRefreshInterval()` was not called).

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
| `ppidManager`                            | `PpidManager`                           | Read-only value - variable throught which to interact with PpidManger class             |
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

| Name                                | Parameters                                                                                                                               | Description                                                                                                                                                                                                                                                                                   |
|-------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `initializeSdk`                     | `context: Context`, `companyId: String`, ` enablePpid: Boolean = false`, `sdkInitializationListener: AudienzzSdkInitializationListener?` | Initializes the SDK. When enablePPID is true - SDK will automatically generate unique identifier, store it in Shared Preferences and add it to all Google Ad Manager requests as a Publisher Provided identifier. On additional methods to work with PPID look at [PpidManager](#ppidmanager) |
| `addStoredBidResponse`              | `bidder: String`, `responseId: String`                                                                                                   | Adds a stored bid response.                                                                                                                                                                                                                                                                   |
| `clearStoredBidResponses`           |                                                                                                                                          | Clears all stored bid responses.                                                                                                                                                                                                                                                              |
| `checkGoogleMobileAdsCompatibility` | `googleAdsVersion: String`                                                                                                               | Checks compatibility with Google Mobile Ads.                                                                                                                                                                                                                                                  |
| `registerPluginRenderer`            | `prebidMobilePluginRenderer: AudienzzPrebidMobilePluginRenderer`                                                                         | Registers a plugin renderer.                                                                                                                                                                                                                                                                  |
| `unregisterPluginRenderer`          | `prebidMobilePluginRenderer: AudienzzPrebidMobilePluginRenderer`                                                                         | Unregisters a plugin renderer.                                                                                                                                                                                                                                                                |
| `containsPluginRenderer`            | `prebidMobilePluginRenderer: AudienzzPrebidMobilePluginRenderer`                                                                         | Checks if a plugin renderer is registered.                                                                                                                                                                                                                                                    |
| `setSchainObject`                   | `schain: String`                                                                                                                         | Method used to set Schain object for all ad requests. For example on usage refer to [AdsPageFragment](Example/TestApp/src/main/java/org/audienzz/mobile/testapp/view/AdsPageFragment.kt)                                                                                                      |

### `PpidManager`

Available through `AudienzzPrebidMobile.ppidManager` public variable.

**Methods:**

| Name                      | Parameters                        | Description                                                                                                                              |
|---------------------------|-----------------------------------|------------------------------------------------------------------------------------------------------------------------------------------|
| `isAutomaticPpidEnabled`  |                                   | Used to get current status of automatic PPID usage (if true - PPID is generated and used with all requests, if false - PPID is not used) |
| `setAutomaticPpidEnabled` | `isAutomaticPpidEnabled: Boolean` | Used to enable or disable automatic PPID usage                                                                                           |
| `getPpid`                 |                                   | Used to obtain current PPID if automaticPpid is enabled                                                                                  |

### `AudienzzAdViewHandler`

This class handles the loading of ads for a given `AdManagerAdView`.

**Constructors:**

| Name                    | Parameters                                          | Description                            |
|-------------------------|-----------------------------------------------------|----------------------------------------|
| `AudienzzAdViewHandler` | `adView: AdManagerAdView`, `adUnit: AudienzzAdUnit` | Creates a new `AudienzzAdViewHandler`. |

**Methods:**

| Name                  | Parameters                                                                                                                                                                    | Description                                                                                                                                 |
|-----------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------|
| `load`                | `withLazyLoading: Boolean`, `prefetchMarginDp: Int = 200`, `gamRequestBuilder: AdManagerAdRequest.Builder`, `callback: (AdManagerAdRequest, AudienzzResultCode?) -> Unit`     | Loads an ad. When `withLazyLoading` is true, loading starts `prefetchMarginDp` dp before the view enters the viewport (default 200 dp; pass 0 for exact-visibility behaviour). |
| `enableSmartRefresh`  |                                                                                                                                                                               | Enables viewport-aware smart refresh: pauses auto-refresh while off-screen and force-refreshes when the ad returns if the interval elapsed. |
| `disableSmartRefresh` |                                                                                                                                                                               | Disables smart refresh and removes the visibility listener.                                                                                 |

### `AudienzzStickyAdWrapperView`

A `FrameLayout` that reserves a block of vertical space in the layout and keeps the child ad view pinned within that space — sliding it via `translationY` as the user scrolls — so the ad stays visible for as long as possible before naturally scrolling off-screen.

**Constructor:**

| Name | Parameters | Description |
|---|---|---|
| `AudienzzStickyAdWrapperView` | `context: Context`, `attrs: AttributeSet? = null`, `defStyleAttr: Int = 0`, `maxHeightDp: Int = 600` | Creates a sticky wrapper. `maxHeightDp` is the vertical space reserved in the layout (default 600 dp). |

**Properties:**

| Name | Type | Default | Description |
|---|---|---|---|
| `maxHeight` | `Int` | converted from `maxHeightDp` | Height in pixels reserved in the layout. Settable at runtime; triggers `requestLayout()`. |
| `stickyTopOffset` | `Int?` | `null` | Y offset in pixels from the top of the scroll viewport where the ad sticks. `null` resolves to 0. |
| `isStickyEnabled` | `Boolean` | `true` | Enables or disables sticky positioning at runtime. When `false` the child stays at position 0. |
| `isVisibilityGateEnabled` | `Boolean` | `false` | When `true`, skips scroll calculations while the wrapper is more than one viewport height off-screen. |

**Methods:**

| Name | Parameters | Description |
|---|---|---|
| `setAdView(view: View)` | `view: View` | Sets the ad view to make sticky. Replaces any previously set view. |
| `attachToScrollView(scrollView: NestedScrollView)` | `scrollView: NestedScrollView` | Attaches sticky scroll tracking to a `NestedScrollView`. |
| `attachToScrollView(scrollView: ScrollView)` | `scrollView: ScrollView` | Attaches sticky scroll tracking to a standard `ScrollView`. |
| `detachFromScrollView()` | — | Removes all scroll listeners and stops position updates. Call in `onDestroyView()`. |

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

Remote Configuration Integration
========

The SDK supports a simplified integration using remote configuration. This allows you to manage ad units (GAM IDs, Prebid Config IDs, sizes, etc.) from the backend, requiring only a simple configuration ID in your app.

### Initialize SDK with Remote Configuration

Before using remote configuration ads, ensure the SDK is properly initialized:

```kotlin
// 1. Configure remote URL and publisher ID
RemoteConfigManager.initialize(
    publisherId = "YOUR_PUBLISHER_ID", // Will be provided for you
    remoteUrl = "https://dev-api.adnz.co/api/ws-sdk-config/public/v1/" // Audienzz remove config URL
)

// 2. Initialize SDK with remote configuration support
AudienzzPrebidMobile.initializeRemoteSdk(
    context = applicationContext,
    publisherId = "YOUR_PUBLISHER_ID",
    enablePpid = false
) { status ->
    if (status == AudienzzInitializationStatus.SUCCEEDED) {
        Log.d(TAG, "SDK was initialized successfully with remote config")
    } else {
        Log.e(TAG, "Error during SDK initialization: $status")
    }
}
```

### Banner Ad (Remote Config)

Use `AudienzzRemoteBannerView` to load a banner defined by a remote configuration ID.

```kotlin
// 1. Create the remote banner view with the configuration ID
val remoteBannerView = AudienzzRemoteBannerView(
    context = context,
    adConfigId = "YOUR_CONFIG_ID"
)

// 2. Add the view to your layout
containerLayout.addView(remoteBannerView)

// 3. Set an ad listener (optional)
remoteBannerView.setAdListener(object : AdListener() {
    override fun onAdLoaded() {
        Log.d(TAG, "Remote banner loaded successfully")
    }

    override fun onAdFailedToLoad(error: LoadAdError) {
        Log.e(TAG, "Remote banner failed to load: ${error.message}")
    }
})

// 4. Load the ad
remoteBannerView.loadAd()
```

#### Fixed Size Banner
To request a specific fixed size for your banner, you can set the layout parameters of the `AudienzzRemoteBannerView` before calling `loadAd()`. If the remote configuration contains matching sizes, they will be used:

```kotlin
remoteBannerView.layoutParams = FrameLayout.LayoutParams(320.dpToPx(), 50.dpToPx(), Gravity.CENTER)
remoteBannerView.loadAd()
```

#### Adaptive Banner
If adaptive banners are enabled in the remote configuration, the SDK handles the sizing automatically. By default, the `AudienzzRemoteBannerView` uses `MATCH_PARENT` for width and `WRAP_CONTENT` for height. The SDK will calculate the appropriate adaptive height based on the available width and the configuration fetched from the backend:

```kotlin
// Default behavior is adaptive if configured in the backend
remoteBannerView.loadAd()
```

**Lifecycle Management:**
Remember to handle lifecycle events properly:

```kotlin
override fun onResume() {
    super.onResume()
    remoteBannerView.onResume()
}

override fun onPause() {
    super.onPause()
    remoteBannerView.onPause()
}

override fun onDestroy() {
    super.onDestroy()
    remoteBannerView.destroy()
}
```

### Interstitial Ad (Remote Config)

Use `AudienzzRemoteConfigInterstitial` to load an interstitial defined by a remote configuration ID.

```kotlin
// 1. Create the remote interstitial with the configuration ID
val remoteInterstitial = AudienzzRemoteConfigInterstitial(
    context = context,
    adConfigId = "YOUR_CONFIG_ID"
)

// 2. Set a listener (optional)
remoteInterstitial.setListener(object : AudienzzRemoteConfigInterstitial.Listener {
    override fun onAdLoaded() {
        Log.d(TAG, "Remote interstitial loaded successfully")
        // Show the ad when ready
        remoteInterstitial.show()
    }

    override fun onAdFailedToLoad(error: String) {
        Log.e(TAG, "Remote interstitial failed to load: $error")
    }

    override fun onAdClosed() {
        Log.d(TAG, "Remote interstitial closed")
    }
})

// 3. Load the ad
remoteInterstitial.load()
```

Sticky Ad
========

`AudienzzStickyAdWrapperView` makes any ad view sticky within a scroll view. It reserves a fixed block of vertical space in the layout, and the ad view floats within that space — staying visible as the user scrolls past — before naturally scrolling off-screen once it reaches the edge of the reserved area.

### Layout

Place the wrapper's container inside your `NestedScrollView` (or `ScrollView`) at the position where the ad should appear. The wrapper will reserve exactly `maxHeightDp` pixels of vertical space:

```xml
<androidx.core.widget.NestedScrollView
    android:id="@+id/scrollView"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <LinearLayout
        android:orientation="vertical"
        android:layout_width="match_parent"
        android:layout_height="wrap_content">

        <!-- content above the ad -->

        <FrameLayout
            android:id="@+id/stickyContainer"
            android:layout_width="match_parent"
            android:layout_height="wrap_content" />

        <!-- content that scrolls past the sticky ad -->

    </LinearLayout>

</androidx.core.widget.NestedScrollView>
```

### Code (Remote Config banner)

```kotlin
private lateinit var sticky: AudienzzStickyAdWrapperView
private lateinit var banner: AudienzzRemoteBannerView

private fun loadStickyAd() {
    // 1. Create the ad view
    banner = AudienzzRemoteBannerView(requireContext(), "YOUR_CONFIG_ID")

    // Optional: listen to ad events via the underlying banner view
    banner.setAdListener(object : AdListener() {
        override fun onAdLoaded() { /* ad ready */ }
        override fun onAdFailedToLoad(error: LoadAdError) { /* handle error */ }
    })

    // 2. Create the sticky wrapper
    sticky = AudienzzStickyAdWrapperView(
        context = requireContext(),
        maxHeightDp = 300,  // vertical space reserved in the layout
    ).apply {
        setAdView(banner)
    }

    // 3. Add it to the container
    binding.stickyContainer.addView(
        sticky,
        FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
        ),
    )

    // 4. Attach to the scroll view and load
    sticky.attachToScrollView(binding.scrollView)
    banner.loadAd()
}
```

### Code (manual banner)

The wrapper is not limited to remote config ads — it works with any view:

```kotlin
val gamAdView = AdManagerAdView(context).apply {
    adUnitId = GAM_AD_UNIT_ID
    setAdSizes(AdSize(320, 50))
}

val audienzzAdUnit = AudienzzBannerAdUnit(PREBID_CONFIG_ID, 320, 50)

val sticky = AudienzzStickyAdWrapperView(context, maxHeightDp = 150).apply {
    setAdView(gamAdView)
}

binding.stickyContainer.addView(sticky, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
sticky.attachToScrollView(binding.scrollView)

AudienzzAdViewHandler(adView = gamAdView, adUnit = audienzzAdUnit)
    .load(callback = { request, _ -> gamAdView.loadAd(request) })
```

### Cleanup

Always detach the wrapper and destroy the ad view when the fragment or activity is destroyed to avoid memory leaks:

```kotlin
override fun onDestroyView() {
    sticky.detachFromScrollView()
    banner.destroy()
    super.onDestroyView()
}
```

### Configuration options

| Property | Type | Default | Description |
|---|---|---|---|
| `maxHeightDp` | `Int` (constructor) | `600` | Vertical space reserved in the layout in dp. |
| `maxHeight` | `Int` (property) | converted from `maxHeightDp` | Same as above but in pixels; settable at runtime. |
| `stickyTopOffset` | `Int?` | `null` (= 0) | Y offset in pixels from the top of the viewport where the ad sticks. Use this to account for a toolbar or status bar. |
| `isStickyEnabled` | `Boolean` | `true` | Disable sticky positioning at runtime without removing the view. |
| `isVisibilityGateEnabled` | `Boolean` | `false` | Skip position calculations when the wrapper is more than one screen-height away from the viewport. Enable for pages with many ads. |

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