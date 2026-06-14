# Ostomate 2.0

Cross-platform (Android + iOS) ostomy supply tracker. Ground-up rewrite of
[Ostomate v1](../Ostomate) in Kotlin Multiplatform with Compose Multiplatform UI.

**Status: Phase 0 spike** — see `../Ostomate/ostimate-2.0/05-dev-plan.md` for the full
plan and `ostimate-2.0/` in the v1 repo for all design docs (product spec, architecture,
UI/UX with HTML mockups, test plan, security, business).

## Modules

| Module | Contents | iOS targets |
|---|---|---|
| `shared` | Domain + data: Room KMP database, repository, deep-link parser, Koin data/platform modules. **No Compose.** | arm64, simulator-arm64, **x64** |
| `composeApp` | All Compose Multiplatform UI, ViewModels, theme, navigation, `initKoin`. Builds the `Shared.framework` Xcode embeds. | arm64, simulator-arm64 |
| `androidApp` | Thin Android launcher: Application class, MainActivity with deep-link handling. | — |
| `iosApp` | Thin Xcode project: SwiftUI entry, `onOpenURL` deep-link bridge. | — |

**Why `shared` has iosX64 and `composeApp` doesn't:** Compose Multiplatform 1.11+
dropped Intel-simulator artifacts. The dev machine is an Intel Mac, so logic tests run
locally on the x64 simulator (`:shared:iosX64Test`), while the full iOS app builds for
real devices locally and for arm64 simulators in CI.

## Commands

```bash
./gradlew :androidApp:assembleDebug       # Android debug APK
./gradlew :androidApp:installDebug        # install on connected device
./gradlew :shared:testAndroidHostTest     # shared tests on JVM (fast)
./gradlew :shared:iosX64Test              # shared tests on iOS simulator (Intel Mac)
./gradlew :shared:iosSimulatorArm64Test   # same, Apple-Silicon hosts / CI

# iOS app (from iosApp/): build for device without signing
xcodebuild -project iosApp.xcodeproj -target iosApp -configuration Debug \
  -sdk iphoneos CODE_SIGNING_ALLOWED=NO build
```

Deep-link test (Android, with device):
```bash
adb shell am start -a android.intent.action.VIEW -d "ostomate://log?item=bag" com.ostomate.app
```

## Conventions

Architecture rules live in `../Ostomate/ostimate-2.0/02-architecture.md`. Short version:
logic in `shared/commonMain`; UI state as one `UiState` per screen via `StateFlow`;
DI via Koin only; Room schema export ON with a migration test per version bump; no `!!`.
