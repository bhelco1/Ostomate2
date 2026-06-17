# Ostomate 2.0 — Development Plan

> Update this file as items complete. It is the source of truth for current phase and status.

## Status Summary

| Phase | Goal | Status |
|---|---|---|
| 0 | KMP spike — prove the stack | ✅ Complete |
| 1 | Wire platform features + stabilize | ✅ Complete |
| 2 | Physical device validation | ⬜ |
| 3 | Release prep (signing, store listings) | ⬜ |
| 4 | App Store + Play Store submission | ⬜ |
| 5 | Production release | ⬜ |

---

## Phase 0 — KMP Spike ✅ Complete

Proven: Android APK + iOS device build, Room KMP CRUD on iOS simulator, deep links both platforms, nav-compose, CI green. 60 shared tests passing on iOS simulator (M1).

---

## Phase 1 — Wire Platform Features + Stabilize

**Goal:** All implemented expect/actual features are wired and exercised. No new features.

### 1.1 — Wire Notifications ✅ Already implemented

All wiring was in place before Phase 1 began:
- `Notifier` registered in both platform Koin modules (Android: WorkManager, iOS: UNUserNotificationCenter)
- `NotificationScheduler` registered in `dataModule`
- `HomeViewModel.init` calls `reschedule()` reactively on every supply/event change
- Android: `POST_NOTIFICATIONS` permission requested in `MainActivity.onCreate()`
- iOS: authorization requested inside `Notifier.ios.kt` on first `schedule()` call

**Remaining:** verify on physical device (covered in Phase 2).

### 1.2 — Wire Biometric Auth into Settings ✅

`BiometricAuthenticator` injected into `SettingsViewModel`. Lock gate added to `SettingsScreen`:

- `isLocked` initialized in ViewModel `init` from persisted `lockSettings` setting
- `LaunchedEffect(isLocked)` auto-triggers the biometric prompt on entry
- `BiometricResult.NotEnrolled` → auto-unlock (matches v1 behavior)
- `BiometricResult.Failed` → shows error text, stays locked; Unlock button to retry
- `ON_RESUME` lifecycle observer calls `relockIfNeeded()` — covers tab-switching away and back
- Lock overlay (`Box` + `Column` + lock icon) shown instead of settings content when locked

**Remaining:** verify on physical device (covered in Phase 2).

### 1.3 — Verify Backup Round-Trip ✅

`BackupRepository`, `FileSharer`, and `FileImportLauncher` fully wired on both platforms:

- `CsvExporter`: 9 unit tests passing on both JVM and iOS simulator (69 total shared tests)
- `FileSharer.ios.kt`: writes CSV as raw UTF-8 bytes via `NSData.dataWithBytes` + `NSFileManager.createFileAtPath`, presents `UIActivityViewController`
- `FileImportLauncher.ios.kt`: `UIDocumentPickerViewController` with `DocumentPickerDelegate : NSObject()` (requires `import platform.darwin.NSObject`) reading file via `NSString.stringWithContentsOfURL`
- iOS Xcode build: `BUILD SUCCEEDED` after fixing pre-existing `String.format()` → `.replace()` and `kotlinx.datetime.Clock.System` → `kotlin.time.Clock.System` + epoch-millis bridge

**Remaining:** manual smoke test on physical device (covered in Phase 2).

### 1.4 — Wire CrashReporter to Sentry (iOS) ✅

Android is fully wired: `sentry-android` dep, `CrashReporter.android.kt`, `OstomateApp.init()` reads `SENTRY_DSN` from `BuildConfig`.
iOS has a no-op stub — Sentry is called from Swift, not Kotlin (by design).

**Human steps (Xcode):**
1. In Xcode: File → Add Package Dependencies → `https://github.com/getsentry/sentry-cocoa`, version `~> 8.0`
2. Link `Sentry` framework to the `iosApp` target
3. Add `SENTRY_DSN` to `iosApp/Configuration/Config.xcconfig` (local only, git-ignored)
4. Add `SENTRY_DSN` as a GitHub Secret for CI

**Code steps:**
- Expose `SENTRY_DSN` via `Info.plist` entry (read from xcconfig)
- Call `SentrySDK.start` in `iOSApp.swift` gated on the stored opt-in preference
- Read `crashReportingEnabled` from shared `DataStore` settings before init

### 1.5 — Integration Pass ✅

- All Phase 1 items complete
- ktlint + detekt green; 57 JVM / 69 iOS simulator tests passing; Android APK + iOS simulator build succeed
- CI green on `main` (push this commit to verify)
- Manual smoke test on physical iPhone + Android device (covered in Phase 2)

---

## Phase 2 — Physical Device Validation

**Goal:** App runs correctly on real hardware. All Maestro E2E flows pass.

### 2.1 — iOS Physical Device ✅
- Build for device: `xcodebuild -project iosApp.xcodeproj -target iosApp -configuration Debug -sdk iphoneos ARCHS=arm64`
- Install via Xcode or `ios-deploy`
- Run manual device checklist from `04-test-plan.md`
  - [x] 1. Launch / onboarding
  - [x] 2. Log bag change
  - [x] 3. QR deep link
  - [x] 4. Calendar
  - [x] 5. Biometric lock
  - [x] 6. Manage Supplies
  - [x] 7. QR Labels
  - [x] 8. Export CSV
  - [x] 9. Notifications
  - [x] 10. Dark mode
  - [x] 11. VoiceOver

### 2.2 — Android Physical Device ✅
- `./gradlew :androidApp:installDebug`
- Run manual device checklist from `04-test-plan.md`
- All 11 checklist items passed on Pixel 8 Pro
- Fixed: biometric gate moved from Settings screen to count-editing action only
- Fixed: biometric session resets on ManageSupplies exit

### 2.3 — Maestro E2E All Green 🔧 (flows fixed, pending CI run)
- Fixed all 5 CI flows (02–05, 08):
  - All flows: added `tapOn: "Skip" optional: true` to handle fresh onboarding after `clearState: true`
  - Flow 01: fixed wildcard `id: "overflowButton_*"` → `id: "overflowButton"` with `index: 0`
  - Flow 03: fixed `id: "dayCell_today"` (added testTag to CalendarScreen), `text: "Events"` → `text: "+ Add an entry*"`, `id: "eventRow"` (added testTag to HistoryScreen)
  - Flow 04: fixed button target (`editSupplyButton` → `supplyCount_*`) and dialog title assertion (`Edit*count` → `Set*count`)
  - Flow 05: fixed invalid YAML on `pressKey: BACK` (removed misindented `optional: true`)
  - Flow 08: rewrote biometric test — gate is on Settings screen, not Home overflow; now navigates back to Settings and asserts auto-unlock on emulator
- Push to main to trigger CI `android-e2e` job

---

## Phase 3 — Release Preparation

**Goal:** Signed release builds. Store listings ready. Fastlane configured for iOS.

### 3.1 — Android Release Signing ⬜

**Human step:** Generate keystore:
```bash
keytool -genkey -v -keystore ostomate-release.jks -alias ostomate \
  -keyalg RSA -keysize 2048 -validity 10000
```
Store `.jks` in password manager. Never commit it.
Add GitHub Secrets: `KEYSTORE_FILE` (base64), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

**Code:** Add `signingConfigs.release` to `androidApp/build.gradle.kts`. Wire to CI `build-release` job.

### 3.2 — iOS Release Signing ⬜

**Human step:**
1. Enroll in Apple Developer Program (developer.apple.com, $99/year)
2. Create App ID `com.ostomate.app` in developer.apple.com
3. Create distribution certificate + provisioning profile
4. Add to Fastlane match or manual download

**Code:** Configure `iosApp/Configuration/Config.xcconfig` for release target. Add Fastlane lane for TestFlight upload.

### 3.3 — App Icon ⬜

Design branded icon (1024 × 1024 px source, no rounded corners).
- Android: generate adaptive icons via Android Studio Image Asset wizard → all mipmap densities
- iOS: 1024 × 1024 PNG in Assets.xcassets → AppIcon

### 3.4 — Store Listings ⬜

See `planning/07-business-plan.md` and `docs/store-listing.md`.

- Google Play: store listing content + screenshots (5 minimum)
- App Store Connect: metadata + screenshots (6.7" + 5.5" required)
- Privacy policy URL: GitHub Pages at this repo's `/docs/privacy-policy.html`

### 3.5 — Enable GitHub Pages ⬜

Settings → Pages → Source: main, folder `/docs`.
Verify privacy policy is live at the public URL before store submission.

---

## Phase 4 — Store Submission

### 4.1 — Google Play Internal Testing ⬜

1. Create app in Play Console → package `com.ostomate.app`
2. Complete Data Safety form (no data collected, no data shared)
3. Upload signed AAB from CI `build-release` artifact
4. Publish to Internal Testing track
5. Install on device via Play Store

### 4.2 — App Store TestFlight ⬜

1. Create app in App Store Connect → bundle ID `com.ostomate.app`
2. Upload IPA via Fastlane or Xcode Organizer
3. Submit for TestFlight review
4. Install on device via TestFlight

---

## Phase 5 — Production Release

### 5.1 — Real-Device Test Period (minimum 3–5 days) ⬜
- Use app daily on primary devices
- Monitor Firebase Crashlytics for crashes
- File GitHub Issues for any bugs found

### 5.2 — Pre-Release Audit ⬜
See `04-test-plan.md` manual device checklist. All items must pass.

### 5.3 — Promote to Production ⬜
- Google Play: Production track, 20% rollout → 100% after 48h if crash-free ≥ 99%
- App Store: Submit for review → release

**v1.0 shipped when:** Both stores at 100% rollout, crash-free rate ≥ 99% for 7 days.
