# Ostomate 2.0 — Claude Code Project Instructions

Cross-platform (Android + iOS) ostomy supply tracker. Ground-up rewrite of Ostomate v1,
which lives at `~/Projects/Ostomate` and stays untouched in maintenance mode (it is
Bobby's daily driver until Phase 2 exit). The folders `~/Projects/Ostimate` and
`~/Projects/Ostimate copy` are old v1 backups — never modify them.

## Source of truth for all decisions

Planning docs live in `planning/` within this repo:
- `planning/01-product-spec.md` — features, platform requirements, testing requirements
- `planning/02-architecture.md` — module layout, tech stack, architecture rules
- `planning/04-test-plan.md` — test pyramid, coverage gates, E2E journeys
- `planning/05-dev-plan.md` — **current phase and checklist; update it as items complete**
- `planning/06-security-privacy.md` — privacy posture (local-first, no analytics), threat model
- `planning/07-business-plan.md` — cost rules (no new recurring costs without justification)

Store assets: `docs/privacy.html` (**the** privacy policy — it is what GitHub Pages actually
serves at https://bhelco1.github.io/Ostomate2/, via the `docs/index.html` redirect), and
`docs/store-listing.md`.

## Current status (2026-10-08)

**Phases 0–2.5 complete; Phase 3 (release prep) next** — it starts with steps only Bobby can do
(Apple Developer + Google Play accounts, the upload keystore and its GitHub secrets).
Release signing + the CI `build-release` job are already in (PR #38), skipping until the
keystore secrets exist. See `planning/05-dev-plan.md`.
JVM host gate: 88 shared tests + 64 composeApp tests (51 ViewModel/UiState + 10 Roborazzi
screenshot + 3 accessibility); the iOS sim runs 86 shared + 51 composeApp (counts from CI run
37857036492, 950b796; testpulse shows 163 distinct tests). JaCoCo floors gate every PR
(shared domain+data 91%, composeApp ViewModel+UiState 93%), and so does a Pitest mutation
score on the domain layer (95% floor, 98% measured). ktlint + detekt green.
Maestro E2E: 8 Android flows + 5 iOS flows green; both jobs report JUnit to testpulse via the
shared action `bhelco1/testpulse/.github/actions/report@v1`. The Android emulator build is
pinned (`emulator-build`), like `MAESTRO_VERSION`.

**Flaky E2E flows are root-caused, never quarantined** (decision 2026-10-08; procedure in
`planning/04-test-plan.md` "Flaky tests"). Every flake so far was a real defect: Maestro
2.6.1's false "App crashed or stopped" (fixed by 2.11.0), the iOS flow 01 wait that matched
the home-screen icon label "Ostomate" (never wait on the app name as text), flow 10's Back
closing only the keyboard. Read `e2e-diagnostics` / `maestro-ios-debug` before any rerun —
a rerun that goes green explains nothing.

**The biometric lock guards supply-count edits in Manage Supplies**, not the Settings screen.
Android 9–10 use `BIOMETRIC_WEAK | DEVICE_CREDENTIAL` because androidx.biometric rejects
STRONG | DEVICE_CREDENTIAL there (it silently dropped every locked edit until PR #43).

(Shared went 86 → 79 when FEAT-00 deleted `CsvExporter` and its 9 tests — not a regression —
then 79 → 82 with the negative-inventory regression tests.)

**Trust `test-results/*/TEST-*.xml` execution counts over any number written in a doc —
including this one.** Docs drift; the XML does not.

Screenshot baselines live in `composeApp/screenshots/` and are verified by
`:composeApp:testAndroidHostTest`. After an intended UI change, re-record with
`./gradlew :composeApp:testAndroidHostTest -Pscreenshot.record` and commit the PNGs —
anything captured must stay deterministic (no wall-clock reads; see `ScreenshotTest.kt`).

## Stack

Kotlin 2.3.21 · Compose Multiplatform 1.11.0 · Room KMP 2.8.4 (KSP 2.3.9) · Koin 4.2.1 ·
nav-compose · Gradle version catalog (`gradle/libs.versions.toml`) · min Android API 26.
Package `com.ostomate.app`, deep-link scheme `ostomate://log?item=bag|flange`.

## Modules — keep this boundary

| Module | Rule |
|---|---|
| `shared` | Domain + data only. **No Compose imports, ever.** iOS targets: arm64 + simulator-arm64. |
| `composeApp` | All CMP UI, ViewModels, theme, `initKoin`. iOS arm64-only (CMP dropped iosX64). Builds `Shared.framework` for Xcode. |
| `androidApp` / `iosApp` | Thin launchers + platform glue (deep-link entry, app icons, widgets later). |

## Hardware (Apple Silicon M1)

Full Compose Multiplatform iOS app runs in the local simulator. Use `iosSimulatorArm64`
targets for all local iOS work. `iosX64` was removed 2026-07-13 — nothing built or tested it.

**iOS secrets:** the Sentry DSN lives only in gitignored `iosApp/Configuration/Secrets.xcconfig`,
reaching the app via `Config.xcconfig` → `Info.plist` (`SentryDSN`). **xcconfig treats `//` as a
comment**, so escape it: `SENTRY_DSN=https:/$()/key@host/123`. Unescaped, it truncates to
"https:" and crash reporting silently dies.

## Commands

```bash
./gradlew :androidApp:assembleDebug       # Android APK
./gradlew :androidApp:installDebug        # install on connected device
./gradlew :shared:testAndroidHostTest     # shared tests, JVM (fast)
./gradlew :shared:iosSimulatorArm64Test   # shared tests, local iOS simulator (M1)
cd iosApp && xcodebuild -project iosApp.xcodeproj -target iosApp \
  -configuration Debug -sdk iphoneos CODE_SIGNING_ALLOWED=NO build   # iOS device build
adb shell am start -a android.intent.action.VIEW -d "ostomate://log?item=bag" com.ostomate.app
```

## Device state vs repo state (post-mortem 2026-07-12)

- Bobby's phone is the deliverable. "Merged to main" / gate-green / `assembleDebug` is NOT
  "on the phone" — only `:androidApp:installDebug` is, verified by
  `adb shell dumpsys package com.ostomate.app | grep lastUpdateTime` showing "now".
- Before debugging ANY reported on-device behavior, FIRST pin which build the device runs
  (lastUpdateTime vs commit times). Never reason from code on main until the installed
  build is confirmed current — a stale build makes correct code look broken.
- versionName is a static "1.0" and About says "v2.0.0-dev" regardless of commit — neither
  identifies a build. Trust lastUpdateTime only.
- Empty results from sandboxed `find`/`ls` over ~/Downloads etc. can be macOS TCC denials,
  not absence — treat "found nothing" as "can't see" until a direct path read confirms.

## CI (post-mortem 2026-07-13 — full story in PR #15)

- **A run existing is not a run happening.** An invalid workflow is rejected wholesale and
  shows as an ordinary red ✗ with 0 jobs / 0s, while PRs show *no checks* — which reads as
  "pending", not "broken". CI was dead 11 days that way. Confirm jobs actually executed.
- **Never mark a test item done without a run showing execution counts.** The E2E suite was
  recorded "✅ All Green" having never once passed; every defect in it was unpassable-by-
  construction. A gate that only runs post-merge gates nothing.
- **A dead gate hides other rot** — reviving it surfaced six further breakages, then two real
  app bugs. Assume it stopped catching things and go looking.
- **Pin tool versions.** `curl … | bash` installs *latest*, so a release can rot the suite with
  no commit of ours (`MAESTRO_VERSION` is pinned; bump deliberately).

## Architecture rules (enforced; full text in 02-architecture.md)

- UI → ViewModel (one `UiState` per screen via `StateFlow`, UDF) → UseCase → Repository → Room/DataStore
- DI via Koin only; platform code only via expect/actual; logic goes in `shared/commonMain` if it possibly can
- Room schema export stays ON (`shared/schemas/`); every version bump ships a migration + migration test
- No `!!`; all user-facing strings externalized; code lands with tests (see 04-test-plan.md)
- Parity before new features — check the current phase in 05-dev-plan.md before building anything new
- Privacy: local-first, no analytics SDKs. **No network calls except opt-in crash reporting
  (Sentry), which is OFF by default** — the app is not "zero outbound requests", and saying
  so on a store form would be false. Everything else stays on-device (06-security-privacy.md);
  new recurring costs need a written case in 07-business-plan.md
- A skipped/disabled test task looks like success — after touching test config, verify
  execution counts in `shared/build/test-results/*/TEST-*.xml`, not just BUILD SUCCESSFUL

## testpulse reporting

This project reports test results to testpulse and conforms to the
[testpulse reporting standard, v1](https://github.com/bhelco1/testpulse/blob/main/docs/reporting-standard.md)
(in a local checkout: `../testpulse/docs/reporting-standard.md`).
Any change to CI test jobs, test frameworks or result output is a reporting change:
follow the standard's Change checklist and update `projects/ostomate2.yaml` in testpulse.
