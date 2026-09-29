# Analytics review and fixes — September 29, 2026

Scope: Android and iOS `feature/durable-analytics-batching`, primarily Original/Remote ads.
The changes are not in published Android 0.3.1 / iOS 0.4.1. No bridge API changes are required
for these producer fixes; Flutter and React Native must consume the subsequent native releases.

## Reconciliation with the external review

| Claim | Verified result and action |
|---|---|
| Unique event IDs rule out in-process duplicates | Incorrect. Repeated callbacks can generate different IDs for one impression. Android banner/interstitial/rewarded callbacks are now guarded per creative/ad. Separate clicks remain separate events. |
| Same-screen page reports can trigger another auction | True, but an explicit report means a new visit. Automatic time-based deduplication would suppress valid returns/content changes. Documented one navigation owner and no duplicate manual calls alongside bridge helpers. |
| `viewability.start` repeats after interrupted exposure | Intentional. A start represents an exposure attempt; success is terminal for that creative. Preserved this contract. |
| At-least-once transport can resend an event | True. Preserve the same `event_id` on retry; collector deduplication is required. Client-only code cannot establish exactly-once ingestion. |
| Android fullscreen has no Prebid completion guard | Not true at this branch tip. `AudienzzAdUnit.fetchDemand` already settles once and retires old callbacks, including for fullscreen. The SDK's interval setter does not arm Prebid timers. Added a regression test rather than a second guard/state machine. |

## Confirmed defects fixed

- Banner viewability now checks active-page/foreground/visibility eligibility and cancels on
  release, destruction and replacement. iOS measurement uses the concealment/clipping geometry.
  Background polling/pre-draw cannot rearm a success timer.
- Measurement retains the creative's auction/page context. A replacement cannot inherit its
  predecessor's timer. A visible creative can still qualify while a replacement auction is pending.
  Duplicate Google load callbacks for the same known response ID preserve ongoing measurement.
- iOS Original fullscreen handlers retain the loaded ad's economics and page context when their
  owner is reused. Rewarded viewability carries that context, and RemoteInterstitial now emits
  viewability events. Fullscreen timers belong to one presentation.
- Android guards repeated impressions and terminal Google load callbacks. Rewarded analytics is
  installed before the publisher's loaded callback, covering synchronous presentation there.
- First Google-filled banner impressions retain `slot_reload=0` on both platforms.
- No-bid `bidResponse` events retain their auction ID. iOS success-without-bidder is reported as
  `noBid(NO_BIDS)`. Android fullscreen bypassing failed Prebid initialization emits no synthetic
  Prebid request/response/no-bid events; Google's handoff remains intact.

## September 25 timestamps

The reported field is `event_timestamp`. Persisted unsent events retain their creation time,
including across app restarts. September 25 events received later can therefore be valid backlog.
We did not delete old events or rewrite timestamps. Restore/send diagnostics now show the oldest
event timestamp; disk-reopen tests prove that an acknowledged old event is not resurrected.

No sample event IDs/payloads were available to establish the actual cause of the reported traffic.
Same ID on multiple deliveries means replay; different IDs require inspecting producer callbacks
and auction context. Collector deduplication/atomic batch acceptance and physical-device delivery
remain separate verification work.

See [the field and delivery contract](analytics-contract.md) for the precise event semantics.

## Follow-up batching review

The external review found no P0/P1 and independently reproduced the 314 Android / 342 iOS
auction-batching baseline. Its four P2 observations were valid:

- The flush regression only covered an event arriving during HTTP, missing an arrival while a
  forced batch waited for request spacing. Both platforms now test B at t=2s, A1 + flush at
  t=2.1s, A2 at t=2.5s: the t=4s POST must contain only A1. Production filtering was already correct.
- Quarantine could permanently consume all capacity. Rejected payloads now have separate bounded
  diagnostic retention (100 events / 1 MiB); durable discard markers prune only rejected records.
  Pending deliverable events keep the 20 MiB quota and are never evicted for newer events.
- Restored backlog could delay fresh events for the full drain. Due restored/fresh groups now
  alternate while preserving auction grouping, debounce, caps, one HTTP request and spacing.
  Failed HTTP plans keep their backoff/priority; this does not promise a two-second delivery SLA.
- A failed local acknowledgement retried an already-accepted POST. Known HTTP successes now retry
  only local acknowledgement, with capped backoff, while other persisted events can proceed.
  The durable copy stays intact until acknowledgement; process death can still cause replay.

Foreground recovery/page-impression semantics are explicitly outside this follow-up. Flutter/RN
code and native release versions are unchanged. Collector atomic acceptance and ID deduplication
remain required; client tests do not independently establish the live backend implementation.

## Validation

- Producer/lifecycle baseline: **306 Android / 334 iOS**; auction-batching baseline: **314 / 342**.
- Follow-up full suites: **321 Android / 349 iOS passed**, zero failures.
- Mutation verification on both platforms: sending the whole auction during an early flush,
  restoring backlog-first scheduling/HTTP retries after acknowledgement failure, and removing
  quarantine quota isolation/retention each fail the corresponding new regressions. Sources
  were restored before the successful full-suite runs.
- Persistent acknowledgement failure is tested over ten simulated minutes. Physical journal
  reopen tests cover process death after HTTP acceptance but before local acknowledgement.
- Regressions drive the handlers' installed Google callbacks, real page transitions and lifecycle
  notifications, with controlled demand/network/visibility fixtures. No live ad requests are needed.
- Mutation checks: removing Android impression deduplication produces three impressions instead
  of one; removing the same-response load guard stops measurement on both platforms; reverting
  iOS fullscreen attribution to its mutable owner reports the replacement auction. Each targeted
  test fails with the mutation and passes after restoration.
- The visible-creative test also proves that starting a replacement auction alone does not discard
  its predecessor's ongoing measurement. First-load flags, no-bid identities and disk replay after
  acknowledgement are checked separately.
- No device/live-collector validation was performed for these changes. Queue delivery remains
  at-least-once, and full Rendering-API event coverage is outside this review's scope.
