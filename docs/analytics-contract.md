# Audienzz clickstream analytics — field contract

Canonical for all four SDKs. iOS, Flutter and React Native carry a pointer to this file.

Events are flat JSON posted to
`POST https://api.adnz.co/api/ws-clickstream-collector/submit/batch`. Every event has an envelope
(top-level keys) and an `attributes` map. **Every value inside `attributes` is a JSON string**,
including numeric-looking ones — that is the existing contract and this pass did not change it.

Status of each item below is one of: **fixed** (changed in this pass), **already correct**
(verified, unchanged), **backend/config** (not a code defect), or **needs analytics input**.

---

## 1. Envelope fields

| Field | Type | Unit / format | Nullable | Provenance |
|---|---|---|---|---|
| `event_type` | string | — | no | Event kind, see §3 |
| `source` | string | `android-sdk` \| `ios-sdk` | no | Hardcoded per native SDK. **Not** the wrapper — see §8 |
| `company_id` | string | — | yes | **Inconsistent across platforms — see §2** |
| `event_id` | string | UUID v4, lowercase | no | Per event |
| `session_id` | string | UUID v4, lowercase | no | Per process |
| `session_start_timestamp` | number | **Unix SECONDS** (was milliseconds) | yes | Fixed in this pass — see §4 |
| `session_seq` | number | monotonic from 0 | no | Per session, assigned at creation so batching/retry cannot reorder it |
| `event_timestamp` | string | ISO-8601 UTC, ms precision | no | Per event |
| `page_impression_id` | string | UUID v4 | yes | New on every `pageImpression` |
| `visitor_id` | string | UUID v4 | yes | Persisted in app storage |
| `device_id` | string | lowercase advertising id | **yes — key absent when unavailable** | Fixed in this pass — see §7 |
| `screen_name` | string | — | yes | Whatever the app reported |
| `sdk_name` / `sdk_version` | string | — | no | Compile-time constant — see §5 |
| `zone_offset_seconds` | number | seconds | no | Device timezone offset |
| `locale`, `os_name`, `device_category`, `browser_name` | string | — | no | Device/app metadata |
| `screen_*`, `viewport_*` | number | dp / points | no | Device metrics |
| `app_package_name`, `app_version`, `app_title` | string | — | yes | Host app |

---

## 2. `company_id` and `website_id` — **needs analytics input**

**Traced, and the two platforms disagree. Nothing was changed, because changing it means choosing
a business mapping the SDK cannot verify.**

| Path | `company_id` | `website_id` |
|---|---|---|
| Android `initializeSdk(companyId = …)` (direct) | the publisher's literal argument | `publisherId`, which is unset on this path → **absent** |
| Android `initializeRemoteSdk(publisherId = …)` | `publisherConfig.ortb.schain.sellerId`, fallback `"1"` | `publisherId` |
| iOS `configureSDK(companyId:)` (direct) | the publisher's literal argument | `AudienzzRemoteConfig.publisherId` → **absent** |
| iOS `configureWithRemoteSDK()` | `AudienzzRemoteConfig.publisherId`, fallback `"1"` | `AudienzzRemoteConfig.publisherId` |

So for the same publisher, remote-initialized:

* **iOS** sends `company_id = website_id = publisherId`. The attached log shows `35` / `35`.
* **Android** sends `company_id = schain.sellerId`. For publisher 35 the backend returns
  `"sellerId": "1"`, so Android would send `company_id = "1"`, `website_id = "35"`.

### Where does `company_id = 2372` come from?

**It cannot come from the SDK as currently written.** Checked against the live backend on
2026-09-21:

```
GET /api/ws-sdk-config/public/v1/publishers/35            → no field equal to 2372
GET /api/ws-sdk-config/public/v1/publishers/35/ad-configs → no field equal to 2372
```

The only numeric identifiers the SDK is given are `publisherId` (35), `prebidServer.accountId`
(3927) and `schain.sellerId` ("1"). A value of 2372 is therefore an **Audienzz-internal company
identifier that is not exposed to the SDK on any endpoint it calls**.

Failure modes, all of which currently produce a *value* rather than an absence:

| Situation | `company_id` today |
|---|---|
| Remote config fetched normally | iOS `publisherId`; Android `sellerId` |
| Remote config missing/404, no cache | `"1"` on both — a literal fallback, indistinguishable from a real seller id of 1 |
| Remote config served from the 24h cache | the cached value, same mapping |
| Direct (non-remote) init | whatever the publisher passed |

### The question for analytics

> For a remote-initialized app, which identifier do you want in `company_id`: the **publisher id**
> the app initializes with (iOS's current behaviour), the **schain seller id** (Android's current
> behaviour), or an **Audienzz company id** such as 2372? If the third — which endpoint or config
> field should the SDK read it from? It is on none of the ones we call today.
>
> Second: should `company_id` be **omitted** when remote config is unavailable, instead of falling
> back to the literal `"1"`? Today `"1"` is ambiguous with a real value.

Until that is answered, treat `company_id` as **platform-dependent** and prefer `website_id`
(= `publisherId`) for joins; it is consistent on both platforms.

---

## 3. Event scope and `bidder_code`

| Event | Scope | `bidder_code` |
|---|---|---|
| `pageImpression` | screen visit | — |
| `bidRequest` | one auction | — (nothing has responded yet) |
| `bidResponse` | one auction | the Prebid seat (`hb_bidder`) when there is a usable bid, else absent |
| `bidWon` | one auction | the Prebid seat (`hb_bidder`) |
| `noBid` | **one auction, not one bidder** | **always absent — see below** |
| `adImpression`, `adClick` | one rendered creative | which demand *rendered* — see below |
| `viewability.start`, `viewability.success` | one rendered creative | same as its `adImpression` |

### `noBid` carries no bidder — **already correct, now covered by tests**

Prebid's `fetchDemand` reports one aggregate result code for the auction. It does not report which
bidders were asked, or which of them returned nothing. Both SDKs therefore emit `noBid` with **no
`bidder_code` key at all**, and this pass added tests pinning that. Specifically, `noBid` must never
be labelled `google` (Google is not a Prebid bidder) and must never reuse the previous winner.

Distinguishable no-bid causes, carried in `result_code`:

| `result_code` | Meaning |
|---|---|
| `NO_BIDS` | The auction completed and nothing usable came back. This includes Prebid returning `SUCCESS` with empty targeting, which both SDKs normalize to `NO_BIDS` so the funnel does not show a "successful" no-bid |
| `NETWORK_ERROR`, `TIMEOUT` | Transport failure — no auction result at all |
| `INVALID_ACCOUNT_ID`, `INVALID_CONFIG_ID`, … | Configuration rejected by the server |

`STORED_REQUEST_NOT_FOUND` is **not** separately distinguishable at the SDK boundary: Prebid Mobile
collapses it into the generic failure codes above. If analytics needs it, it has to come from the
Prebid Server response, not the client.

### `bidder_code` on render events

`adImpression` / `adClick` / `viewability.*` answer *which demand rendered*, which is a different
question from *who won the Prebid auction*. The SDK decides it from the GAM **app event**: the
Prebid line item announces itself with an app event named `Prebid`. If that event fired, the render
is attributed to the Prebid seat; otherwise to the ad server, reported as `google`.

**This depends on ad-ops configuration.** If the GAM Prebid line item is not configured to send
that app event, every render is attributed to `google` even when Prebid won the auction. That is
the pattern in the attached log: `bidResponse`/`bidWon` say `test`, every render says `google`.

### `bidder_code = "test"` — **genuine demand, not a placeholder**

In the attached log, seven Prebid responses carry seat `test`. This is **real test demand**, not a
hardcoded value:

* It arrives as `hb_bidder` in the Prebid targeting keywords on the GAM request, read verbatim.
* The demo app runs against a Prebid endpoint in test mode — the log shows `test = 1` in the GAM
  custom targeting and a `?test=1` Prebid URL. Publisher 35's configured endpoint is
  `https://fast.nexx360.io/inapp`; in test mode the server answers with a seat literally named
  `test` at a fixed `1.42` CPM, which is exactly what the log shows on all seven.
* There is **no hardcoded bidder string anywhere in the SDKs**. The only literal is
  `AD_SERVER_BIDDER = "google"`, which is the ad-server attribution described above, and
  `PREBID_BIDDER`, used only when the app event fired but `hb_bidder` was somehow absent.

**Recommended analytics filter:** exclude rows where `bidder_code = 'test'` **and**
`cpm = 1.42` from revenue reporting; they are test-endpoint responses from demo/QA builds. Do not
suppress them in the SDK — they are a true record of what the server returned, and hiding them
would make QA builds indistinguishable from production ones.

---

## 4. `slot_reload` — **fixed**

Binary. `"0"` = this slot's first load in its lifetime. `"1"` = any subsequent load, whatever the
cause (periodic refresh, page return, manual reload, retry).

* **Type: JSON string**, matching every other `attributes` value. Unchanged.
* Emitted on `bidRequest`, `bidResponse`, `bidWon`, `noBid` and the render events — including
  Google-only fills and no-bid paths.
* **Snapshotted with its auction/creative**, so a render event carries the flag of the creative it
  describes, not the slot's current state.
* An internal counter still exists (it is how "first or not" is decided); it is not reported and no
  new public field was added.

**Before:** a slot that refreshed four times emitted `slot_reload` `"0"`, `"1"`, `"2"`, `"3"`.
The attached log contains nine `"2"` values.
**After:** `"0"`, then `"1"` forever.

**Migration:** historical rows with `slot_reload > 1` mean "a reload"; read them as `1`.

---

## 5. `session_start_timestamp` — **fixed**

**Unix seconds**, fixed for the life of the process.

* **Before:** milliseconds (`1789978756503` in the attached log).
* **After:** seconds (`1789978756`).

**Migration rule** — by magnitude, which is unambiguous for any date this decade:

```sql
CASE WHEN session_start_timestamp > 100000000000
     THEN session_start_timestamp / 1000   -- legacy milliseconds
     ELSE session_start_timestamp          -- current seconds
END
```

Do **not** apply a blanket `/1000`: it would halve every new row into 1970.

**Not converted, and still milliseconds:** `time_to_respond`, `autorefresh_time`. These are
durations, not absolute timestamps, and their unit did not change.

---

## 6. `auction_id` — **already correct for banners, hardened**

Every actually-started auction mints a new id, shared by every event of that auction
(`bidRequest → bidResponse → bidWon|noBid → adImpression → viewability.*`).

* Covered: initial load, periodic refresh, manual reload, page return, retry, interstitial reload.
* Coalesced or rejected requests do **not** mint an id — the id is minted where the request is
  actually issued, after every gate.
* **The reported reuse was not reproduced.** The attached log contains 8 auctions and 8 distinct
  ids. Verified against current code on both platforms.

**Fixed in this pass:** the *stale-callback* case. Render events used to read the newest auction's
economics, so once a replacement's Prebid response arrived — or once it failed and cleared them —
a late `adImpression` or `viewability.*` from the creative **still on screen** was filed under the
replacement's `auction_id`, `cpm`, `creative_id` and `bidder_code`. The displayed creative's
economics are now snapshotted when Google confirms the render, and render events read that
snapshot. A replacement that never arrives changes nothing.

**Also fixed:** iOS remote interstitials generated an uppercase UUID while banners generated
lowercase, so one run's `auction_id` column mixed `CE4A378A-…` with `a58d608a-…` and could not be
joined case-sensitively. Both now use the same lowercase helper.

---

## 7. `device_id` — **fixed**

The platform advertising identifier (IDFA on iOS, AAID on Android), lowercase.

**When unavailable the key is absent from the payload.** Analytics must work without it.

| Situation | Before | After |
|---|---|---|
| iOS, ATT not authorized (incl. simulator default) | `"00000000-0000-0000-0000-000000000000"` | key absent |
| iOS, ATT authorized | real IDFA | real IDFA |
| iOS, ATT authorized then **revoked** | kept emitting the cached IDFA for the rest of the session | key absent from the next event |
| Android, limit-ad-tracking on | all-zero id | key absent |
| Android, Play Services unavailable / read throws | the literal string `"empty"` | key absent |
| Android, granted after the first event | never picked up (cached for the process) | picked up within 60s |

Deliberately **not** done: no ATT prompt is triggered by the SDK; the PPID, IDFV, Android ID and
any fingerprint are **not** substituted. `device_id` and the PPID are different things with
different consent bases and remain unrelated.

`device_id` is the advertising identifier only. It is **not** affected by CMP/TCF purpose consent
in the SDK today — that gates the PPID, not this field. If analytics needs `device_id` gated on a
TCF purpose as well, that is a policy change and needs a decision.

---

## 8. Wrappers: React Native and Flutter

**All analytics events are emitted natively.** Neither bridge emits any event from JS or Dart —
verified by inspection of both packages. The bridges create native ad objects through the plugin,
and the native SDK's own event pipeline fires `bidRequest`, `bidResponse`, `bidWon`, `noBid`,
`adImpression`, `adClick`, `viewability.*` and `pageImpression` exactly as it does for a native app.

That is why `source` is `android-sdk` or `ios-sdk`: the mapper sets it per platform, and the
collector currently has **no way to tell a Flutter or React Native app from a native one**.

Supported paths that produce events through the bridges: RemoteBanner (both), original banner
(both), remote interstitial (both), original interstitial (both), rewarded (both). Rendering-API
inventory uses stock Prebid event handlers and is outside this pipeline.

Native versions actually bundled by the published bridges:

| Bridge | iOS native | Android native |
|---|---|---|
| React Native 0.5.0 | `AudienzziOSSDK ~> 0.3.2` | `com.audienzz:sdk:0.2.2` |
| Flutter 0.2.0 | `AudienzziOSSDK ~> 0.3.2` | `com.audienzz:sdk:0.2.2` |

### Proposal (not implemented — needs an analytics decision)

If distinguishing the wrapper is wanted, add **additive** envelope metadata rather than changing
`source`:

```json
"sdk_wrapper": "react-native",      // or "flutter"; key ABSENT for native apps
"sdk_wrapper_version": "0.5.0"
```

`source` keeps its meaning (which native SDK produced the event), every existing query keeps
working, and the absence of the key is itself the "native app" signal. The bridges already call a
native configure entry point, so setting it costs one parameter on each side. **Say the word and
this is a small change; it was not added speculatively.**

---

## 9. Release compatibility

`sdk_version` alone is **not** sufficient evidence of behaviour: it is a compile-time constant
(`AUSDKVersion = "0.3.2"` on iOS, `BuildConfig.AUDIENZZ_SDK_VERSION` from `audienzzSdkVersion =
"0.2.2"` on Android) and is **not bumped on a feature branch**. The attached log was produced by a
build of this branch and reports `0.3.2` — the same string the released 0.3.2 reports.

| Field | Affected releases | Fixing commit | First released fix | Recommended filter |
|---|---|---|---|---|
| `slot_reload` climbing past 1 | iOS ≤ 0.3.2, Android ≤ 0.2.2 | this pass | **unreleased** | `LEAST(slot_reload, 1)` |
| `session_start_timestamp` in ms | iOS ≤ 0.3.2, Android ≤ 0.2.2 | this pass | **unreleased** | magnitude rule, §5 |
| all-zero / `"empty"` `device_id` | iOS ≤ 0.3.2, Android ≤ 0.2.2 | this pass | **unreleased** | drop `00000000-…` and `empty` |
| stale `auction_id` on late render events | iOS ≤ 0.3.2, Android ≤ 0.2.2 | this pass | **unreleased** | none available — affected rows are not identifiable after the fact |
| mixed-case `auction_id` (interstitials) | iOS ≤ 0.3.2 | this pass | **unreleased** | `lower(auction_id)` when joining |
| `company_id` platform mismatch | all | — | **not fixed, see §2** | prefer `website_id` |
| `bidder_code = 'test'` | all | — | not a defect | exclude `bidder_code='test' AND cpm=1.42` |
| `bidder_code = 'google'` on every render | all | — | ad-ops configuration | see §3 |

**Nothing above is in a published release.** Do not assume all data from Android 0.2.2 or iOS 0.3.2
is trustworthy on these fields — by the table above, none of it is.

### How the correction release will be distinguishable

Once released, `sdk_version >= 0.3.3` (iOS) / `>= 0.2.3` (Android) is the marker. Until then, a
build carrying these fixes is indistinguishable by version alone, so use the **data shape**:

* `session_start_timestamp < 1e11` ⇒ post-fix, unambiguously.
* `slot_reload` never exceeding `1` for a long-lived slot ⇒ consistent with post-fix.

For the release itself, bump the version constant **in the same commit** as the release tag, so the
two can never disagree again.

## Delivery: batching and the durable outbox

Identical on iOS (`AUEventQueue` + `AUEventStore`) and Android (`EventBatcher` + `EventStore`).

| Setting | Value | Was |
|---|---|---|
| Max events per POST | 50 | 20 |
| Flush interval (partial batch) | 30s | 5s |
| Max buffered / stored events | 500 | 500 (memory only) |
| Retries per batch | 3, backing off 2s / 4s / 8s | unchanged |
| Extra flush triggers | app background, app foreground, connectivity regained (iOS) | unchanged |

**What this means for a consumer.** An event can now arrive up to ~30s after it occurred, plus
retry backoff — `event_timestamp` is when it *happened* and is unaffected, but "rows seen in the
last minute" is no longer a good proxy for "events that just occurred". Order within a session is
carried by `session_seq`, not arrival, so batching and retries cannot reorder anything.

**Duplicates are possible and expected to be deduped on `event_id`.** A batch that is in flight when
the process dies is still on disk, so the next launch resends it. That is deliberate: a duplicate is
recoverable, a dropped event is not. `event_id` is a UUID minted per event, so deduping on it is
exact.

**Events now survive process death.** They are written to a JSON-Lines file the moment they are
enqueued — not at flush time — and removed only once the batch settles, so a foreground crash or a
force-quit no longer loses the buffer. Two consequences worth knowing:

* Events can arrive in a *later session* than the one that produced them. They keep their original
  `session_id`, `session_start_timestamp` and `session_seq`, and on Android the whole payload is
  frozen at creation time, so `app_version` and device context are the ones the event was produced
  under — not the ones it was eventually delivered from.
* A batch that exhausts its retries is dropped from disk rather than kept, otherwise every future
  launch would replay a permanently failing batch forever.

The store is capped at 500 events (drop oldest). A device that is offline for a long session will
lose the oldest events beyond that, exactly as the in-memory buffer did before.
