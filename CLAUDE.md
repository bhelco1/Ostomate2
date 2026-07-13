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

Store assets: `docs/privacy-policy.md`, `docs/store-listing.md`.

## Current status (2026-07-13)

**Phases 0–2 complete; Phase 2.5 (test hardening) in progress — 2.5.1–2.5.5 + 2.5.7 done.**
JVM host gate: 79 shared tests + 57 composeApp tests (47 ViewModel/UiState + 10 Roborazzi
screenshot tests). JaCoCo coverage floors gate every PR (shared domain+data 91%, composeApp
ViewModel+UiState 93%). ktlint + detekt green. See `planning/05-dev-plan.md`.

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
| `shared` | Domain + data only. **No Compose imports, ever.** Targets include iosX64 so tests run on this Intel Mac's simulator. |
| `composeApp` | All CMP UI, ViewModels, theme, `initKoin`. iOS arm64-only (CMP dropped iosX64). Builds `Shared.framework` for Xcode. |
| `androidApp` / `iosApp` | Thin launchers + platform glue (deep-link entry, app icons, widgets later). |

## Hardware (Apple Silicon M1)

Full Compose Multiplatform iOS app runs in the local simulator. Use `iosSimulatorArm64`
targets for all local iOS work. The `iosX64` target in `shared` remains for CI
compatibility but is not needed locally.

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

## Architecture rules (enforced; full text in 02-architecture.md)

- UI → ViewModel (one `UiState` per screen via `StateFlow`, UDF) → UseCase → Repository → Room/DataStore
- DI via Koin only; platform code only via expect/actual; logic goes in `shared/commonMain` if it possibly can
- Room schema export stays ON (`shared/schemas/`); every version bump ships a migration + migration test
- No `!!`; all user-facing strings externalized; code lands with tests (see 04-test-plan.md)
- Parity before new features — check the current phase in 05-dev-plan.md before building anything new
- Privacy: local-first, no analytics SDKs, no network calls (06-security-privacy.md); new
  recurring costs need a written case in 07-business-plan.md
- A skipped/disabled test task looks like success — after touching test config, verify
  execution counts in `shared/build/test-results/*/TEST-*.xml`, not just BUILD SUCCESSFUL
