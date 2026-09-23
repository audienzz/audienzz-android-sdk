# Sanitized before/after payloads

"Before" rows are taken verbatim from the attached 2026-09-21 iOS run (a build of
`feature/page-impression-api` reporting `sdk_version 0.3.2`), with ids shortened and the app bundle
replaced. "After" rows are the same events under the fixes in this pass. Only the changed keys are
annotated; everything else is unchanged.

---

## 1. Envelope — `session_start_timestamp`, `device_id`

### Before

```json
{
  "event_type": "pageImpression",
  "company_id": "35",
  "device_id": "00000000-0000-0000-0000-000000000000",
  "session_id": "f51365db-…",
  "session_seq": 0,
  "session_start_timestamp": 1789978756503,
  "sdk_name": "ios",
  "sdk_version": "0.3.2",
  "source": "ios-sdk",
  "screen_name": "RemoteConfigViewController",
  "attributes": { "transport": "xhr", "website_id": "35" }
}
```

### After

```json
{
  "event_type": "pageImpression",
  "company_id": "35",
  "session_id": "f51365db-…",
  "session_seq": 0,
  "session_start_timestamp": 1789978756,
  "sdk_name": "ios",
  "sdk_version": "0.3.2",
  "source": "ios-sdk",
  "screen_name": "RemoteConfigViewController",
  "attributes": { "transport": "xhr", "website_id": "35" }
}
```

* `session_start_timestamp` — **seconds**, was milliseconds.
* `device_id` — **key gone**. ATT was not authorized, so there is no identity to report; the
  all-zero UUID is a sentinel, not a device.
* `company_id` — **unchanged at `35`**, and still platform-dependent. See §2 of the contract.

---

## 2. Banner funnel — `slot_reload`

Third load of the same slot.

### Before

```json
{ "event_type": "bidResponse", "attributes": {
    "auction_id": "7b780282-…", "bidder_code": "test", "cpm": "1.42",
    "result_code": "SUCCESS", "slot_reload": "2", "time_to_respond": "146",
    "ad_unit_code": "wuobgeuc", "website_id": "35" } }
```

### After

```json
{ "event_type": "bidResponse", "attributes": {
    "auction_id": "7b780282-…", "bidder_code": "test", "cpm": "1.42",
    "result_code": "SUCCESS", "slot_reload": "1", "time_to_respond": "146",
    "ad_unit_code": "wuobgeuc", "website_id": "35" } }
```

* `slot_reload` — **binary**. `"2"`, `"3"`, … no longer occur. Still a JSON **string**.
* `time_to_respond` — **unchanged, still milliseconds**. It is a duration, not a timestamp.

`bidRequest` and `noBid` now also carry `slot_reload`, which they did not before:

```json
{ "event_type": "bidRequest", "attributes": {
    "auction_id": "7b780282-…", "slot_reload": "1",
    "media_types": "[\"banner\"]", "ad_unit_code": "wuobgeuc", "website_id": "35" } }
```

---

## 3. `noBid` — unchanged, and that is the point

```json
{ "event_type": "noBid", "attributes": {
    "auction_id": "ce4a378a-…", "result_code": "NO_BIDS", "slot_reload": "0",
    "ad_type": "INTERSTITIAL", "ad_subtype": "MULTIFORMAT",
    "time_to_respond": "119", "website_id": "35" } }
```

* **No `bidder_code` key**, before or after. A no-bid is auction-level; Prebid does not report which
  bidders declined. It is never labelled `google` and never reuses the previous winner.
* `auction_id` is now **lowercase** — iOS interstitials used to emit `CE4A378A-…` while banners
  emitted lowercase, so the column could not be joined case-sensitively.
* `slot_reload` is new here.

---

## 4. Late callback from a creative that is still on screen

Not present in the attached log because it needs a replacement in flight, but this is the sequence
the fix is about: creative **A** is on screen, replacement **B** starts and responds, then A's
`viewability.success` fires.

### Before

```json
{ "event_type": "viewability.success", "attributes": {
    "auction_id": "B", "bidder_code": "seatB", "cpm": "9.99",
    "creative_id": "crB", "slot_reload": "1" } }
```

The event describes the creative the reader was looking at — **A** — but every economic field
belongs to **B**, which had not rendered and might never render.

### After

```json
{ "event_type": "viewability.success", "attributes": {
    "auction_id": "A", "bidder_code": "seatA", "cpm": "1.42",
    "creative_id": "crA", "slot_reload": "0" } }
```

The displayed creative's economics are snapshotted when Google confirms the render, and render
events read that snapshot. If B fails, nothing about A's reporting changes.

---

## 5. Android `device_id`

### Before (no Play Services, or read failure)

```json
{ "event_type": "adImpression", "device_id": "empty", … }
```

### After

```json
{ "event_type": "adImpression", … }
```

`"empty"` was a literal placeholder that aggregates, joins and counts in the collector exactly as if
it were a device. The key is now absent. The same applies when limit-ad-tracking is on, and — new —
the value is re-read rather than pinned for the process, so a revocation stops being reported
instead of continuing for the rest of the session.
