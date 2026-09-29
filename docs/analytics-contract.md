# Audienzz clickstream analytics — field contract

Canonical for Android, iOS, Flutter and React Native. Updated September 28, 2026 for the
`feature/page-impression-api` branch. New changes below require new native releases and bridge
updates; the existing iOS 0.4.0 / Android 0.3.0 pins do not contain them.

Each event is flat JSON with top-level envelope fields and an `attributes` map. Attribute values
remain JSON **strings**, including CPM and counters. The collector endpoint is
`POST https://api.adnz.co/api/ws-clickstream-collector/submit/batch`.

## 1. Identity and device metadata

| Field | Current branch contract |
|---|---|
| `publisher_id` | Top-level ws-sdk-config publisher ID. Remote init supplies it; Flutter forwards it from Dart. Direct integrations may supply it through analytics configuration. Absent if unknown. |
| `company_id`, `attributes.website_id` | Omitted on newly created events. Analytics resolves these from publisher ID and its own mapping. Pending events persisted by an older SDK retain their original payload. |
| `environment` | `production` by default; explicitly configurable as `production`, `staging` or `test`. All four example apps set `test`. Invalid values are rejected without changing context. |
| `source` | `ios-sdk` or `android-sdk`, including bridge apps. |
| `os_name`, `os_version` | `iOS` / `Android` and the device OS version. |
| `device_category` | `Smartphone` / `Tablet`, using iOS idiom or Android smallest screen width ≥600dp. This is a platform classification, not a user-agent guess. |
| `screen_width`, `screen_height` | iOS points / Android dp. Not physical pixels. `viewport_*` currently mirrors these screen metrics; it is not the ad's visible rectangle. |
| `app_package_name` | Installed app bundle/package ID, not the remote config's claimed app ID. |
| `sdk_name`, `sdk_version` | Native platform and compiled SDK version. Local branch builds may share a version with an older release. |
| `event_id` | New lowercase UUID per event; unchanged on retry. Collector deduplicates this key. |
| `session_id`, `session_seq` | Process session and monotonic event sequence starting at zero. Sort by sequence, not HTTP arrival. |
| `session_start_timestamp` | Unix seconds. `event_timestamp` remains ISO-8601 UTC with milliseconds. Durations remain milliseconds. |
| `page_impression_id`, `screen_name` | Screen-visit UUID and publisher-reported name, captured when the ad request starts. All events for that delivery retain this pair; a new page impression creates a new pair. |
| `device_id` | Available advertising identifier only. Omitted for zero/unavailable IDFA/AAID; no substitute identity is invented. |

Previously, iOS remote initialization copied the publisher ID into both company and website;
Android used the schain seller as company and publisher as website. Flutter's Dart-only config
path could leave native publisher identity missing altogether. **Do not infer a ws-sdk-config
publisher from historical company IDs.** A company value such as 34 or 2372 alone cannot establish
which publisher/config a particular client used.

Configure before SDK initialization:

```swift
Audienzz.shared.configureAnalytics(publisherId: nil, environment: "test")
// configureWithRemoteSDK then supplies the remote publisher ID.
```

```kotlin
AudienzzPrebidMobile.configureAnalytics(null, "test")
// initializeRemoteSdk then supplies the remote publisher ID.
```

```dart
await AudienzzSdkFlutter.instance.initializeRemote(
  publisherId: '35', remoteUrl: remoteUrl, environment: 'test');
// Direct initialize also accepts optional publisherId and environment.
```

```typescript
await Audienzz.configureAnalytics(null, 'test');
await Audienzz.initializeRemote(remoteUrl, publisherId);
```

Production integrations can leave the environment default. Remote publishers need no extra ID
argument beyond their existing remote initializer. Direct integrations must not use a Prebid
account ID or schain seller ID as the analytics publisher ID.

## 2. Money and unavailable Prebid metadata

| Field/path | Source and meaning |
|---|---|
| Android bid `cpm` + `currency` | Actual Prebid winning bid price and response `cur`, when available. `cpm_source=prebid_bid`. No hardcoded USD/CHF. |
| iOS `price_bucket` | Prebid `hb_pb` targeting. Bucketed; **not** an exact bid price. |
| iOS bid `cpm` + `currency` | Omitted with stock Prebid original API: exact price/currency are not exposed on this path. |
| iOS render `cpm` + `currency` | Google paid-event value ×1,000 and that same paid event's currency, when already available. `cpm_source=google_paid`. Google's estimate/precision still applies. |
| `creative_id`, `ad_id` | Real metadata only when available. Missing/empty/`"0"` placeholders are omitted. |

**CHF is supported without changing labels or converting numbers.** If Prebid supplies USD and
Google supplies CHF, those are different monetary sources. The SDK must not label a USD bid as
CHF because the GAM account/report uses CHF. Currency conversion belongs in a separately defined
backend/reporting rule. Unknown currency remains absent.

The iOS paid callback reports revenue for **one impression**, not a CPM; this branch corrects the
missing ×1,000 conversion and no longer pairs Google's currency with a Prebid price bucket.
The `currency` field qualifies `cpm`; it does not assign a currency to the raw Prebid
`price_bucket`. Google paid callbacks can arrive after `adImpression`; the SDK does not delay impressions or emit a
second impression to fill missing monetary fields. Later click/viewability events may have the
paid value. Therefore these events are not a complete impression-level revenue ledger.

Android render events can carry the winning Prebid bid when its line item rendered; that remains
`prebid_bid`, not measured Google revenue. A Google/direct banner fill no longer inherits a losing
Prebid bid's amount or creative ID. Do not sum CPM across every event in an auction.

Missing original-API fields are deliberately not reconstructed from cache IDs, response IDs,
account defaults or reflection into unsupported iOS Prebid internals. See the iOS repository's
`docs/analytics-fork-free-economics.md`.

CPM strings use plain decimal notation, rounded to six decimal places: `0.00001`, not `1.0E-5`.
Non-finite or negative values are omitted.

## 3. Event and impression semantics

- `bidRequest`, `bidResponse`, `bidWon` / `noBid` describe a Prebid auction. Each started auction
  gets a new `auction_id`; coalesced/rejected requests do not.
- `noBid` is aggregate. Stock Prebid does not expose a list of individual non-bidders here, so
  `bidder_code` is absent. Google is not fabricated as a bidder for this event.
- `bidder_code=test` is a real server seat/test response, not a SDK placeholder. Use
  `environment=test` for new QA traffic; historical filtering still needs known bundles/configs.
- `adImpression` comes from Google's recorded-impression callback, not a blank placeholder,
  loading state, timer or a page impression. iOS now guards repeat callbacks per displayed
  creative; a real replacement can report its own impression. Starting a replacement auction
  alone does not reopen the previous creative's impression or change its identity.
- Banner render attribution uses GAM's `Prebid` app event. Without that ad-ops signal, the banner
  is attributed to `google`. Fullscreen bidder attribution is best effort from the winning bid,
  not independent proof that this bid won the Google auction.
- `slot_reload` is the string `"0"` for the first slot load, `"1"` after that. It is distinct from
  the numeric request-targeting `hb_refresh_count`, which resets with a page visit.

Two duplicate cases must be separated in production: identical `event_id` means replay/transport
retry (collector dedup required); distinct IDs for the same delivery can mean repeated callbacks.
The new callback guard covers the latter. The historical 2–5 event reports still need event IDs
and delivery IDs to establish their exact cause; matching slot/timestamp alone is insufficient.

## 4. When to report page impressions

Report each actual screen visit, including return navigation and screens with no ads. An article
is a new page when it is a new route/visit. Do not report from `build`, layout, scrolling, every ad
request, or every refresh timer.

With the SDK's Flutter navigator observer or React Native navigation integration, let that helper
report the transition; do not also call the manual API. Custom navigation must report itself.
Native SDKs automatically re-report the active page after an app background/foreground transition.
The managed bridge interstitial flow also handles return to the current page; do not add another
publisher page impression for the same dismissal. A page change in fullscreen must remain owned
by navigation, rather than resurrecting the page that launched the ad.

### Joining ad events to a page visit

`pageImpression.page_impression_id` is the visit key. Every `bidRequest`, `bidResponse`, `bidWon`,
`noBid`, `adImpression`, `adClick` and `viewability.*` for an ad request carries that same top-level
`page_impression_id`. Each event still has its own `event_id` for retry deduplication.

- Refreshing a banner on the same visit keeps the page ID and creates a new auction ID.
- Navigating A → B → A creates three different page IDs, including on an ad-free screen.
- Late callbacks and queued/retried events retain the originating visit; they cannot adopt the
  screen that happens to be current when the callback or network send occurs.
- An interstitial prefetched on A and shown on B retains A's page ID for its entire lifecycle.
  A new prefetch accepted on B gets B's ID. Joining the request to its eventual render is deliberate.
- Call `pageImpression` before requesting ads. Before the first report, the SDK omits the page ID
  rather than inventing an ID with no matching page event. Such an early load does not adopt a later
  visit retroactively. Loading behavior is unchanged; a missing page ID indicates integration order.

Native owns this attribution for Flutter and React Native too; publishers do not attach the ID
manually. Background-thread page reports publish the ID and transition the native slots together
on the main thread, so old-page work cannot observe the next visit before its release.

## 5. Release and operational action items

The current checkout pins native **iOS ~>0.4.0 / Android 0.3.0** in the bridges. New APIs and fixes
in this document have been checked against local natives and must ship native-first, then be
pinned by both bridges. Do not claim a new fixing version until it is tagged and published.

Local release tags confirm the seconds timestamp contract in Android 0.2.3/0.3.0 and iOS 0.4.0;
iOS 0.3.3 is not available as a local tag for verification. This does **not** mean these releases
contain today's publisher/environment, deduplication, economics or immediate-delivery changes.
Existing version constants alone cannot identify unpublished branch builds. Preserve release
commit SHAs with validation records and bump constants at release.

Backend/publisher owners still need to provide or verify:

1. NZZ's actual publisher configuration and installed native/wrapper versions. `company_id=34`
   is insufficient. Confirm active public config for 34/81 or correct the client configuration.
2. Whether NZZ uses instrumented Original/Remote ad components. Page-only events can mean no
   instrumented ad requests, failed config, or a different ads integration; it is not proof that
   ad analytics are working. Rendering-API inventory is outside this event pipeline.
3. La Liberté's actual production bundle/package IDs and the remote config mapping, including
   `com.pagesuite.android.laliberte` versus `ch.stpaul.laliberte`.
4. Production rollout owners/dates and exact client build/native dependency versions. The SDK
   repository cannot establish when a publisher will ship.
5. Collector joins on `publisher_id`, filtering on `environment`, and deduplication on `event_id`.
   Keep legacy handling for queued old payloads and older app versions.

Historical milliseconds can be normalized with `timestamp > 100000000000 ? timestamp / 1000 : timestamp`.
Treat historical `slot_reload > 1` as `1`; drop zero advertising IDs. No reliable backfill exists
for lost events or incorrect historical impression attribution.

## Delivery: immediate sends and a durable outbox

The current branch uses the same policy on iOS (`AUEventQueue` + `AUEventStore`) and Android
(`EventBatcher` + `EventStore`). The existing endpoint remains `/submit/batch` for server
compatibility, but **each request contains a single event**. There is no batching window or size
threshold. Delivery starts as soon as the event is persisted and the sender is available.

| Setting | Current branch | Previous behaviour |
|---|---|---|
| Events per POST | 1 | Up to 20 |
| Deliberate batching delay | None | Up to 15 seconds |
| Concurrent HTTP requests per SDK process | 1 | 1 batch |
| Complete HTTP attempt timeout | 30 seconds | Platform defaults |
| Failed delivery | Retained; consecutive failures back off 2s, 4s, 8s, 16s, 32s, then 60s; success resets backoff | Dropped after 3 retries |
| Persisted pending events | 500, evict oldest waiting event on overflow; protect the in-flight event | 500 buffered events |

**Save → attempt → acknowledge → remove.** File I/O, request creation and retries run off the UI
thread. The application never waits for the collector. A successful HTTP acknowledgement,
including 204, removes that event by `event_id`. Failure leaves it saved. Foreground/connectivity
hints and new events cannot bypass retry backoff. A rejected event rotates behind other pending
events so one invalid payload cannot permanently strand the rest. On startup, saved pending
events resume delivery with their original IDs, timestamps and payloads.

The outbox keeps the existing JSONL storage and adds small acknowledgement records. It reads the
backlog once, appends changes, and compacts every 64 removals or when empty. This avoids a full-file
rewrite for every request. Existing event-only files are readable without losing pending events.
Repeated SDK initialization reuses the same sender/store owner.

**Duplicates remain possible:** the server can accept an event and the app can close before the
acknowledgement is recorded. The collector must deduplicate by `event_id`, not `auction_id` or slot.
Order analytically by `session_seq`, not HTTP arrival. Immediate delivery does not guarantee receipt
before force-quit; retained data resumes when the app can run again. Uninstalling the app, exceeding
the storage cap, or a filesystem failure can still lose data. `persistenceFailed` diagnoses a failed
write; analytics falls back to memory rather than interrupting ads or the application.

**Performance tradeoff:** removing batching increases HTTP request count. Connection reuse, a
single in-flight request, off-thread storage and capped retries bound the work; they do not imply
zero battery/network cost. Host/simulator storage benchmarks are regression checks, not physical
device battery measurements.

### Checking delivery in Flutter and React Native

Both bridges use the native collector transport. In Charles, look for
`api.adnz.co/api/ws-clickstream-collector/submit/batch`, with SSL proxying enabled for
`api.adnz.co:443` and the Charles certificate trusted on the test device. Current-branch builds
send individual events without a 15-second wait. Flutter's Dart proxy override alone
does not route native analytics: the device's network proxy must also be configured.

With SDK diagnostics enabled (already enabled in the examples), filter device logs for
`AUDZ analytics`. The current branch reports:

* `queued`: the native queue received an event, with its type only.
* `sending`: one event is being submitted, with attempt number.
* `sent`: the HTTP request succeeded. This does not prove downstream dashboard ingestion.
* `failed`: the HTTP status or transport error code/type; `retryScheduled` gives the cooldown.
* `dropped`: the bounded backlog overflowed; `persistenceFailed`: a local write failed.

These lines omit payloads, identifiers, targeting and consent strings, and are disabled when
SDK diagnostics are off. If `sending` appears without a decrypted request in Charles, check the
device proxy, certificate trust and capture filters. If `failed` appears, its status/code identifies
the transport failure without needing the event payload.

The current iOS branch also fixes a queue stall in the 0.4.0 transport: an empty or non-JSON reply
could leave a batch in flight forever. All 2xx acknowledgements now settle successfully, including
204; non-2xx replies enter the retry path regardless of body format. Both bridges need the fixed
native SDK (or a local native checkout for verification); upgrading Dart/JS alone cannot apply it.

Verified on September 28, 2026: rebuilt Flutter and React Native iOS examples sent individual
events to the live collector and received HTTP 204 acknowledgements. The full native suites
passed (291 iOS tests, 271 Android tests). Android's event-to-HTTP integration tests also pass
with 204 and retry an HTML 403 response; no Android device was connected for live verification.

Saving and acknowledging 500 events with 2 KB test payloads took about 0.43 seconds on the iOS
simulator and 0.24 seconds on the Android JVM test host. These are aggregate storage costs on
worker threads, not UI blocking time or measurements of physical-device battery consumption.

## Validation of the September 28 changes

- Current native suites: 305 iOS tests and 281 Android tests pass, including page attribution for
  navigation/return, banner refreshes, late interstitial callbacks and bridge-thread page reports.
  Reverting captured-page attribution fails the real interstitial callback test on both platforms.
- Earlier identity/schema checks: Flutter 211 tests; RN 144 tests plus one existing TODO;
  TypeScript passed. Flutter analysis retained existing lint warnings/infos. These bridge suites
  were not rerun for the native-only page-attribution follow-up.
- Earlier Flutter/RN builds compiled on both platforms against local natives. Local dependency
  overrides are not committed and published dependency pins remain unchanged; the page-attribution
  follow-up has been compiled and tested through the native suites.
- The installed iOS banner delegate regression delivers five impression callbacks for one
  creative, then a replacement. Removing the guard fails it (five impressions instead of one).
  The same path checks Google CHF 0.0025 impression revenue becomes CHF 2.50 CPM.
- Before the page-attribution follow-up, normal Flutter and RN iOS simulator runs received HTTP 204 for the new event schema. Publisher,
  environment and OS version were present, legacy company/website fields absent; two Flutter
  impressions had distinct delivery/auction identities. These are short smoke tests, not proof
  of every production lifecycle or of dashboard ingestion.
- No Android device was connected for live collector verification. Client rollout dates and
  the historical NZZ/La Liberté mappings still require publisher/backend confirmation.
