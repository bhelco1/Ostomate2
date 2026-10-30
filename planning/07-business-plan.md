# Ostomate 2.0 — Business Plan & Cost Rules

## Cost Rules

> Any new recurring cost requires a written justification in this file before the dependency is added.

**Rule:** No new recurring costs without documented justification.

### Current Costs

| Service | Cost | Justification |
|---|---|---|
| GitHub Actions — ubuntu-latest | Free tier | Android build + JVM tests |
| GitHub Actions — macos-latest | 10× billing rate | iOS tests — gated to non-PR only to limit cost |
| Apple Developer Program | $99/year | Required for App Store distribution |
| Google Play Developer | $25 one-time | Required for Play Store distribution |
| Firebase (Crashlytics) | Free tier (Spark) | Crash reporting only; no paid features |

### Billing Impact of CI Choices

macOS runners bill at 10× the ubuntu-latest rate. The iOS CI job is intentionally gated:
```yaml
if: github.event_name != 'pull_request'
```
This means iOS tests run on `main` merges and manual dispatch only — not on every PR push. This is a deliberate cost decision. Do not remove this gate without documenting the monthly cost impact here.

## App Pricing

**Free at launch.** No in-app purchases. No subscription. This is a personal health utility.

**Later, maybe: an optional tip** ("support the developer" in-app purchase); it unlocks nothing
(Bobby, 2026-10-30). Not in v1.0. Before building it:
- It needs Play Billing / StoreKit, a third network path: update `06-security-privacy.md`,
  the privacy policy and the Data Safety / App Privacy answers in the same change.
- Apple's Paid Apps agreement and a Google payments profile (tax + bank) come first.

**Never switch the Play listing to paid-upfront.** Google Play does not allow an app published
free to become paid; it would need a new listing. Price changes, paid → free and adding
in-app purchases stay possible on both stores.

## Store Presence

### Google Play
- Internal Testing → Closed Testing → Production rollout
- 20% rollout for 48h before expanding to 100%
- Monitor crash-free rate via Firebase before expanding

### Apple App Store
- TestFlight for beta testing
- App Store review required before production release
- Review timeline: typically 24–48h

## Revenue

None. This app is built for personal use and published as a free utility.

## Decisions Log

| Question | Decision | Date |
|---|---|---|
| Pricing model | Free — no monetization | 2026-06 |
| Pricing model (revisited) | Free at launch; optional tip purchase may come later. No subscription, no paid download | 2026-10-30 |
| Analytics | None — privacy-first | 2026-06 |
| Cloud sync | Deferred — local-only for v1.0 | 2026-06 |
| macOS CI gate | Non-PR only — 10× cost mitigation | 2026-06 |
