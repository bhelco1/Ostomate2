# Ostomate 2.0 — Development Plan

> Update this file as items complete. It is the source of truth for current phase and status.

## Status Summary

| Phase | Goal | Status |
|---|---|---|
| 0 | KMP spike — prove the stack | ✅ Complete |
| 1 | Wire platform features + stabilize | ✅ Complete |
| 2 | Physical device validation | ✅ Complete |
| 2.5 | Test hardening & QA infrastructure | 🚧 (2.5.1–2.5.5 ✅, 2.5.6+ ⬜) |
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

### 2.3 — Maestro E2E All Green ✅
- Fixed all 5 CI flows (02–05, 08):
  - All flows: added `tapOn: "Skip" optional: true` to handle fresh onboarding after `clearState: true`
  - Flow 01: fixed wildcard `id: "overflowButton_*"` → `id: "overflowButton"` with `index: 0`
  - Flow 03: fixed `id: "dayCell_today"` (added testTag to CalendarScreen), `text: "Events"` → `text: "+ Add an entry*"`, `id: "eventRow"` (added testTag to HistoryScreen)
  - Flow 04: fixed button target (`editSupplyButton` → `supplyCount_*`) and dialog title assertion (`Edit*count` → `Set*count`)
  - Flow 05: fixed invalid YAML on `pressKey: BACK` (removed misindented `optional: true`)
  - Flow 08: rewrote biometric test — gate is on Settings screen, not Home overflow; now navigates back to Settings and asserts auto-unlock on emulator
- Push to main to trigger CI `android-e2e` job

---

## Phase 2.5 — Test Hardening & QA Infrastructure

**Goal:** Close the coverage and CI-gating gaps found in the 2026-06-22 QA audit
before shipping. Full rationale, target-state pyramid, and reporting design live
in `08-test-strategy.md` §7 (this is its rollout, ordered by
risk-reduction-per-effort). Every item lands with its own tests; verify
execution counts in `shared/build/test-results/`, not just BUILD SUCCESSFUL.

### 2.5.1 — Close the PR gate ✅ (done 2026-06-22)
*Highest impact. Approved split in `08` §4. Outcome: **all 69 shared tests now
run on the ubuntu host JVM (`testAndroidHostTest`), so every PR gates them — at
zero macOS cost.***

**Done:**
- [x] DataStore tests (`SettingsRepositoryTest`, 3) moved to `commonTest`; run on
      JVM host + iOS sim (DataStore needs no native SQLite).
- [x] The 9 SQLite tests (7 Room DAO + 2 migration) now run on **both** targets.
      The android `sqlite-bundled` artifact ships only Android-ABI natives
      (`UnsatisfiedLinkError: no sqliteJni`), so the JVM host run uses
      **Robolectric + `AndroidSQLiteDriver`** (Robolectric supplies a `Context`
      and a desktop-capable SQLite); iOS keeps the bundled native driver.
- [x] Shared assertions extracted to `commonTest` (`ChangeEventDaoScenarios`,
      `MigrationScenarios`); thin per-platform test classes feed them the right
      driver. Logic written once, exercised on both targets.
- [x] `buildDatabase(builder, driver = BundledSQLiteDriver())` — added a defaulted
      driver param so tests can inject the framework driver; production unchanged.
- [x] Migration tests call `Migration.migrate()` directly on an in-memory
      connection (dropped `MigrationTestHelper`/`room.testing`/schema env wiring).
- [x] New test deps (approved): `robolectric`, `androidx.test.core`,
      `sqlite-framework` on `androidHostTest` only.
- [x] CI: `detect-changes` + `ios` job still gates the **iOS build** on PRs when
      iOS/DB/build files change (catches iOS-specific compile breaks); the iOS
      test run validates the real native driver. Pure domain/UI/Android PRs stay
      ubuntu-only.

**Verified:** JVM host 69 green, iOS sim 69 green; ktlint clean for all new files.

**Trade-off noted:** dropped `MigrationTestHelper`'s exported-schema-JSON
validation (instrumentation-only on Android). Migration data-transform is still
tested; schema drift is caught by Room's build-time `exportSchema`. Re-add as an
optional check later if belt-and-suspenders wanted.

### 2.5.2 — Coverage measurement (JaCoCo) ✅ (done 2026-07-02)
- [x] JaCoCo wired to `:shared:testAndroidHostTest` (`jacocoHostTestReport`,
      `jacocoCoverageVerification`). Robolectric needs
      `isIncludeNoLocationClasses = true` or sandbox-loaded classes report 0%.
- [x] Scope: hand-written `domain` + `data` classes; excluded: Room-generated
      `*_Impl*`, generated `OstomateDatabaseConstructor`, platform
      `DatabaseBuilder_*` glue. (ViewModels live in `composeApp` — extend
      coverage there when 2.5.3 lands.)
- [x] **Baseline measured 2026-07-02: 52.6% line** (db 94.7%, settings 76.7%,
      domain 53.5%, repositories 22.0% — the 2.5.4 target). Floor set at 0.52;
      ratchet up as 2.5.3/2.5.4 land, never lower.
- [x] CI: PR gate runs report + verification; coverage table in the Actions job
      summary; HTML/XML report + JUnit XML uploaded as artifacts (feeds 2.5.5).

### 2.5.3 — ViewModel tests ✅ (done 2026-07-02)
*Fulfills the existing `04-test-plan.md` policy that previously had 0 implementations.*
- [x] 42 tests covering the `UiState` transitions of all 7 ViewModels, green on
      JVM host + iOS sim (`composeApp/src/commonTest/.../ui/`).
- [x] Fakes sit at the **DAO / platform-interface boundary** (`FakeSupplyTypeDao`,
      `FakeChangeEventDao`, `InMemoryDataStore`), so the real repositories are
      exercised, not mocked away. Testability seams added in `shared`:
      `ReminderNotifier`, `BiometricAuth`, `CrashReporting` interfaces that the
      `expect class` platform types implement (Koin `bind`s them; production
      behavior unchanged).
- [x] BUG-02 regression (leading-zero input) at the ViewModel layer:
      `OnboardingViewModelTest.countInputStripsLeadingZerosAndNonDigits`.
- [x] JaCoCo extended to `:composeApp` (ViewModel + UiState scope): baseline
      **93.3% line**, floor 0.93. CI gates both modules' floors on every PR and
      runs the composeApp suite on JVM (PR) + iOS sim (post-merge/dispatch).

### 2.5.4 — Repository tests ✅ (done 2026-07-02)
- [x] All five targets have direct tests, run against a **real in-memory Room DB**
      on both platforms (`RepositoryScenarios` in `commonTest` + thin per-platform
      classes, same pattern as `ChangeEventDaoScenarios`): 12 scenarios × 2 targets.
- [x] **Backup round-trip proven**: export → import into a fresh DB → event parity
      (timestamps + kinds); re-import of own export is idempotent (all skipped);
      v1 import maps kinds to seeded supplies; parse errors counted without losing
      good rows; >10 MB import rejected untouched.
- [x] `ChangeEventRepository`: delete restocks / reinsert consumes inventory,
      update stamps `editedAtMillis`, deep-link debounce, custom-supply `id:` links.
- [x] `SupplyRepository`/`SupplyTypeDao`: custom supply sort-order append, archived
      supplies skipped by kind lookup, on-hand/threshold adjustments persist.
- [x] `NotificationScheduler`: 5 pure tests via a recording `ReminderNotifier`
      (no history / out-of-stock / below threshold / healthy delay / per-supply tags).
- [x] Shared suite now **86 tests per target** (was 69). Coverage rose 52.6% → 92.0%
      line; floor ratcheted 0.52 → 0.91.

### 2.5.5 — Zero-cost reporting + dashboard ✅ (done 2026-07-02; design updated for the Pi)
- [x] Actions job summary shows coverage tables for both modules on every PR;
      JUnit XML + JaCoCo HTML/XML uploaded as artifacts (90-day retention). *(2.5.2/2.5.3)*
- [x] **Static dashboard** (`scripts/generate_test_dashboard.py`, no deps): groups
      results into **Unit / Integration / UI / CI-CD**, with per-suite table,
      coverage %, and a run-trend strip (`history.json`, last 200 runs). The CI
      `dashboard` job regenerates it on every main merge/dispatch and force-pushes
      to the **`test-dashboard` branch** (single commit; trend lives in the JSON).
- [x] **Raspberry Pi**: `scripts/pi/update-dashboard.sh` pulls the branch and
      serves it (cron example + `--serve` mode in the script header). Decision
      2026-07-02: Pi-pull replaces the GitHub Pages design — no secrets in CI,
      the Pi stays private.
- [x] README: CI status badge + dashboard link; stale Phase-0 status corrected.
- [x] **Codecov** step added (approved in `08` §6) — inert until Bobby adds the
      `CODECOV_TOKEN` repo secret (Settings → Secrets → Actions; token from
      codecov.io after signing in with GitHub).
- **Done when** ✅: suite health + coverage trend readable without opening CI logs.

### 2.5.6 — iOS E2E ⬜
*Biggest remaining hole — iOS correctness is 100% manual today.*
- Maestro on the iOS simulator for onboarding, log, share, and deep-link flows
  (the clusters where device bugs appeared).
- **Done when:** iOS E2E runs post-merge alongside the Android Maestro suite.

### 2.5.7 — Screenshot tests ⬜
- Screenshot-diff coverage for Home, Onboarding, Calendar, QrLabels (catches
  layout regressions like BUG-03 keyboard overlap).
- Engine (Roborazzi vs Paparazzi vs Compose-native) is the one deferred decision
  in `08` §8 — pick it when starting this item.
- **Done when:** baseline images committed; diffs fail CI on layout change.

### 2.5.8 — Wire orphan Maestro flows ⬜
- Add `01_cold_start_qr_log.yaml` and `09_store_screenshots.yaml` to the CI
  `android-e2e` job (currently only 5 of 7 run).
- Strengthen flow `08` so it asserts the biometric **gate logic**, not emulator
  auto-unlock behavior.
- **Done when:** all 7 flows run in CI with meaningful assertions.

### 2.5.9 — Comprehensive extras ⬜
- Mutation testing on the pure domain layer (small + pure → high ROI).
- Automated accessibility semantics checks (Home + Settings); keep manual
  VoiceOver/TalkBack for screen-reader feel.
- Flakiness tracking / E2E quarantine lane.
- **Done when:** the full target-state pyramid in `08` §3 is in place.

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

### 3.3 — App Icon ✅ (2026-07-12)

Branded mark: white "O" progress ring with dot on brand blue (#2563EB), replacing the
placeholder stoma illustration. Artwork fits the 66 dp adaptive-icon safe zone.
- Android: adaptive icon vectors (foreground/background/monochrome) + legacy mipmap PNGs
- iOS: 1024 × 1024 PNG in Assets.xcassets → AppIcon (no rounded corners; this is the source)

### 3.4 — Store Listings ⬜

See `planning/07-business-plan.md` and `docs/store-listing.md`.

- Google Play: store listing content + screenshots (5 minimum)
- App Store Connect: metadata + screenshots (6.7" + 5.5" required)
- Privacy policy URL: **https://bhelco1.github.io/Ostomate2/** (serves `docs/privacy.html`).
  There is no `/docs/privacy-policy.html` — that path never existed.

### 3.5 — Enable GitHub Pages ✅ (already live; verified 2026-07-13)

Pages is **already enabled**: source `main` / folder `/docs`, published at
https://bhelco1.github.io/Ostomate2/, where `docs/index.html` redirects to
`docs/privacy.html`. Nothing to do here — this item was stale, not pending.

Before store submission, re-verify the page loads and that its crash-reporting section
still matches what the app actually does.

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
- Monitor **Sentry** for crashes (not Crashlytics — that was never what shipped). Note it is
  opt-in and OFF by default, so enable it on the test devices or you will see nothing.
- File GitHub Issues for any bugs found

### 5.2 — Pre-Release Audit ⬜
See `04-test-plan.md` manual device checklist. All items must pass.

### 5.3 — Promote to Production ⬜
- Google Play: Production track, 20% rollout → 100% after 48h if crash-free ≥ 99%
- App Store: Submit for review → release

**v1.0 shipped when:** Both stores at 100% rollout, crash-free rate ≥ 99% for 7 days.
