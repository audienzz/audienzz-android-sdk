Audienzz Android SDK
========

## Quick integration (remote config + `pageImpression`)

Initialize once, report navigation, and keep one owner per placement. Remote configuration controls
banner sizes, lazy loading and refresh; you do not need your own refresh timers.

### 1. Install

```gradle
repositories { mavenCentral() }

dependencies {
  implementation 'com.audienzz:sdk:{latest_version}'
}
```

Find the release on [Maven Central](https://central.sonatype.com/artifact/com.audienzz/sdk). Add your
GAM/AdMob app ID to `AndroidManifest.xml` as `com.google.android.gms.ads.APPLICATION_ID`.
In GAM, leave each banner ad unit's **refresh rate unset**; Audienzz owns refresh.

### 2. Initialize once, from app startup

Run your CMP and forward its result through `AudienzzTargetingParams` before initializing or
creating ads — see [Consent](#consent). Use one app-wide startup flow shared by every entry route,
not separate initialization in each ad screen. Call this on the main thread with the application
context after consent is resolved:

```kotlin
AudienzzPrebidMobile.initializeRemoteSdk(applicationContext, "YOUR_PUBLISHER_ID") { status ->
    if (status == AudienzzInitializationStatus.FAILED) {
        // Keep app content available; show an error/retry option for ads.
    } else {
        // Ads can be enabled. Report the current page (step 3) before loading its ads.
        // Server-status warnings still permit Google fallback.
    }
}
```

Use publisher and placement IDs from the production configuration supplied by Audienzz.
`initializeRemoteSdk` configures the remote manager itself; no separate manager setup is needed.

### 3. Report navigation, before loading the page's ads

```kotlin
// Your navigation callback, including the initial destination:
AudienzzPrebidMobile.pageImpression(destinationScreen) // Activity, Fragment, or route-instance key
```

The report releases the previous page's banners and gives this visit its analytics identity.
Recommended startup order: **initialization → page report → ad creation/load**. Report only the
visible destination, not screens that your navigator pre-creates off screen.

| Situation | Report a new page? |
|---|---|
| First visible screen, navigation, tab change, back, or a new article | **Yes**, including destinations with no ads; report before loading their ads |
| App background → foreground | **No** — native recovery refreshes the active page's banners |
| SDK interstitial dismissal | **No** — native recovery handles it |
| Layout, render/rebuild, scrolling, or an ad callback | **No** |

A manual `pageImpression` call always starts a new visit, even for the same screen key.
Automatic recovery keeps `page_impression_id`, `au_page_seq` and `au_slot`; replacement requests
advance `hb_refresh_count`. Visibility and publisher pauses still apply. Interstitial events keep
the page captured at prefetch. Actual navigation during an interstitial still needs a page report.

Do not report unconditionally from `onResume`: it also runs after app and interstitial returns.
The automatic return behavior described here requires Android SDK `0.3.2` or newer.

### 4. Place a remote banner

```kotlin
// Keep this as a screen/view property. Use the host Activity/Fragment's context.
val banner = AudienzzRemoteBannerView(context, adConfigId = "YOUR_BANNER_CONFIG_ID")
container.addView(banner)
banner.loadAd() // after step 3; do not repeat from layout or scroll callbacks
```

Give the container a real width and reserve the expected height while loading. Adaptive sizing
comes from the backend. Lazy loading defaults to `true` with a `200dp` prefetch margin; both are
backend-controlled.

If multiple destinations share one native host (Compose/custom navigation), call
`banner.setScreen(routeInstanceKey)` **before `loadAd()`**, using the same key reported in step 3.
Use a distinct key for each route instance, not just an article route template. See
[Compose ownership](#jetpack-compose).

Call `banner.destroy()` when its view is permanently discarded (`onDestroyView` for a Fragment,
`onDestroy` for an Activity, or Compose `onRelease`). Temporary detachment/backgrounding is handled
by the SDK. For a custom overlay, use `setHostCover(true)` and clear it when the overlay closes.

### 5. Show an interstitial

Retain one owner per placement in a field. Construct it with the host Activity's context:

```kotlin
val interstitial = AudienzzRemoteConfigInterstitial(activity, "YOUR_INTERSTITIAL_CONFIG_ID")

// At a prefetch opportunity: cache one ad, never present.
interstitial.prefetch()

// Later, at a display opportunity: show now if ready, otherwise skip.
val submitted = interstitial.show(activity, eligible = canShowAd)

// Alternative flow: request and present as soon as ready.
if (canShowAd) interstitial.prefetchAndShow()
```

These are separate event-handler actions, not three calls to run together. `canShowAd` is your
frequency-cap and screen-policy decision. Use the owner's `Events` callbacks for load/show errors;
`show()` returning `true` means presentation was submitted, not an impression guarantee. Repeated
prefetches share an outstanding load or retain the ready ad. Keep the owner through dismissal and
call `destroy()` when finished. See [interstitial details](#interstitial-ad-remote-config).

---

## Overview

A mobile advertising SDK that combines header bidding capabilities from Prebid Mobile with Google's advertising ecosystem through a unified interface.
The implementation includes lazy loading functionality to optimize application performance by deferring ad initialization until needed.

> ### ⚠️ Important
>
> - **You report every screen.** Call `AudienzzPrebidMobile.pageImpression(...)` on every screen, dialog or popup that can show an ad — including ad-free destinations, because reporting those is what releases the previous screen's banners. There is no automatic tracking: it was removed so that every platform behaves the same way, and so that a screen the SDK cannot see (Jetpack Compose, a custom navigation model) is not a special case. See [Screen reporting](#step-2--screen-reporting).
> - **Page ownership always applies.** Navigation pauses/releases the previous page’s banners regardless of Smart Refresh v2. The opt-in `smartRefreshV2` flag (or `AudienzzPrebidMobile.smartRefreshV2Override`) selects the stricter directional viewport gate only. See [Smart Refresh](#smart-refresh).

## How screens & ads work (read this first)

The SDK is **screen-aware**: it knows which screen is active and which ads belong to it, and drives
each ad's lifecycle (page impressions + smart refresh) for you. Understanding this model is the key
to integrating correctly.

- **A screen** is whatever you report: an `Activity`, a `Fragment`, a ViewPager2 tab, a Compose
  destination, a dialog. You tell the SDK when one becomes current with
  `AudienzzPrebidMobile.pageImpression(...)` — see [Screen reporting](#step-2--screen-reporting).
- **An ad belongs to the screen it is placed in.** The SDK resolves each banner's host from the
  view hierarchy — `FragmentManager.findFragment(adView)`, falling back to its `Activity` — and
  matches by **object identity**, so two tabs, or two instances of the same screen class, are
  distinct screens. The host is pinned once resolved, so the association never drifts. When the
  hierarchy cannot distinguish your screens (Compose, or several screens in one Activity), tag each
  banner with the same key you report: `banner.setScreen("home")`.
- **Lifecycle:** when a screen becomes active, its banners (re)load; when you leave it, they pause;
  returning reloads them, independently of Smart Refresh v2. This stops off-screen slots from auctioning and
  gives each visit a fresh, viewable ad.
- **Report ad-free destinations too.** A settings screen with no ads still has to be reported —
  that report is what releases the banners of the screen the reader just left. Skipping it leaves
  them auctioning for a screen nobody is looking at.

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

Consent
-------
The SDK does **not** gate itself on user consent — that's the app's responsibility.
Run your CMP (consent) flow and forward the result **before** you initialize the
SDK or load any ads:

1. Show your CMP and obtain the user's choice.
2. Forward the consent signals (GDPR subject, TCF consent string, purpose
   consents) via `AudienzzTargetingParams`.
3. **Then** call `AudienzzPrebidMobile.initializeSdk(...)` and load ads.

Initializing or loading ads before consent will request ads without the consent
signals.

Initialize SDK
-------
Initialize the SDK with a context. The asynchronous callback returns `SUCCEEDED`,
`SERVER_STATUS_WARNING`, or `FAILED`. Both success and warning allow Original API / remote
Google ads to load. A warning can mean Prebid is unavailable: Google demand continues without
header bidding. Only `FAILED` should block ad creation.

```kotlin
AudienzzPrebidMobile.initializeSdk(applicationContext, COMPANY_ID) { status ->
    if (status != AudienzzInitializationStatus.FAILED) {
        Log.d(App.TAG, "SDK ready: $status — ${status.description}")
    } else {
        Log.e(App.TAG, "Error during SDK initialization: $status")
    }
}
```
CompanyId is provided by Audienzz, usually - it is id of the company in ad console.
`isSdkInitialized` reports **Prebid** readiness; it can remain false during Google-only fallback.
Use the initialization callback above to decide whether to create Original API / remote ads.
After an initialization failure in Prebid, retry SDK initialization to restore header bidding.
For individual auctions, Prebid errors or a missing callback hand off to Google once; a missing
callback is bounded by the configured Prebid timeout plus 250 ms. Page/background/destroy guards
still apply. This does not bypass unavailable placement configuration, Google errors, or TLS validation.

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

In a `RecyclerView` views are created and bound on-demand — only just before an item scrolls into view (typically 1 item ahead). By the time `onBindViewHolder` runs and `load()` is called, the view is already within ~40 dp of the viewport.

**More precisely, the margin saturates rather than stops working.** Raising it above the bind distance changes nothing — the lead time is capped by when `RecyclerView` binds the holder, so 200, 600 and 2000 dp behave identically. Lowering it still works: `prefetchMarginDp = 0` inside a `RecyclerView` does exactly what it says, and suppresses auctions for items the reader binds but never scrolls to.

Lazy and eager loading **converge** in a `RecyclerView`, but they are not equivalent. They coincide only when the holder is bound inside the margin *and* the ad is eligible at that moment. They diverge when:

- the margin is `0` or smaller than the bind distance — lazy then waits and eager does not;
- `setInitialPrefetchItemCount` is raised, which can bind an item outside the margin;
- the slot is not yet eligible when the item appears — lazy re-evaluates, eager has already requested.

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

To start the auction earlier in a `RecyclerView`, the main lever is making the holder bind earlier — `setInitialPrefetchItemCount`. `withLazyLoading = false` additionally removes the viewport condition entirely, which matters when the item is bound outside the margin or is not yet eligible:

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

#### React Native and other cross-platform hosts

The saturation above applies to **native** `RecyclerView` only. React Native's `FlatList` is JS-level windowing over a `ReactScrollView` — not a `RecyclerView` — so the ad view is attached well ahead of the viewport and the distance-based margin applies normally. There, the margin is the effective lever, and the 200 dp default is usually what binds.

If raising it does not move the auction earlier, the ad component is not mounting early enough: raise the list's `windowSize` / `initialNumToRender` rather than the margin. Saturation does return if the list recycles native views (e.g. FlashList) or if `removeClippedSubviews` is enabled, which it is by default on Android.

#### Remote-config banners

`AudienzzRemoteBannerView` takes both delivery settings from the ad config only — **ad config → SDK default**. There is no app-side override: a placement behaves the same in every app and on every platform, and is tuned in the backend.

| Setting | Ad config field (`config`) | Default |
|---|---|---|
| Lazy loading | `lazyLoad` | `true` — the auction waits for the viewport |
| Prefetch margin | `prefetchDistanceDp` | `200` dp |

> **Default is lazy.** A remote-config banner waits until the slot comes within the prefetch margin. This is deliberate: a publisher who builds several below-fold placements on entering an article would otherwise buy fills the reader may never approach, and an unrendered fill cannot become an impression. Set `lazyLoad: false` on the ad config for slots that are always on screen.

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

### Smart Refresh v2 (screen-aware) — opt-in

Smart Refresh v2 selects a stricter viewport gate. It is **off by default**; when disabled, the
classic viewport gate applies. Page ownership and navigation recovery apply in both modes.

**Directional visibility gate.** A refresh runs only while the ad's **top edge is fully on screen** and **at least 50% of the ad is visible**. It pauses the moment the top scrolls off (even 1px) or more than half the ad drops below the fold — a stricter, less "wasteful" rule than a plain visible-percentage threshold. The **initial load is unaffected** (the ad still loads as early as possible via lazy/prefetch).

**Page-aware pause & reload applies in both modes.** Report the destination with `pageImpression`
before loading its ads. Leaving releases the previous page's banners; returning starts a new visit
and reloads eligible slots. App foreground and SDK interstitial dismissal instead recover the
current page without a new analytics visit, as described in the quick guide.

Optionally, set `AudienzzPrebidMobile.blankOnScreenReload = true` to clear the slot (keeping its size, so no layout shift) while a screen-change reload is in progress. The slot is blanked as soon as the page is left, so returning to it never shows the previous screen's creative — you see an empty slot until the fresh ad renders, rather than the old ad followed by a blank. If no replacement auction can start, the previous creative is left in place rather than leaving the slot empty with nothing on the way. Default is off.

Enable it per publisher from the backend remote config (`smartRefreshV2: true` on the publisher config), or locally in the app (the local override wins):

```kotlin
// Select the v2 viewport gate regardless of the backend flag.
AudienzzPrebidMobile.smartRefreshV2Override = true
```

> **Note:** v2 still requires `enableSmartRefresh()` and an auto-refresh interval on each banner — the flag switches *which* refresh model runs, not whether refresh is enabled.

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

**Constructors:**

| Name                         | Parameters                                                       | Description                                                                                                         |
|------------------------------|------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------|
| `AudienzzInterstitialAdUnit` | `configId: String, minWidthPerc: Int, minHeightPerc: Int`        | Creates a new `AudienzzInterstitialAdUnit` with a minimum size in percentage. ConfigId - refers to prebid config id |
| `AudienzzInterstitialAdUnit` | `configId: String`                                               | Creates a new `AudienzzInterstitialAdUnit`. ConfigId - refers to prebid config id                                   |

An interstitial's formats and API frameworks are not arguments: they are backend-controlled (`prebidConfig.format` / `prebidConfig.apis`), and a hand-built `AudienzzInterstitialAdUnit` requests banner + video with MRAID 1/2/3 + OMID 1. The `api` of its `bannerParameters` / `videoParameters` is ignored. See [docs/interstitial-capabilities.md](docs/interstitial-capabilities.md).


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

| Name                                | Parameters                                                                                                                                                                          | Description                                                                                                                                                                                                                                                                                   |
|-------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `initializeSdk`                     | `context: Context`, `companyId: String`, `appVolume: Float = 0f`, `sdkInitializationListener: AudienzzSdkInitializationListener?`                   | Initializes the SDK. A Publisher Provided Identifier is generated, persisted and attached to every Google Ad Manager request automatically — see [PpidManager](#ppidmanager) to supply your own instead. |
| `initializeRemoteSdk`               | `context: Context`, `publisherId: String`, `sdkInitializationListener: AudienzzSdkInitializationListener?`                                           | Initializes the SDK with remote configuration support, fetching ad unit configs from the Audienzz backend using the publisher ID.                                                                                                                                                             |
| `pageImpression`                    | `screen: Any, name: String? = null` — or `name: String`                                                                                                                             | Call whenever a screen becomes current (each navigation to an Activity/Fragment, Compose destination or dialog; not app resume), **including ad-free destinations**. Fires a `pageImpression` analytics event and generates a new page impression ID shared by all ad events on that screen visit. Replaces the removed `onScreenResumed`. See [Screen reporting](#step-2--screen-reporting). |
| `getAdUnitConfig`                   | `configId: String`, `callback: (RemoteAdUnitConfig?) -> Unit`                                                                                                                       | Fetches a remote ad unit configuration by its ID. The SDK must have been initialized via `initializeRemoteSdk` first.                                                                                                                                                                         |
| `setAppVolume`                      | `volume: Float`                                                                                                                                                                     | Sets the global app volume for Google Mobile Ads ad audio. Range: 0.0 (muted) – 1.0 (full device volume). Can be called at any time after SDK initialization.                                                                                                                                 |
| `addStoredBidResponse`              | `bidder: String`, `responseId: String`                                                                                                                                              | Adds a stored bid response.                                                                                                                                                                                                                                                                   |
| `clearStoredBidResponses`           |                                                                                                                                                                                     | Clears all stored bid responses.                                                                                                                                                                                                                                                              |
| `checkGoogleMobileAdsCompatibility` | `googleAdsVersion: String`                                                                                                                                                          | Checks compatibility with Google Mobile Ads.                                                                                                                                                                                                                                                  |
| `registerPluginRenderer`            | `prebidMobilePluginRenderer: AudienzzPrebidMobilePluginRenderer`                                                                                                                    | Registers a plugin renderer.                                                                                                                                                                                                                                                                  |
| `unregisterPluginRenderer`          | `prebidMobilePluginRenderer: AudienzzPrebidMobilePluginRenderer`                                                                                                                    | Unregisters a plugin renderer.                                                                                                                                                                                                                                                                |
| `containsPluginRenderer`            | `prebidMobilePluginRenderer: AudienzzPrebidMobilePluginRenderer`                                                                                                                    | Checks if a plugin renderer is registered.                                                                                                                                                                                                                                                    |
| `setSchainObject`                   | `schain: String`                                                                                                                                                                    | Method used to set Schain object for all ad requests. For example on usage refer to [AdsPageFragment](Example/TestApp/src/main/java/org/audienzz/mobile/testapp/view/AdsPageFragment.kt)                                                                                                      |

### `PpidManager`

Available through `AudienzzPrebidMobile.ppidManager` public variable.

**Methods:**

| Name                      | Parameters                        | Description                                                                                                                              |
|---------------------------|-----------------------------------|------------------------------------------------------------------------------------------------------------------------------------------|
| `setPublisherPpid`        | `ppid: String?`                   | Supply your own PPID (e.g. a hashed e-mail). Takes precedence over the SDK-generated one; pass `null` to clear and fall back to it.       |
| `getPpid`                 |                                   | The PPID currently being sent: yours if set, otherwise the SDK-generated UUID. `null` only when consent is missing.                       |

A PPID is **always** sent with ad requests — the SDK generates one (a UUID,
persisted locally and rotated every 12 months) whenever you haven't supplied
your own. There is no enable/disable switch in the SDK: a missing PPID costs
frequency capping and cross-session targeting. One backend switch suppresses it:

| Publisher config field | Effect when `false` | Absent |
|---|---|---|
| `ppidEnabled` | No PPID is sent at all, including one you supplied | Enabled |

### `AudienzzAdViewHandler`

This class handles the loading of ads for a given `AdManagerAdView`.

**Constructors:**

| Name                    | Parameters                                          | Description                            |
|-------------------------|-----------------------------------------------------|----------------------------------------|
| `AudienzzAdViewHandler` | `adView: AdManagerAdView`, `adUnit: AudienzzAdUnit` | Creates a new `AudienzzAdViewHandler`. |

**Methods:**

| Name                  | Parameters                                                                                                                                                                    | Description                                                                                                                                 |
|-----------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------|
| `load`                | `withLazyLoading: Boolean`, `prefetchMarginDp: Int = 200`, `gamRequestBuilder: AdManagerAdRequest.Builder`, `callback: (AdManagerAdRequest, AudienzzResultCode?) -> Unit`     | Loads an ad. When `withLazyLoading` is true, loading starts `prefetchMarginDp` dp before the view enters the viewport (default 200 dp; pass 0 for exact-visibility behaviour). Inside a `RecyclerView` the margin saturates — raising it has no effect, lowering it still does. See [Prefetch Margin](#prefetch-margin). |
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

Follow [quick integration steps 1–3](#quick-integration-remote-config--pageimpression): initialize
once after consent, report the current destination, then load its ads.

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

> **Jetpack Compose:** when this banner lives on a composable destination (not an Activity/Fragment),
> tag it with the screen's route key — `remoteBannerView.setScreen("home")` — and report that same
> key with `AudienzzPrebidMobile.pageImpression("home")`. See [Jetpack Compose](#jetpack-compose).

#### Fixed Size Banner
The remote configuration defines requested creative sizes. Layout parameters reserve space; they
do not override the backend request sizes. For a placement configured as 320×50, for example:

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

**Lifecycle management:** native background/foreground handling is automatic. Keep the owner
while its screen can return, and destroy it when its view is permanently removed. In an Activity:

```kotlin
override fun onDestroy() {
    remoteBannerView.destroy()
    super.onDestroy()
}
```

In a Fragment, destroy in `onDestroyView`; in Compose, use `AndroidView.onRelease`. Do not destroy
on a temporary detach or add manual refreshes to `onResume`.

### Interstitial Ad (Remote Config)

Use `AudienzzRemoteConfigInterstitial` to load an interstitial defined by a remote configuration ID.

Three verbs, and the verb decides whether anything is presented:

| Method | What it does |
| --- | --- |
| `prefetch()` | Obtains and retains one ad. Never presents. |
| `show(activity, eligible)` | Presents ready inventory at this opportunity, or reports why it could not. Never schedules a presentation for later. |
| `prefetchAndShow()` | Presents when the load completes, or presents inventory already in hand. |

```kotlin
// Retain one owner per placement outside transient page views.
val interstitial = AudienzzRemoteConfigInterstitial(context, "YOUR_CONFIG_ID")
interstitial.prefetch() // Never presents; repeated calls preserve ready inventory.

// At a later eligible transition, after evaluating the publisher's frequency cap:
val submitted = interstitial.show(activity, eligible = publisherAllowsAd)
// false: skip this opportunity. A later load completion will not display it.
// true: submitted to Google; Events.onOpened/onFailedToShow report the outcome.
// Destroy when this owner is no longer needed; presentation cleanup is deferred.
```

If you want the ad shown as soon as it arrives, ask for that by name:

```kotlin
interstitial.prefetchAndShow()
```

#### Migrating from `loadAd` / `preload` / `showAtOpportunity`

`loadAd()` is **removed**. It loaded *and* presented, which a method named "load" should not
decide — reading the call told you nothing about whether the reader would be interrupted.

| Before | Now |
| --- | --- |
| `loadAd()` | `prefetchAndShow()` |
| `preload()` | `prefetch()` |
| `showAtOpportunity(activity, eligible)` | `show(activity, eligible)` — `eligible` now defaults to `true` |

Repeated prefetches for one owner coalesce onto the request in flight and reuse valid ready
inventory, so a second call costs nothing; repeated presentation calls cannot show twice.

Analytics
========

The SDK reports ad-performance analytics to the Audienzz backend automatically. **All ad-level
events fire on their own** once the SDK is initialized — you do not wire up bid, impression, click,
or viewability tracking yourself. Report each navigation visit, including ad-free destinations
(see [Screen reporting](#step-2--screen-reporting)).

### What gets collected

| Event | When it fires |
|---|---|
| `pageImpression` | A navigation visit is reported explicitly, including ad-free screens; app resume is not a new visit |
| `bidRequest` | A Prebid bid request is sent for a slot (also on each auto-refresh) |
| `bidResponse` | Prebid returns a result |
| `bidWon` | A Prebid bid wins — carries `cpm`, `currency`, `creative_id`, `auction_id`, `ad_id`, `bidder_code` |
| `noBid` | The auction returned no usable bid |
| `adImpression` | The ad is rendered on screen — carries `bidder_code` (the demand that rendered) |
| `adClick` | The user taps the ad |
| `viewability.start` | The ad becomes ≥ 50 % visible |
| `viewability.success` | The ad stays ≥ 50 % visible for 1 continuous second |

Banner, interstitial and rewarded ads on the Original API are all covered.

### Step 1 — Initialize the SDK

Analytics uses the **publisher ID** from remote configuration as `publisher_id`; the collector
resolves company and website IDs. Direct integrations can supply the publisher through
`configureAnalytics`. See [the analytics contract](docs/analytics-contract.md).

### Step 2 — Screen reporting

**You report every screen.** Call `pageImpression` when a screen becomes current. Each call fires a
`pageImpression` analytics event with a fresh page-impression id that tags every ad event of that
visit. It also drives page ownership in **both** refresh modes: entering a screen reloads its
banners and leaving releases them. Smart Refresh v2 only changes viewport eligibility.

Two forms. Pass the screen object and the name is derived from it, or pass a name of your own:

```kotlin
// Your navigation callback reports the destination (Activity/Fragment/Dialog).
// A bare onResume also fires on app return, so do not report from it unconditionally.
AudienzzPrebidMobile.pageImpression(destinationScreen)
// A screen with no object to point at (a Compose destination, a custom router)
AudienzzPrebidMobile.pageImpression("home")
// An object, but your own analytics name for it
AudienzzPrebidMobile.pageImpression(this, name = "article/detail")
```

**Call it for ad-free destinations too.** A settings screen that carries no ads still ends the
previous screen's visit; without that report the banners you just navigated away from keep
auctioning.

**There is no automatic screen tracking.** The Activity/Fragment lifecycle observer that used to do
this was removed: it could not see Compose destinations or custom routers, so those were a separate
integration anyway, and having two mechanisms meant a screen could be counted twice or not at all.
Every platform now behaves identically — the app always reports.

> **Migrating.** `AudienzzPrebidMobile.onScreenResumed(...)` is now `pageImpression(...)` with the
> same arguments, and `AudienzzPrebidMobile.autoScreenTracking` is removed. If you relied on
> automatic tracking, add a `pageImpression` call to each screen; nothing reports itself any more.

Notes:
- Report an app-owned dialog or bottom sheet treated as a navigation destination, and report the
  screen underneath when returning. SDK interstitial dismissal is automatic; do not report it.
- There is **no `onPause`/teardown counterpart**. If no screen is ever reported, ad events still
  send with a fallback page-impression id; they just aren't tied to a named screen.

### Jetpack Compose

Compose destinations live inside a single `Activity` and have no Fragment, so the view hierarchy
can't tell them apart — every composable screen would resolve to the same host. Wire them up with
two calls that share **one route key**:

1. **Report the screen** on entry with `pageImpression(routeKey)` — fires the `pageImpression` and
   drives the screen-aware pause/reload.
2. **Tag the banner** with the *same* key via `banner.setScreen(routeKey)` so the SDK knows which
   screen that ad belongs to. Without this the banner would resolve to the Activity and never match
   a route key (it would pause on the first navigation and not reload).

Use a stable key **per back-stack entry** (for example `NavBackStackEntry.id`), not a route template
such as `article/{id}` shared by multiple visits. Keys match by value.

Report from your navigation owner when the active entry changes, including back navigation and
ad-free destinations. **Finish that report before composing/loading the destination's ads.** A
`LaunchedEffect` beside an unconditionally created `AndroidView` does not guarantee this order;
gate ad composition until the report has run. Do not repeat the report on recomposition or resume.

After the navigation owner has reported `routeInstanceKey`, the banner component only needs:

```kotlin
@Composable
fun ArticleBanner(routeInstanceKey: String) {
    key(routeInstanceKey) {
        AndroidView(
            factory = { context ->
                AudienzzRemoteBannerView(context, adConfigId = "YOUR_CONFIG_ID").apply {
                    setScreen(routeInstanceKey)
                    loadAd()
                }
            },
            onRelease = { banner -> banner.destroy() },
        )
    }
}
```

The matching `setScreen` is required for page ownership even with Smart Refresh v2 disabled.
Report only the active destination; an off-screen pre-created tab must not claim the current page.

### Demand-source attribution (`bidder_code`) — optional GAM setup

`adImpression` reports `bidder_code` = the demand that actually rendered. To distinguish a winning
**Prebid** line item from **Google/ad-server** demand, the SDK listens for a GAM **app event named
`Prebid`**. For this to be accurate, your **GAM Prebid line item must be configured to send an app
event with the key `Prebid`** (an ad-ops / Google Ad Manager setup — no code on your side). Without
it, rendered ads are attributed to the ad server (`bidder_code = "google"`).

### Privacy

The SDK includes the device advertising ID and standard device/app metadata with each event. Make
sure your app's consent setup (GDPR/TCF) is configured as usual via `AudienzzTargetingParams`; the
same consent signals that govern Prebid apply.

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

## Banner refresh ownership

`AudienzzAdViewHandler` owns Original banner refresh; Prebid receives no refresh interval. Leave the GAM ad unit's own refresh rate unset to avoid a second schedule. `AdManagerAdView.pause()/resume()` also follow app foreground state.

The configured interval starts at Google's terminal load callback. No-fill waits the normal interval; transient Google failures use bounded retries. A Prebid timeout does not cause a fast replacement of a successful Google creative. Page transitions serialize replacement loads on a reused Google view: a stale Google load drains before its replacement starts, and its callback is not forwarded as the new page's result. Install the publisher's `AdListener` before calling `load` so the SDK can wrap it.

`adUnit.setAutoRefreshInterval(seconds)` now updates a running handler, including `0` to disable periodic refresh. Use `handler.stopAutoRefresh()` / `handler.resumeAutoRefresh()` for a durable publisher pause. Viewport resume, reattachment, page impressions and foregrounding do not clear it. First-load prefetch may run before attachment/refresh visibility, but still respects publisher, page and foreground gates. Gate-rejected first loads and page replacements remain pending until they can run. Call `handler.destroy()` when the slot is disposed.


### Remote interstitial lifecycle

`prefetchAndShow()` presents as soon as the load completes, under the same guards an explicit
`show` applies: a backgrounded app, expired inventory or another interstitial already on screen all
cancel it, reported through `Events.onError` and as `opportunitySkipped` on
`Events.onLifecycleEvent`. Destroying a pending instance prevents later demand or Google callbacks
from showing an ad. Destruction while presenting waits for its terminal callback.
`Events.onLifecycleEvent` supplies a load ID, event name, response ID and failure/disposal reason
for publisher analytics. Loading, presenting, and recording an impression are distinct events.


### Recommended interstitial prefetch and presentation

Retain one `AudienzzRemoteConfigInterstitial` per logical placement. `prefetch()` retains ready
inventory, joins a load already in flight and never presents. Call `show(activity, eligible)` on the
main thread at the actual transition, supplying the publisher's current frequency-cap decision.
It skips unavailable/expired inventory, inactive hosts, ineligible opportunities and concurrent
SDK remote interstitial presentations. A skip never schedules a later show — that is what
`prefetchAndShow()` is for, and it has to be asked for by name. The prefetched ad stays available
for a later explicit opportunity; expiry requires another explicit `prefetch()`.

True means submitted to Google, with `Events` callbacks reporting presentation/impression/failure.
The publisher still owns frequency caps and other fullscreen content. Check eligibility before
prefetching when practical. Do not create owners or requests on rebuild, rotation or
`pageImpression`. `destroy()` during presentation retains the owner through the terminal callback.

### Automatic request counters

Original and remote banners include `au_page_seq`, `au_slot` and `hb_refresh_count` in GAM
custom targeting. Interstitials include only `au_page_seq` and `hb_refresh_count`; they never
consume a banner position. See [the request targeting contract](docs/ad-request-targeting.md)
for page resets, automatic slot ordering and request-count semantics. No new publisher parameter is required.

### Analytics environments and publisher identity

Remote initialization supplies the ws-sdk-config `publisher_id` automatically (including Flutter).
The collector resolves company and website IDs. Analytics defaults to `environment=production`;
set `test` or `staging` before initializing a non-production app. Our examples use `test`.
See [the analytics contract](docs/analytics-contract.md) for configuration, currency provenance,
missing Prebid metadata and release requirements. These additions require the upcoming native
releases; current published native pins do not provide them.
