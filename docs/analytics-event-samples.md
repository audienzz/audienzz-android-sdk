# Audienzz Analytics — Event Samples (Android)

One representative payload per event type, captured from the Audienzz Test App
(SDK `0.1.4`, `dev-analytics`, banner + rewarded on `MainActivity`).

Each event is a flat JSON object POSTed individually to
`https://api.adnz.co/api/ws-clickstream-collector/submit/batch`.

**Event flow per screen visit:**
`pageImpression` → `bidRequest` → `bidResponse` → (`bidWon` | `noBid`) →
`adImpression` → `viewability.start` → `viewability.success` (+ `adClick` on tap).

> `adClick` is not shown below — it did not fire in this capture (nothing was tapped).
> Its shape matches `adImpression` (render-winner `bidder_code` + economics), with
> `event_type: "adClick"`.

> `device_id` is the advertising id (GAID / `dpid`). In this run it is
> `4c5b17c1-be6f-45b8-9086-354991b1162a`.

---

## 1. pageImpression

Fires once per screen visit (`onScreenResumed`, from the Activity/Fragment `onResume`).
Groups all following events via `page_impression_id`.

```json
{
  "event_type": "pageImpression",
  "attributes": {
    "website_id": "35",
    "transport": "xhr"
  },
  "company_id": "1",
  "source": "android-sdk",
  "event_id": "1dbf0973-76ca-4b04-a477-48e13b54dff9",
  "page_impression_id": "f6b41bf6-4e28-43be-a926-249ee77e0abe",
  "session_id": "601988e2-3755-4345-9471-eebc739c4813",
  "session_start_timestamp": 1783956258922,
  "session_seq": 0,
  "event_timestamp": "2026-07-13T15:24:18.933Z",
  "locale": "uk-UA",
  "zone_offset_seconds": 10800,
  "screen_height": 759,
  "screen_width": 360,
  "viewport_height": 759,
  "viewport_width": 360,
  "device_id": "4c5b17c1-be6f-45b8-9086-354991b1162a",
  "user_agent": "Mozilla/5.0 (Linux; Android 12; vivo 2004 Build/SP1A.210812.003; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/150.0.7871.46 Mobile Safari/537.36",
  "os_name": "Android",
  "device_category": "Smartphone",
  "browser_name": "Android WebView",
  "sdk_name": "android",
  "sdk_version": "0.1.4",
  "app_package_name": "org.audienzz.mobile.testapp",
  "app_version": "offline",
  "app_title": "Audienzz Test App",
  "screen_name": "org.audienzz.mobile.testapp.view.MainActivity",
  "visitor_id": "92b5b9c9-25b2-44a3-a808-db446f3c17fa"
}
```

---

## 2. bidRequest

Fires when the ad unit starts a Prebid auction (`fetchDemand`).

```json
{
  "event_type": "bidRequest",
  "attributes": {
    "ad_unit_id": "/96628199/de_audienzz.ch_v2/multi-size",
    "sizes": "300x250, 320x50",
    "ad_type": "BANNER",
    "ad_subtype": "HTML",
    "api_type": "ORIGINAL",
    "autorefresh": "true",
    "autorefresh_time": "30000",
    "refresh": "false",
    "ad_unit_code": "wuobgeuc",
    "website_id": "35",
    "media_types": "[\"banner\"]",
    "transport": "xhr"
  },
  "company_id": "1",
  "source": "android-sdk",
  "event_id": "854af609-d742-4a15-90e6-1737371353fb",
  "page_impression_id": "f6b41bf6-4e28-43be-a926-249ee77e0abe",
  "session_id": "601988e2-3755-4345-9471-eebc739c4813",
  "session_start_timestamp": 1783956258922,
  "session_seq": 1,
  "event_timestamp": "2026-07-13T15:24:19.078Z",
  "locale": "uk-UA",
  "zone_offset_seconds": 10800,
  "screen_height": 759,
  "screen_width": 360,
  "viewport_height": 759,
  "viewport_width": 360,
  "device_id": "4c5b17c1-be6f-45b8-9086-354991b1162a",
  "user_agent": "Mozilla/5.0 (Linux; Android 12; vivo 2004 Build/SP1A.210812.003; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/150.0.7871.46 Mobile Safari/537.36",
  "os_name": "Android",
  "device_category": "Smartphone",
  "browser_name": "Android WebView",
  "sdk_name": "android",
  "sdk_version": "0.1.4",
  "app_package_name": "org.audienzz.mobile.testapp",
  "app_version": "offline",
  "app_title": "Audienzz Test App",
  "screen_name": "org.audienzz.mobile.testapp.view.MainActivity",
  "visitor_id": "92b5b9c9-25b2-44a3-a808-db446f3c17fa"
}
```

---

## 3. bidResponse

Fires when the auction resolves with a winning bid (`result_code: "SUCCESS"`). Carries full economics.

```json
{
  "event_type": "bidResponse",
  "attributes": {
    "ad_unit_id": "/96628199/de_audienzz.ch_v2/multi-size",
    "result_code": "SUCCESS",
    "sizes": "320x50",
    "ad_type": "BANNER",
    "ad_subtype": "HTML",
    "api_type": "ORIGINAL",
    "autorefresh": "true",
    "autorefresh_time": "30000",
    "refresh": "false",
    "bidder_code": "test",
    "time_to_respond": "672",
    "price_bucket": "1.42",
    "hb_size": "320x50",
    "hb_format": "banner",
    "cpm": "1.425",
    "currency": "USD",
    "creative_id": "123456789",
    "auction_id": "bb2f7e50-c1ba-434a-b128-99c86d02a732",
    "ad_id": "713bff5e-ad78-4d0d-a0d9-50360f243487",
    "ad_unit_code": "wuobgeuc",
    "website_id": "35",
    "media_type": "banner",
    "size": "320x50",
    "slot_reload": "0",
    "transport": "xhr"
  },
  "company_id": "1",
  "source": "android-sdk",
  "event_id": "f786ac62-9010-408e-bca5-a5916953e09d",
  "page_impression_id": "f6b41bf6-4e28-43be-a926-249ee77e0abe",
  "session_id": "601988e2-3755-4345-9471-eebc739c4813",
  "session_start_timestamp": 1783956258922,
  "session_seq": 7,
  "event_timestamp": "2026-07-13T15:24:19.793Z",
  "locale": "uk-UA",
  "zone_offset_seconds": 10800,
  "screen_height": 759,
  "screen_width": 360,
  "viewport_height": 759,
  "viewport_width": 360,
  "device_id": "4c5b17c1-be6f-45b8-9086-354991b1162a",
  "user_agent": "Mozilla/5.0 (Linux; Android 12; vivo 2004 Build/SP1A.210812.003; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/150.0.7871.46 Mobile Safari/537.36",
  "os_name": "Android",
  "device_category": "Smartphone",
  "browser_name": "Android WebView",
  "sdk_name": "android",
  "sdk_version": "0.1.4",
  "app_package_name": "org.audienzz.mobile.testapp",
  "app_version": "offline",
  "app_title": "Audienzz Test App",
  "screen_name": "org.audienzz.mobile.testapp.view.MainActivity",
  "visitor_id": "92b5b9c9-25b2-44a3-a808-db446f3c17fa"
}
```

---

## 4. bidWon

Fires only when the Prebid auction is won (`hb_bidder` present). Same economics as `bidResponse`.

```json
{
  "event_type": "bidWon",
  "attributes": {
    "ad_unit_id": "/96628199/de_audienzz.ch_v2/multi-size",
    "sizes": "320x50",
    "ad_type": "BANNER",
    "ad_subtype": "HTML",
    "api_type": "ORIGINAL",
    "autorefresh": "true",
    "autorefresh_time": "30000",
    "refresh": "false",
    "bidder_code": "test",
    "time_to_respond": "672",
    "price_bucket": "1.42",
    "hb_size": "320x50",
    "hb_format": "banner",
    "cpm": "1.425",
    "currency": "USD",
    "creative_id": "123456789",
    "auction_id": "bb2f7e50-c1ba-434a-b128-99c86d02a732",
    "ad_id": "713bff5e-ad78-4d0d-a0d9-50360f243487",
    "ad_unit_code": "wuobgeuc",
    "website_id": "35",
    "media_type": "banner",
    "size": "320x50",
    "slot_reload": "0",
    "transport": "xhr"
  },
  "company_id": "1",
  "source": "android-sdk",
  "event_id": "2172635c-cc7c-41b6-b074-ed770fb7bb0a",
  "page_impression_id": "f6b41bf6-4e28-43be-a926-249ee77e0abe",
  "session_id": "601988e2-3755-4345-9471-eebc739c4813",
  "session_start_timestamp": 1783956258922,
  "session_seq": 8,
  "event_timestamp": "2026-07-13T15:24:19.794Z",
  "locale": "uk-UA",
  "zone_offset_seconds": 10800,
  "screen_height": 759,
  "screen_width": 360,
  "viewport_height": 759,
  "viewport_width": 360,
  "device_id": "4c5b17c1-be6f-45b8-9086-354991b1162a",
  "user_agent": "Mozilla/5.0 (Linux; Android 12; vivo 2004 Build/SP1A.210812.003; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/150.0.7871.46 Mobile Safari/537.36",
  "os_name": "Android",
  "device_category": "Smartphone",
  "browser_name": "Android WebView",
  "sdk_name": "android",
  "sdk_version": "0.1.4",
  "app_package_name": "org.audienzz.mobile.testapp",
  "app_version": "offline",
  "app_title": "Audienzz Test App",
  "screen_name": "org.audienzz.mobile.testapp.view.MainActivity",
  "visitor_id": "92b5b9c9-25b2-44a3-a808-db446f3c17fa"
}
```

---

## 5. noBid

Fires instead of `bidWon` when the auction returns no usable bid (here the rewarded
unit's stored request is missing → Prebid `NO_BIDS`). Same shape as `bidRequest`, plus
`result_code: "NO_BIDS"`; **no economics**.

```json
{
  "event_type": "noBid",
  "attributes": {
    "ad_unit_id": "/96628199/de_audienzz.ch_v2/multi-size",
    "result_code": "NO_BIDS",
    "ad_type": "REWARDED",
    "ad_subtype": "VIDEO",
    "api_type": "ORIGINAL",
    "autorefresh": "false",
    "autorefresh_time": "0",
    "refresh": "false",
    "ad_unit_code": "37116627",
    "website_id": "35",
    "media_types": "[\"video\"]",
    "transport": "xhr"
  },
  "company_id": "1",
  "source": "android-sdk",
  "event_id": "7f0908e1-28a1-4f2c-8ea4-d77f0c60039e",
  "page_impression_id": "f6b41bf6-4e28-43be-a926-249ee77e0abe",
  "session_id": "601988e2-3755-4345-9471-eebc739c4813",
  "session_start_timestamp": 1783956258922,
  "session_seq": 6,
  "event_timestamp": "2026-07-13T15:24:19.734Z",
  "locale": "uk-UA",
  "zone_offset_seconds": 10800,
  "screen_height": 759,
  "screen_width": 360,
  "viewport_height": 759,
  "viewport_width": 360,
  "device_id": "4c5b17c1-be6f-45b8-9086-354991b1162a",
  "user_agent": "Mozilla/5.0 (Linux; Android 12; vivo 2004 Build/SP1A.210812.003; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/150.0.7871.46 Mobile Safari/537.36",
  "os_name": "Android",
  "device_category": "Smartphone",
  "browser_name": "Android WebView",
  "sdk_name": "android",
  "sdk_version": "0.1.4",
  "app_package_name": "org.audienzz.mobile.testapp",
  "app_version": "offline",
  "app_title": "Audienzz Test App",
  "screen_name": "org.audienzz.mobile.testapp.view.MainActivity",
  "visitor_id": "92b5b9c9-25b2-44a3-a808-db446f3c17fa"
}
```

---

## 6. adImpression

Fires when GAM records the impression. `bidder_code` reflects the actual render winner
(`google` = ad server rendered; a Prebid bidder = its line item rendered).

```json
{
  "event_type": "adImpression",
  "attributes": {
    "ad_unit_id": "/96628199/de_audienzz.ch_v2/multi-size",
    "ad_type": "BANNER",
    "ad_subtype": "HTML",
    "api_type": "ORIGINAL",
    "bidder_code": "google",
    "time_to_respond": "174",
    "price_bucket": "1.42",
    "hb_size": "300x250",
    "hb_format": "banner",
    "cpm": "1.425",
    "currency": "USD",
    "creative_id": "123456789",
    "auction_id": "ff8ae78f-f468-476b-9917-32ae46572418",
    "ad_id": "009b4e4e-368d-4268-adb5-680e0788d436",
    "ad_unit_code": "wuobgeuc",
    "website_id": "35",
    "media_type": "banner",
    "size": "300x250",
    "slot_reload": "0",
    "transport": "xhr"
  },
  "company_id": "1",
  "source": "android-sdk",
  "event_id": "6cc1d387-9133-46fb-993c-70c73e407323",
  "page_impression_id": "e7f02c92-e6d9-4ed5-a018-9fc70be5b31c",
  "session_id": "601988e2-3755-4345-9471-eebc739c4813",
  "session_start_timestamp": 1783956258922,
  "session_seq": 25,
  "event_timestamp": "2026-07-13T15:24:36.976Z",
  "locale": "uk-UA",
  "zone_offset_seconds": 10800,
  "screen_height": 759,
  "screen_width": 360,
  "viewport_height": 759,
  "viewport_width": 360,
  "device_id": "4c5b17c1-be6f-45b8-9086-354991b1162a",
  "user_agent": "Mozilla/5.0 (Linux; Android 12; vivo 2004 Build/SP1A.210812.003; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/150.0.7871.46 Mobile Safari/537.36",
  "os_name": "Android",
  "device_category": "Smartphone",
  "browser_name": "Android WebView",
  "sdk_name": "android",
  "sdk_version": "0.1.4",
  "app_package_name": "org.audienzz.mobile.testapp",
  "app_version": "offline",
  "app_title": "Audienzz Test App",
  "screen_name": "org.audienzz.mobile.testapp.view.MainActivity",
  "visitor_id": "92b5b9c9-25b2-44a3-a808-db446f3c17fa"
}
```

---

## 7. viewability.start

Fires when the creative first crosses ≥50% visible. Adds `tracker_version`.
`bidder_code` matches `adImpression` (same render winner).

```json
{
  "event_type": "viewability.start",
  "attributes": {
    "ad_unit_id": "/96628199/de_audienzz.ch_v2/multi-size",
    "ad_type": "BANNER",
    "ad_subtype": "HTML",
    "api_type": "ORIGINAL",
    "bidder_code": "google",
    "time_to_respond": "174",
    "price_bucket": "1.42",
    "hb_size": "300x250",
    "hb_format": "banner",
    "cpm": "1.425",
    "currency": "USD",
    "creative_id": "123456789",
    "auction_id": "ff8ae78f-f468-476b-9917-32ae46572418",
    "ad_id": "009b4e4e-368d-4268-adb5-680e0788d436",
    "ad_unit_code": "wuobgeuc",
    "website_id": "35",
    "media_type": "banner",
    "size": "300x250",
    "slot_reload": "0",
    "transport": "xhr",
    "tracker_version": "1.0.0"
  },
  "company_id": "1",
  "source": "android-sdk",
  "event_id": "36f03e04-fd6e-4e24-9559-80c9ced5e442",
  "page_impression_id": "e7f02c92-e6d9-4ed5-a018-9fc70be5b31c",
  "session_id": "601988e2-3755-4345-9471-eebc739c4813",
  "session_start_timestamp": 1783956258922,
  "session_seq": 26,
  "event_timestamp": "2026-07-13T15:24:36.977Z",
  "locale": "uk-UA",
  "zone_offset_seconds": 10800,
  "screen_height": 759,
  "screen_width": 360,
  "viewport_height": 759,
  "viewport_width": 360,
  "device_id": "4c5b17c1-be6f-45b8-9086-354991b1162a",
  "user_agent": "Mozilla/5.0 (Linux; Android 12; vivo 2004 Build/SP1A.210812.003; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/150.0.7871.46 Mobile Safari/537.36",
  "os_name": "Android",
  "device_category": "Smartphone",
  "browser_name": "Android WebView",
  "sdk_name": "android",
  "sdk_version": "0.1.4",
  "app_package_name": "org.audienzz.mobile.testapp",
  "app_version": "offline",
  "app_title": "Audienzz Test App",
  "screen_name": "org.audienzz.mobile.testapp.view.MainActivity",
  "visitor_id": "92b5b9c9-25b2-44a3-a808-db446f3c17fa"
}
```

---

## 8. viewability.success

Fires once after ≥50% visible for 1 continuous second (terminal per creative).

```json
{
  "event_type": "viewability.success",
  "attributes": {
    "ad_unit_id": "/96628199/de_audienzz.ch_v2/multi-size",
    "ad_type": "BANNER",
    "ad_subtype": "HTML",
    "api_type": "ORIGINAL",
    "bidder_code": "google",
    "time_to_respond": "174",
    "price_bucket": "1.42",
    "hb_size": "300x250",
    "hb_format": "banner",
    "cpm": "1.425",
    "currency": "USD",
    "creative_id": "123456789",
    "auction_id": "ff8ae78f-f468-476b-9917-32ae46572418",
    "ad_id": "009b4e4e-368d-4268-adb5-680e0788d436",
    "ad_unit_code": "wuobgeuc",
    "website_id": "35",
    "media_type": "banner",
    "size": "300x250",
    "slot_reload": "0",
    "transport": "xhr",
    "tracker_version": "1.0.0"
  },
  "company_id": "1",
  "source": "android-sdk",
  "event_id": "04835345-ef38-47d2-a99b-1d8bdda4a00f",
  "page_impression_id": "e7f02c92-e6d9-4ed5-a018-9fc70be5b31c",
  "session_id": "601988e2-3755-4345-9471-eebc739c4813",
  "session_start_timestamp": 1783956258922,
  "session_seq": 27,
  "event_timestamp": "2026-07-13T15:24:37.982Z",
  "locale": "uk-UA",
  "zone_offset_seconds": 10800,
  "screen_height": 759,
  "screen_width": 360,
  "viewport_height": 759,
  "viewport_width": 360,
  "device_id": "4c5b17c1-be6f-45b8-9086-354991b1162a",
  "user_agent": "Mozilla/5.0 (Linux; Android 12; vivo 2004 Build/SP1A.210812.003; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/150.0.7871.46 Mobile Safari/537.36",
  "os_name": "Android",
  "device_category": "Smartphone",
  "browser_name": "Android WebView",
  "sdk_name": "android",
  "sdk_version": "0.1.4",
  "app_package_name": "org.audienzz.mobile.testapp",
  "app_version": "offline",
  "app_title": "Audienzz Test App",
  "screen_name": "org.audienzz.mobile.testapp.view.MainActivity",
  "visitor_id": "92b5b9c9-25b2-44a3-a808-db446f3c17fa"
}
```

---

## Not captured in this run

- **adClick** — fires on user tap. Same envelope + `attributes` as `adImpression`
  (render-winner `bidder_code` + economics), with `event_type: "adClick"`.
