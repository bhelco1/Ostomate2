# Ostomate 2.0

[![CI](https://github.com/bhelco1/Ostomate2/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/bhelco1/Ostomate2/actions/workflows/ci.yml)
[![QA dashboard](https://img.shields.io/badge/QA-dashboard-blue)](https://github.com/bhelco1/Ostomate2/tree/test-dashboard)

Cross-platform (Android + iOS) ostomy supply tracker. Ground-up rewrite of
Ostomate v1 (retired) in Kotlin Multiplatform with Compose Multiplatform UI.

**Status:** see `STATUS.json` and `planning/05-dev-plan.md` for the current phase and
checklist and the rest of `planning/` for all design docs (product spec, architecture,
test strategy, security, business). Test results are published per merge to the
[`test-dashboard`](https://github.com/bhelco1/Ostomate2/tree/test-dashboard) branch,
grouped Unit / Integration / UI / CI-CD (`scripts/pi/update-dashboard.sh` serves it
on the Raspberry Pi).

## Modules

| Module | Contents | iOS targets |
|---|---|---|
| `shared` | Domain + data: Room KMP database, repository, deep-link parser, Koin data/platform modules. **No Compose.** | arm64, simulator-arm64 |
| `composeApp` | All Compose Multiplatform UI, ViewModels, theme, navigation, `initKoin`. Builds the `Shared.framework` Xcode embeds. | arm64, simulator-arm64 |
| `androidApp` | Thin Android launcher: Application class, MainActivity with deep-link handling. | — |
| `iosApp` | Thin Xcode project: SwiftUI entry, `onOpenURL` deep-link bridge. | — |

## Commands

```bash
./gradlew :androidApp:assembleDebug       # Android debug APK
./gradlew :androidApp:installDebug        # install on connected device
./gradlew :shared:testAndroidHostTest     # shared tests on JVM (fast)
./gradlew :shared:iosSimulatorArm64Test   # shared tests on the iOS simulator (Apple Silicon)

# iOS app (from iosApp/): build for device without signing
xcodebuild -project iosApp.xcodeproj -target iosApp -configuration Debug \
  -sdk iphoneos CODE_SIGNING_ALLOWED=NO build
```

Deep-link test (Android, with device):
```bash
adb shell am start -a android.intent.action.VIEW -d "ostomate://log?item=bag" com.ostomate.app
```

## Conventions

Architecture rules live in `planning/02-architecture.md`. Short version:
logic in `shared/commonMain`; UI state as one `UiState` per screen via `StateFlow`;
DI via Koin only; Room schema export ON with a migration test per version bump; no `!!`.
