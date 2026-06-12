# Ostimate 2.0 — Claude Code Project Instructions

Cross-platform (Android + iOS) ostomy supply tracker. Ground-up rewrite of Ostomate v1,
which lives at `~/Projects/Ostomate` and stays untouched in maintenance mode (it is
Bobby's daily driver until Phase 2 exit). The folders `~/Projects/Ostimate` and
`~/Projects/Ostimate copy` are old v1 backups — never modify them.

## Source of truth for all decisions

The full planning doc set lives in the v1 repo: `~/Projects/Ostomate/ostimate-2.0/`
- `01-product-spec.md` — features, parity matrix, personas
- `02-architecture.md` — module layout, tech stack, architecture rules
- `03-ui-ux-design.md` + `design-mockups/index.html` — design system and visual mockups
- `04-test-plan.md` — test pyramid, coverage gates, E2E journeys
- `05-dev-plan.md` — **current phase and checklist; update it as items complete**
- `06-security-privacy.md` — privacy posture (local-first, no analytics), threat model
- `07-business-plan.md` — cost rules (no new recurring costs without justification)

Specialist agents (product-manager, uiux-designer, mobile-developer, qa-engineer,
security-engineer, cfo, release-engineer, accessibility-specialist) are defined in
`~/Projects/Ostomate/.claude/agents/`.

## Current status (2026-06-12)

**Phase 0 spike, mostly done.** Proven: Android APK + unsigned iOS device build, Room KMP
CRUD on iOS simulator (17 tests green), deep links both platforms, nav-compose, CI
skeleton. Remaining: notifications + biometrics expect/actual, first migration test,
push to GitHub (no remote yet — CI never exercised), run on physical devices.

## Stack

Kotlin 2.3.21 · Compose Multiplatform 1.11.0 · Room KMP 2.8.4 (KSP 2.3.9) · Koin 4.2.1 ·
nav-compose · Gradle version catalog (`gradle/libs.versions.toml`) · min Android API 26.
Package `com.ostimate.app`, deep-link scheme `ostimate://log?item=bag|flange`.

## Modules — keep this boundary

| Module | Rule |
|---|---|
| `shared` | Domain + data only. **No Compose imports, ever.** Targets include iosX64 so tests run on this Intel Mac's simulator. |
| `composeApp` | All CMP UI, ViewModels, theme, `initKoin`. iOS arm64-only (CMP dropped iosX64). Builds `Shared.framework` for Xcode. |
| `androidApp` / `iosApp` | Thin launchers + platform glue (deep-link entry, app icons, widgets later). |

## Hardware constraint (Intel Mac, i7-9750H)

CMP 1.11+ has no Intel-simulator artifacts, so the full app cannot run in the local iOS
simulator. Logic tests CAN: `./gradlew :shared:iosX64Test`. Full iOS app: build for a
physical iPhone, or rely on CI's Apple-Silicon runners (`iosSimulatorArm64`).

## Commands

```bash
./gradlew :androidApp:assembleDebug       # Android APK
./gradlew :androidApp:installDebug        # install on connected device
./gradlew :shared:testAndroidHostTest     # shared tests, JVM (fast)
./gradlew :shared:iosX64Test              # shared tests, local iOS simulator
cd iosApp && xcodebuild -project iosApp.xcodeproj -target iosApp \
  -configuration Debug -sdk iphoneos CODE_SIGNING_ALLOWED=NO build   # iOS device build
adb shell am start -a android.intent.action.VIEW -d "ostimate://log?item=bag" com.ostimate.app
```

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
