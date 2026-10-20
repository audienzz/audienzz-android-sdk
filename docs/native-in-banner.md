# Native ads in banner slots

A remote banner can also serve **native** ads. The slot stays the same `AudienzzRemoteBannerView`
(a GAM `AdManagerAdView`); the native ad is laid out by a **GAM native style** (HTML/CSS defined
in the GAM console) and rendered by Google in that view. Apps add no native views and no code.

Native is switched on per placement in the ad config. Nothing changes for placements that do not
set it.

## Backend schema

`config.nativeConfig` on a banner ad config:

```json
{
  "id": 46,
  "config": {
    "adType": "banner",
    "refreshTimeSeconds": 30,
    "nativeConfig": { "enabled": true, "fluid": false }
  },
  "gamConfig": { "adUnitPath": "/96628199/de_audienzz.ch_v2/300x250", "adSizes": ["300x250"] },
  "prebidConfig": { "placementId": "wuobgeuc", "adSizes": ["300x250"] }
}
```

| Field | Type | Effect | When absent or invalid |
|---|---|---|---|
| `config.nativeConfig.enabled` | boolean | The Prebid auction also bids for native (see below) | `false` |
| `config.nativeConfig.fluid` | boolean | GAM is also asked for the fluid size; only read when `enabled` is `true` | `false` |

Only a JSON boolean `true` turns a flag on. Any other value (string, number, a bare `true`
instead of an object) leaves native off; it never drops the rest of the ad config.

## What the SDK does

With `enabled: true` (and Prebid sizes present, i.e. header bidding on):

- The Prebid impression carries **banner and native**. The native request is
  `AudienzzNativeParameters.gamNativeStyleDefaults()`:

  | Asset | Required | Notes |
  |---|---|---|
  | Title | yes | max 90 characters |
  | Main image | yes | 200x200 |
  | Sponsored by | yes | max 90 characters |
  | Icon | no | 20x20 |
  | Description | no | |
  | Call-to-action text | no | max 90 characters |

  Plus one impression event tracker (image pixel). Context: content-centric, general; placement:
  in-content unit.
- Because the auction asks for two formats, Prebid Server adds **`hb_format`** (`banner` or
  `native`) to the GAM key-values. Bids are cached in Prebid Cache as for every banner.
- Analytics `media_types` is `["banner","native"]` for that placement.

With `fluid: true` the GAM request also includes `AdSize.FLUID` (after the slot's own sizes), and
the GAM view spans the full banner width, because a fluid ad takes its width from the view and
its height from the creative.

GAM-only placements (no Prebid sizes) run no auction, so `enabled` adds nothing there. GAM-sold
native still serves into them through the native style, as it does without this flag.

## GAM / ad ops setup (required before enabling)

1. **Native style** for the ad unit, at the slot's size (e.g. 300x250), or *fluid* when the ad
   config sets `fluid: true`. It must render the assets above and cope with the optional ones
   being absent.
2. **Prebid native rendering**: the native style (or the Prebid native creative) uses the
   Prebid Universal Creative's native renderer, reading the winning bid from Prebid Cache via
   the `hb_cache_*` keys. See Prebid's [GAM native setup](https://docs.prebid.org/adops/gam-native.html)
   and the [in-webview native integration](https://docs.prebid.org/prebid-mobile/recipes/subrecipes/android/gam-bidding-only-native-in-webview.html).
3. **Line items**: Prebid native line items target `hb_format=native` plus the price key.
   **On native-enabled ad units, the existing Prebid banner line items must target
   `hb_format=banner`.** Otherwise a native win can be matched to the banner creative and render
   blank. Placements that stay banner-only never receive `hb_format` and need no change.
4. **Prebid Server**: the stored impression for the placement must allow native, and the bidders
   on it must support native. Without that the toggle only enlarges the request.

## Using it without remote config

```kotlin
val adUnit = AudienzzBannerAdUnit(configId, 300, 250)
adUnit.enableNativeFormat() // or enableNativeFormat(customParameters)
```

Call it before the first `fetchDemand`. Load the GAM request into the same `AdManagerAdView`.

## Not yet verified

- On a device with a real native bid: the native style rendering a Prebid native win and firing
  its impression and click tracking inside the GAM webview.
- `fluid` mixed with fixed sizes in one GAM request is not documented by Google; test on a device
  before enabling it in production.
