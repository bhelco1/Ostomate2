# Ostomate 2.0 — Development Plan

> Update this file as items complete. It is the source of truth for current phase and status.

## Status Summary

| Phase | Goal | Status |
|---|---|---|
| 0 | KMP spike — prove the stack | ✅ Complete |
| 1 | Wire platform features + stabilize | 🔄 In progress |
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

### 1.1 — Wire Notifications ⬜

`Notifier` and `NotificationScheduler` exist but are not called at startup.

**Android:**
- Call `NotificationScheduler.schedule()` from `OstimatApplication.onCreate()`
- Request `POST_NOTIFICATIONS` permission on Android 13+ (API 33+) at first launch
- Verify WorkManager job fires when days remaining < threshold

**iOS:**
- Call `NotificationScheduler.schedule()` from `iOSApp.init()` or `MainViewController`
- UNUserNotificationCenter authorization is requested on first `Notifier.schedule()` call (already in the actual impl)
- Verify notification fires on device

**Test:** Reduce a supply type's inventory count to trigger the threshold. Verify notification appears on both platforms.

### 1.2 — Wire Biometric Auth into Settings ⬜

`BiometricAuthenticator` expect/actual is implemented but verify it is properly integrated into `SettingsViewModel` and `SettingsScreen`.

- Settings screen must be locked on every visit
- Unlock via `BiometricAuthenticator.authenticate()`
- `BiometricResult.NotEnrolled` → auto-unlock (matches v1 behavior)
- `BiometricResult.Failed` → show error, stay locked
- Re-locks on navigate-away

### 1.3 — Verify Backup Round-Trip ⬜

`BackupRepository` and `FileImportLauncher` exist. Verify:
- Export CSV → share to Files app → re-import → events match
- Test on both Android and iOS

### 1.4 — Wire CrashReporter to Firebase ⬜

`CrashReporter` expect/actual stubs exist.

**Human steps:**
1. Create Firebase project → Add Android app (`com.ostomate.app`) + iOS app (`com.ostomate.app`)
2. Download `google-services.json` → `androidApp/`
3. Download `GoogleService-Info.plist` → `iosApp/iosApp/`
4. Base64 encode both → add as GitHub Secrets (`GOOGLE_SERVICES_JSON`, `GOOGLE_SERVICE_INFO_PLIST`)

**Code steps:**
- Add Firebase Crashlytics SDK to `androidApp/build.gradle.kts` and `iosApp` (via SPM or CocoaPods)
- Wire `CrashReporter.record(throwable)` to the existing Android/iOS actuals
- Release builds only — debug builds should not send crash reports

### 1.5 — Integration Pass ⬜

- All Phase 1 items complete
- CI green on `main`
- Manual smoke test on physical iPhone + Android device
- Update this file: mark Phase 1 complete

---

## Phase 2 — Physical Device Validation

**Goal:** App runs correctly on real hardware. All Maestro E2E flows pass.

### 2.1 — iOS Physical Device ⬜
- Build for device: `xcodebuild -project iosApp.xcodeproj -target iosApp -configuration Debug -sdk iphoneos ARCHS=arm64`
- Install via Xcode or `ios-deploy`
- Run manual device checklist from `04-test-plan.md`

### 2.2 — Android Physical Device ⬜
- `./gradlew :androidApp:installDebug`
- Run manual device checklist from `04-test-plan.md`

### 2.3 — Maestro E2E All Green ⬜
- All `.maestro/` flows pass in CI
- Fix any flows that fail

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
