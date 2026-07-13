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

**Phases 0–2 complete; Phase 2.5 (test hardening) in progress — 2.5.1–2.5.5 done.**
Shared: 79 tests on JVM host + iOS sim. ViewModels: 47 tests on both targets.
(Shared dropped 86 → 79 when FEAT-00 deleted `CsvExporter` and its 9 tests — not a regression.)
JaCoCo coverage floors gate every PR (shared domain+data 91%, composeApp
ViewModel+UiState 93%). ktlint + detekt green. See `planning/05-dev-plan.md`.

**Trust `test-results/*/TEST-*.xml` execution counts over any number written in a doc —
including this one.** Docs drift; the XML does not.

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

## CI: "red" and "no checks" are both ambiguous (post-mortem 2026-07-13)

- CI was **completely dead for 11 days** (2026-07-02 → 07-13) and nobody noticed. A
  step-level `if: ${{ secrets.X != '' }}` is invalid — `secrets` is not an allowed
  context in a step `if` — so GitHub **rejected the whole workflow file and ran nothing**.
- It hid because a rejected workflow still appears in the Actions list as an ordinary
  red ✗ — just **0 jobs, 0s duration** — and PRs show **no checks at all**, which reads
  as "checks haven't started yet," not "CI is broken." FEAT-00/01/02, source-tagging and
  the app icon all merged with CI never having run.
- **Before trusting a green/red signal, confirm jobs actually executed** (nonzero
  duration, real job list). A run existing is not a run happening.
- **A dead gate hides other rot.** With CI revived, two further breakages surfaced that
  had been invisible: the E2E emulator never booted (missing `-no-window` /
  `-gpu swiftshader_indirect` on a headless runner), and every Maestro flow used
  `timeout:` on `assertVisible`, which Maestro 2.x rejects outright. When reviving a
  dead gate, assume it stopped catching things and go looking.
- **Pin tool versions in CI.** `curl get.maestro.mobile.dev | bash` installs *latest*, so
  a Maestro release can break the suite with no commit of ours. `MAESTRO_VERSION` is now
  pinned; bump it deliberately.

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
