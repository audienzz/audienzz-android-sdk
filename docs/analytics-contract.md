# Audienzz clickstream analytics — field contract

Canonical for Android, iOS, Flutter and React Native. Updated September 29, 2026 for
`feature/durable-analytics-batching`. The published baseline is Android 0.3.1 / iOS 0.4.1;
batching and the September 29 lifecycle corrections below require new native releases and
bridge dependency updates.

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
- Each accepted Prebid completion emits one `bidResponse` and either `bidWon` or `noBid`, with
  the request's `auction_id`, even without winning-bid economics. A success with no bidder is
  `noBid` with `result_code=NO_BIDS`. Retired completions are discarded. Bypassing unavailable
  Prebid and going directly to Google emits no synthetic Prebid request/response/no-bid events.
- `bidder_code=test` is a real server seat/test response, not a SDK placeholder. Use
  `environment=test` for new QA traffic; historical filtering still needs known bundles/configs.
- `adImpression` comes from Google's recorded-impression callback, not a blank placeholder,
  loading state, timer or a page impression. Both platforms guard repeat callbacks per displayed
  creative; a real replacement can report its own impression. Starting a replacement auction
  alone does not reopen the previous creative's impression or change its identity.
- Banner render attribution uses GAM's `Prebid` app event. Without that ad-ops signal, the banner
  is attributed to `google`. Fullscreen bidder attribution is best effort from the winning bid,
  not independent proof that this bid won the Google auction.
- `slot_reload` is the string `"0"` for the first slot load, `"1"` after that. It is distinct from
  the numeric request-targeting `hb_refresh_count`, which resets with a page visit.
- `viewability.start` marks each exposure attempt, so leaving visibility and returning can emit
  another start for the same creative. `viewability.success` is terminal: at most one per creative
  after a continuous second at ≥50% visible (or a foreground fullscreen presentation).
  Backgrounding, concealment or detachment interrupts exposure. Page release, destruction and a
  received replacement cancel the previous creative's measurement. A duplicate Google load for
  the same known response ID preserves it. Starting a replacement auction alone does not cancel
  a still-visible creative. These are SDK measurements, not a substitute for Google's Active View.
- Fullscreen measurement belongs to the loaded ad, including iOS remote interstitials and
  rewarded ads. Reusing the loader cannot change an earlier ad's page/auction attribution.
  Viewability events capture the creative's economics at measurement start; late paid values
  need not appear in them.

Two duplicate cases must be separated in production: identical `event_id` means replay/transport
retry (collector dedup required); distinct IDs for the same delivery can mean repeated callbacks.
The new callback guard covers the latter. The historical 2–5 event reports still need event IDs
and delivery IDs to establish their exact cause; matching slot/timestamp alone is insufficient.

## 4. When to report page impressions

Report each actual screen visit, including return navigation and screens with no ads. An article
is a new page when it is a new route/visit. Do not report from `build`, layout, scrolling, every ad
request, or every refresh timer.

An explicit report is an instruction to start a new visit, including when its screen name matches
the previous one. There is no time-based same-screen debounce: it would suppress legitimate
returns or an explicit content change on one route. Give each navigation transition one owner.

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

The published baseline is **Android 0.3.1 / iOS 0.4.1**, and both bridge PRs have been updated
to those native dependencies. The durable batching policy below is new work on
`feature/durable-analytics-batching`, not part of those releases. Ship new native releases first,
then update both bridge dependencies; no new Dart/JS integration API is required.

Older SDK versions do not contain the complete current analytics schema or lifecycle fixes.
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

## Delivery: durable batching (next native release)

This branch replaces the per-event HTTP sender shipped in Android 0.3.1 / iOS 0.4.1.
The policy is identical in Android `EventBatcher` and iOS `AUEventQueue`. Both bridges use these
native transports. React Native needs native dependency bumps after publication. Flutter also
forwards `analyticsBatchSize` from its Dart-fetched publisher config to native initialization;
that forwarding change and the matching native releases must ship together.

| Setting | Batching policy |
|---|---|
| Normal flush | 5 seconds after the oldest waiting event, or the configured event count (default 10) |
| Maximum POST | Configured event count, capped at 15, and 128 KiB of serialized UTF-8 JSON, whichever fills first |
| Concurrent HTTP requests | 1 per SDK process |
| Request start spacing | At least 2 seconds, including retries and backlog draining |
| HTTP timeout | 30 seconds per attempt |
| Retry | Exponential 2/4/8/16/32/60-second ceiling, randomized to 50–100%; honor longer `Retry-After` |
| Durable capacity | 20 MiB of pending + quarantined event payloads; journal metadata and an atomic checkpoint need additional space |
| Retention | No automatic age expiry or retry-count limit; acknowledged events removed, rejected singletons retained |
| Overflow | Reject newest admission with `dropped reason=storageCapacity`; never evict already owed events |

The publisher response from ws-sdk-config accepts the top-level field `analyticsBatchSize`:

```json
{"analyticsBatchSize": 15}
```

Use an integer from 1 to 15. Missing, null, blank, malformed or nonpositive values use **10**;
larger positive integers are capped at **15**. Numeric strings are tolerated. The field survives
publisher-config caching and has no public Dart/JS initialization override. The sender reads the
current limit before each attempt. If a new config lowers it, pending retries are split without
changing event IDs or the HTTP request already in flight. Removing the field restores 10.
Configuration updates do not reset the oldest-event deadline, request spacing or retry backoff.

**Persist now → batch → acknowledge exact IDs → remove.** Persistence is on one background worker,
with a synchronized journal write before an event becomes eligible for HTTP. New events do not
restart the batching deadline. Successful requests drain a backlog at the same rate limit; the
worker has no timer when empty. Normal single-event traffic waits at most 5 seconds before its
first attempt, unless an earlier request, backoff or storage failure is holding it.

Foreground, connectivity restoration and backgrounding request an early flush, without bypassing
the minimum spacing or backoff. The iOS background task gives a best-effort opportunity to finish;
neither platform relies on a termination callback. Persisted events resume on the next launch with
the original payload, `event_id`, `session_seq`, timestamp and `page_impression_id`. A restart resets
in-memory retry deadlines; the outbox, including quarantined IDs, remains durable.

**Collector contract required:** a 2xx response (including 204) must mean the **entire array** has
been durably accepted. There is no per-event acknowledgement protocol in the current endpoint.
If the backend accepts only part of an array, it must expose accepted/rejected event IDs before
partial acceptance can be supported safely. These client changes do not establish backend
atomicity or deduplication; confirm both with the collector owner before rollout.

Network errors, 401/403, 429 and server failures retain and retry the same batch. HTTP 400/413/422
split a rejected batch into smaller batches, respecting backoff and spacing. A rejected singleton
is marked **quarantined** in the journal, never counted as delivered; other events can proceed.
A singleton exceeding 128 KiB is also quarantined. There is no automatic replay of quarantine:
it requires investigation and an explicit repair/migration, so a persistently invalid payload
cannot consume requests forever. Quarantined payloads still count toward the 20 MiB capacity.

**Delivery is at least once.** If the server accepts a batch and its reply is lost, or a local
acknowledgement write fails, the same IDs are retried. The collector must deduplicate by `event_id`,
not `auction_id` or slot. Order analytically by `session_seq`, not HTTP arrival.

The existing JSONL format is preserved, with batched acknowledgement and quarantine markers.
Checkpoints run after at least 64 acknowledged events (one quarter of the remaining backlog
for large queues), when empty, or to bound journal overhead. Writes
are synchronized off the UI thread; checkpoints replace files atomically. An unreadable store is
retried rather than overwritten as empty. Existing larger backlogs drain without upgrade eviction.

**Limits are explicit:** no mobile SDK can promise zero loss through uninstall, disk failure,
storage exhaustion or process death before the asynchronous persistence step. Failed writes are
retried from a bounded 1 MiB memory fallback; events are not uploaded until persisted. The admission
queue is bounded to 1,024 events, with a diagnostic for rejection instead of silent replacement.
Do not interpret `queued` as a durability acknowledgement. Diagnostics report metadata/counts and
failure reasons without event payloads or identifiers. No physical-device performance or battery
claim is made from host/simulator timings.

### Batching verification (September 29, 2026)

- Full native suites: 293 Android tests and 320 iOS tests pass.
- Queue tests exercise the real sender with a controlled clock and fake HTTP completion; store
  tests reopen the real journal to check restart recovery, torn writes, capacity and quarantine.
- Transport tests serialize real event arrays through Retrofit / URLSession and stub responses;
  test traffic does not reach the live collector.
- Coverage includes the oldest-event deadline, count/UTF-8 byte limits, one in-flight request,
  two-second spacing, backoff/Retry-After, lost acknowledgements, local write failures, exact-ID
  acknowledgements, poison-batch splitting and no idle timer.
- Backend-limit coverage includes absent/malformed values, cached configuration, the 15-event
  ceiling, resetting to 10, and reducing a failed batch without losing or mis-acknowledging IDs.
- Flutter: 213 tests pass; the Android plugin and iOS example compile against matching local
  natives. Published pins are restored. Config tests analyze clean; the full analyzer still reports
  existing unrelated lint warnings/infos. RN uses native remote initialization unchanged.
- Queue tests control connectivity explicitly; real path-monitor callbacks cannot flush a test
  early. A separate connectivity test verifies early flushing still respects retry backoff.
- Mutation verification: removing the minimum request spacing fails the burst-drain test on
  both platforms. The unmodified implementation passes.
- No live-device battery or collector deduplication/atomic-acceptance validation was performed
  for this batching change. Historical live checks below apply to the previous immediate sender.

### Checking delivery in Flutter and React Native

Both bridges use the native collector transport. In Charles, look for
`api.adnz.co/api/ws-clickstream-collector/submit/batch`, with SSL proxying enabled for
`api.adnz.co:443` and the Charles certificate trusted on the test device. Current-branch builds
send arrays after up to 5 seconds or at the batch threshold. Flutter's Dart proxy override alone
does not route native analytics: the device's network proxy must also be configured.

With SDK diagnostics enabled (already enabled in the examples), filter device logs for
`AUDZ analytics`. The current branch reports:

* `queued`: the native queue received an event, with its type only.
* `restored`: pending events were recovered from disk, with count and oldest `event_timestamp`.
* `sending`: a batch is being submitted, with count, attempt number and oldest `event_timestamp`.
* `sent`: the HTTP request succeeded. This does not prove downstream dashboard ingestion.
* `failed`: the HTTP status or transport error code/type; `retryScheduled` gives the cooldown.
* `quarantined`: a rejected/oversized singleton is retained for inspection.
* `dropped`: new admission exceeded capacity; `persistenceFailed`: a local write failed.

These lines omit payloads, identifiers, targeting and consent strings, and are disabled when
SDK diagnostics are off. If `sending` appears without a decrypted request in Charles, check the
device proxy, certificate trust and capture filters. If `failed` appears, its status/code identifies
the transport failure without needing the event payload.

### Old timestamps and duplicate deliveries

`event_timestamp` is when the event was created, not when a POST succeeds. An event created on
September 25 can legitimately arrive on September 29 after offline time or a blocked/stalled
collector connection. The queue deliberately preserves its original payload, event ID, page ID
and timestamp. It does not expire owed events or relabel them as today's activity. The new
restore/send diagnostics expose backlog age without logging event payloads or identifiers.

Successful local acknowledgement removes exactly the delivered IDs; they must not reappear on
restart. Tests restore a September 25 event, verify its unchanged payload, acknowledge it, reopen
the actual disk store and verify no resend. If the server accepted a POST but its response was
lost, or local acknowledgement could not be saved, delivery can repeat with the **same** event ID.
The collector must deduplicate that ID and distinguish ingestion time from event time. Different
event IDs require examining producer callbacks and auction IDs; an old date alone does not prove
duplicates. The reported September 25 traffic cannot be diagnosed conclusively without payloads.

The released iOS 0.4.1 also fixes a queue stall in the 0.4.0 transport: an empty or non-JSON reply
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
