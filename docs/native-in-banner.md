# Native ads in banner slots

A remote banner can also serve **native** ads. The slot stays the same `AudienzzRemoteBannerView`
(a GAM `AdManagerAdView`); the native ad is laid out with HTML/CSS defined in the GAM console and
rendered by Google in that view. Apps add no native views and no code.

Prebid native wins are drawn by an HTML creative in GAM, and GAM-sold native by a native style
(see the GAM setup below). Native is switched on per placement in the ad config. Nothing changes for placements that do not
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
  `AudienzzNativeParameters.inBannerDefaults()`:

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

Two kinds of native demand reach the slot, and GAM handles them differently.

### Prebid native wins: one HTML creative

Prebid Mobile's documented in-app path for this ("in-webview native") is a **third-party HTML
creative** that loads the Prebid Universal Creative's `native-trk.js`. In app mode it reads the
winning bid from Prebid Cache (`hb_cache_id`), fills the template's placeholders and fires the
bid's impression and click trackers. The HTML/CSS lives in GAM, so the look is still edited in
the console. One creative can be shared by every Prebid native line item.

```html
<style>
  .pbn { box-sizing: border-box; width: 300px; height: 250px; overflow: hidden;
         font-family: -apple-system, Roboto, sans-serif; background: #fff; }
  .pbn img.main { width: 100%; height: 150px; object-fit: cover; display: block; }
  .pbn .t { font-size: 15px; font-weight: 600; margin: 6px 8px 2px; }
  .pbn .b { font-size: 12px; color: #444; margin: 0 8px; }
  .pbn .s { font-size: 11px; color: #888; margin: 4px 8px; }
  .pbn a.cta { float: right; margin: 0 8px; font-size: 12px; }
</style>
<div class="pbn">
  <a class="pb-click" href="%%CLICK_URL_UNESC%%hb_native_linkurl" target="_blank">
    <img class="main" src="hb_native_image" alt="">
    <div class="t">hb_native_title</div>
  </a>
  <div class="b">hb_native_body</div>
  <div class="s">Sponsored by hb_native_brand
    <a class="cta pb-click" href="%%CLICK_URL_UNESC%%hb_native_linkurl" target="_blank">hb_native_cta</a>
  </div>
</div>
<script src="https://cdn.jsdelivr.net/npm/prebid-universal-creative@1.17.2/dist/native-trk.js"></script>
<script>
  var pbNativeTagData = {};
  pbNativeTagData.env = "mobile-app";
  pbNativeTagData.uuid = "%%PATTERN:hb_cache_id%%";
  pbNativeTagData.cacheHost = "%%PATTERN:hb_cache_host%%";
  pbNativeTagData.cachePath = "%%PATTERN:hb_cache_path%%";
  window.pbNativeTag.startTrackers(pbNativeTagData);
</script>
```

Rules the renderer imposes (checked against PUC 1.17.2 source):

- Placeholders are **bare** (`hb_native_title`), not `##hb_native_title##`: the `##` form is
  only for Prebid.js on the web. Unfilled placeholders become empty strings.
- Available keys: `hb_native_title`, `hb_native_image` (main), `hb_native_icon`,
  `hb_native_body`, `hb_native_brand` (sponsored-by), `hb_native_cta`, `hb_native_linkurl`.
- Click trackers fire only on elements with class `pb-click`.
- Image vs icon and body vs sponsored vs CTA are told apart by the `type` in the bid's own
  response assets. A bidder that omits it loses those assets (title and link still work), so
  check real bids from each native bidder.
- If the bid has no `hb_cache_host`/`hb_cache_path`, the renderer falls back to
  `prebid.adnxs.com/pbc/v1/cache`. That is only correct if Prebid Cache is Xandr's.

Line items (a new set, generated like the banner ones):

| Setting | Value |
|---|---|
| Type | Price priority |
| Targeting | `hb_pb` = price bucket **and** `hb_format` = `native` |
| Creative | the HTML creative above, size = the slot size (e.g. 300x250) |
| Inventory | the native-enabled ad units |

**Existing Prebid banner line items: add `hb_format` is not `native`.** Prebid only sends
`hb_format` when an auction asks for more than one format, so this matches every banner-only
request (no key) and every banner win, and stops a native win landing on the banner creative.
Prebid video line items, if they exist, already target `hb_format=video` or need the same guard.

### GAM-sold native: a native style

Direct native campaigns use GAM's own native setup: *Delivery > Native > New native ad*, HTML &
CSS editor, a size matching the slot (e.g. 300x250) or *Fluid*, and the native-enabled ad units.
Google renders them in the same banner view. Choose *Fluid* only with `fluid: true` in the ad
config. This part needs no Prebid setup and works even with `enabled: false`.

### Prebid Server

- The stored impression for the placement (`prebidConfig.placementId`) must allow native, and
  each bidder on it must support native for app traffic. Some SSPs need a separate native
  placement or a native-enabled one. Without native bidders the toggle only enlarges the request.
- Bids must be cached (the SDK always requests `cache.bids`), and `ext.prebid.targeting.
  includeformat` must be honoured so `hb_format` reaches GAM.

## Using it without remote config

```kotlin
val adUnit = AudienzzBannerAdUnit(configId, 300, 250)
adUnit.enableNativeFormat() // or enableNativeFormat(customParameters)
```

Call it before the first `fetchDemand`. Load the GAM request into the same `AdManagerAdView`.

## Not yet verified

- On a device with a real native bid: the HTML creative rendering a Prebid native win and firing
  its impression and click tracking inside the GAM webview.
- `fluid` mixed with fixed sizes in one GAM request is not documented by Google; test on a device
  before enabling it in production.
