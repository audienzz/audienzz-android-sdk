# Changelog

All notable changes to the Audienzz Android SDK are documented here. This project
adheres to [Semantic Versioning](https://semver.org/).

## [0.1.7] — 2026-08-19

Hardening release: fixes the refresh-loop, lifecycle, callback, remote-config, and
targeting issues found in the July 2026 audit. No breaking changes. (0.1.6 was skipped.)

### Fixed

- **Duplicate/parallel banner refresh loops** — a banner no longer spawns extra
  self-re-arming Prebid refresh loops on prefetch/scroll/resume, which was inflating
  ad-request volume and invalid-traffic exposure.
- **Prefetched off-screen banners refreshing forever** — a banner loaded off-screen no
  longer auto-refreshes at 0% viewability; refresh stops until it enters the viewport.
- **Rendering-API interstitial callbacks dying after the first event** — publisher
  listeners (`onAdDisplayed/Clicked/Closed/Failed`) keep firing, so `onAdClosed`-chained
  reloads work again.
- **Duplicated banner callbacks & analytics after refresh** — click/impression callbacks
  and events are no longer multiplied by the number of refreshes.
- **Refresh requests firing while backgrounded** — `onPause()` now cancels the pending
  refresh; no ad requests off-screen or in the background.
- **Publisher `FullScreenContentCallback` overwritten** — interstitial/rewarded preserve a
  callback you set on the ad instead of clobbering it.
- **Detached/recycled ad views treated as visible** — prevents per-frame refresh storms in
  `RecyclerView` reuse; one-shot visibility listeners no longer leak.
- **Remote-config banners blank on cold start** — the first banner waits for the initial
  config fetch instead of returning empty with no retry.
- **One bad remote config discarding all configs** — ad-unit configs are decoded
  individually; a malformed entry is skipped, not fatal.
- **Stale refresh requests** — refreshed banner auctions carry the current
  PPID/consent/targeting instead of values frozen at first load.
- **Consent set before init silently dropped** — GDPR/COPPA/consent values set before
  `initializeSdk` are buffered and applied once init completes.
- **Remote-config interstitial silent failures** — reported via `Events.onError(reason)`;
  `destroy()` releases the loaded ad.
- **Reloading a remote banner leaking a refresh loop** — the previous ad unit/view is
  destroyed before rebuilding.
- **Sticky ad wrapper overwriting the host's scroll listener** — the wrapper now attaches
  additively to your `NestedScrollView`.

### Changed

- **Global ORTB is now a single canonical merge** of publisher ORTB + schain +
  custom-targeting keywords + SDK identity — targeting/schain calls no longer wipe each
  other. No action needed; QA may want to confirm a bid-request payload.
- **SDK retains the application context** instead of the Activity passed to
  `initializeSdk`, fixing an Activity leak.

### Added

- **`AudienzzAdViewHandler.destroy()`** — stops smart refresh, cancels pending refreshes,
  and destroys the underlying Prebid ad unit. Call it from your view/Activity teardown
  (e.g. `onDestroy` / `onViewRecycled`).
- **`AudienzzRemoteConfigInterstitial.Events.onError(reason: String)`** — optional
  (default no-op) callback for config-missing / SDK-not-initialized / no-Activity-to-show
  cases.

### Notes

- Both API additions are additive and non-breaking.
- The rendering-API interstitial now hands your listener the **same**
  `AudienzzInterstitialAdUnit` instance across callbacks (previously a new wrapper each
  time).
- Muted-by-default GMA volume (introduced in 0.1.5) is unchanged.

## [0.1.5] — 2026-07-16

### Changed

- Mute GMA ads by default via `setAppMuted` (backend-driven): ads are muted when the
  backend-resolved app volume is 0 and audible when it is non-zero.

## [0.1.4] — 2026-07-06

### Added

- Backend-driven sticky-ads configuration.
- SDK name and version in keywords targeting.

### Fixed

- Replaced dev-api URLs with production endpoints.

[0.1.7]: https://github.com/audienzz/audienzz-android-sdk/compare/0.1.5...0.1.7
[0.1.5]: https://github.com/audienzz/audienzz-android-sdk/compare/0.1.4...0.1.5
[0.1.4]: https://github.com/audienzz/audienzz-android-sdk/compare/0.1.3...0.1.4
