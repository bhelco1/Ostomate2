# Ostomate 2.0 — Product & Technical Requirements

> **Platform:** iOS + Android (Kotlin Multiplatform + Compose Multiplatform)
> **Goal:** Production-quality, App Store + Play Store published cross-platform app.
> **Data:** All data stays on-device. No backend, no accounts, no cloud sync.

---

## 1. Product Vision

Ostomate helps ostomy patients:
1. Log every bag or flange change with a single tap (or a QR code scan)
2. Know how many days of supply remain based on personal usage history
3. Receive timely warnings before running out of supplies

Must be simple enough to use one-handed in a clinical or bathroom setting.

---

## 2. Platform & Architecture

| Attribute | Value |
|---|---|
| Language | Kotlin 2.3+ |
| UI | Compose Multiplatform (CMP) |
| Platforms | iOS 16+ and Android 8.0+ (API 26+) |
| Architecture | UDF — StateFlow + ViewModel per screen |
| Database | Room KMP (SQLite) |
| DI | Koin |
| Backend | None — all data is local |
| Distribution | Apple App Store + Google Play Store |

---

## 3. Functional Requirements

### 3.1 Home Screen ✅ Implemented
- Display inventory count and days-remaining estimate for each active supply type
- Days remaining = `onHand × averageDaysBetweenChanges` (last 10 events)
- Warning banner when days remaining < user-configured threshold
- One-tap log buttons per supply type
- Snackbar confirmation after logging

### 3.2 QR Code Deep-Link Logging ✅ Implemented
- URI scheme: `ostomate://log?item=bag` and `ostomate://log?item=flange`
- Native camera fires deep link → app logs change → toast → exits
- Works on cold start and foreground (`onNewIntent` / SwiftUI `onOpenURL`)
- Unknown QR codes: toast, no action

### 3.3 Calendar View ✅ Implemented
- Monthly grid; each day shows bag/flange pill counts
- Navigate to previous/next months
- Today highlighted

### 3.4 History Screen ✅ Implemented
- Chronological event log with type and timestamp

### 3.5 Statistics Screen ✅ Implemented
- Period tabs: Week / Month / Year
- Count per supply type for the selected period

### 3.6 Settings ✅ Implemented
- Biometric-locked inventory editing (count + warning threshold per type)
- Fallback to device PIN if no biometric enrolled; auto-unlock if none enrolled
- QR Labels: generate and share QR code PNGs for each supply type
- Reorder Warnings: configure threshold per type
- Manage Supplies: add/rename/reorder custom supply types with color palette
- CSV Export: export all events as `id,type,timestamp_iso8601`
- Privacy Policy screen (in-app)

### 3.7 Onboarding ✅ Implemented
- First-launch flow to set initial inventory counts

### 3.8 Custom Supply Types ✅ Implemented
- User can define supply types beyond BAG/FLANGE
- Each type has a name and color; stored in `supply_types` table

### 3.9 Push Notifications ✅ Implemented (expect/actual + scheduler — not yet wired to UI)
- `Notifier` expect/actual: iOS uses UNUserNotificationCenter, Android uses WorkManager
- `NotificationScheduler` domain class exists
- **Remaining:** wire scheduler into app startup; request permission on first use

### 3.10 Backup & Restore ✅ Partially Implemented
- `BackupRepository` exists
- CSV import via `FileImportLauncher` (expect/actual)
- **Remaining:** verify round-trip on both platforms

### 3.11 Crash Reporting ✅ Implemented (expect/actual stub — not wired to Firebase)
- `CrashReporter` expect/actual exists
- **Remaining:** wire to Firebase Crashlytics in release builds

---

## 4. Non-Functional Requirements

### 4.1 Performance
- Home screen renders within 300 ms of cold launch
- Recording a change (DB write + UI update) within 200 ms

### 4.2 Reliability
- No data loss on process kill (Room transactions are atomic)
- Room schema changes use `Migration` objects — never destructive migration in production
- Inventory count floors at 0

### 4.3 Accessibility
- All interactive elements have semantics/content descriptions for VoiceOver/TalkBack
- Minimum touch target: 48 × 48 dp (Android) / 44 × 44 pt (iOS)
- Color is never the only indicator of state

### 4.4 Dark Mode
- CMP Material3 dark theme verified on all screens

### 4.5 Security & Privacy
- All data local to the device
- Settings editing requires biometric or device PIN
- Crash reports must not include any health or usage data

### 4.6 Code Quality
- No `!!` operators in shared or platform code
- All new code has unit tests before the PR merges
- ktlint + detekt pass clean

---

## 5. Testing Requirements

### 5.1 Shared Tests (commonTest — run on JVM and iOS simulator)
Target: all domain and data logic in `shared/`

| Suite | Tests | Coverage |
|---|---|---|
| DeepLinkParserTest | 15 | `DeepLinkParser` |
| PredictionEngineTest | 13 | `PredictionEngine` |
| CsvImporterTest | 8 | CSV import |
| ChangeEventDaoTest | 7 | Room CRUD |
| CalendarAggregatorTest | 7 | `CalendarAggregator` |
| CsvMigrationTest | 5 | CSV migration |
| SettingsRepositoryTest | 3 | `SettingsRepository` |
| Migration1To2Test | 1 | Room migration |
| Migration2To3Test | 1 | Room migration |

### 5.2 E2E (Maestro — Maestro flows in `.maestro/`)
Runs on Android emulator in CI (non-PR only — emulator is slow):
- `02_log_and_undo.yaml`
- `03_edit_delete_event.yaml`
- `04_set_inventory.yaml`
- `05_backup_round_trip.yaml`
- `08_biometric_gate.yaml`

### 5.3 Coverage Gate
- All shared logic covered by shared tests before shipping
- No new business logic without a corresponding test

---

## 6. CI/CD Requirements

See `.github/workflows/ci.yml`. Jobs:
- `android`: ktlint + detekt + assembleDebug + JVM host tests (every PR)
- `android-e2e`: Maestro flows on emulator (main merges + manual only)
- `ios`: SwiftLint + shared iOS simulator tests + iOS app build (main merges + manual only)

macOS runners bill at 10× — iOS job is gated to non-PR events intentionally.

---

## 7. App Store & Play Store Requirements

| Item | Android | iOS |
|---|---|---|
| Package / Bundle ID | `com.ostomate.app` | `com.ostomate.app` |
| Developer account | Google Play ($25 one-time) | Apple Developer Program ($99/year) |
| Privacy policy | GitHub Pages → `docs/privacy.html` (https://bhelco1.github.io/Ostomate2/) | Same URL |
| Release signing | Keystore via GitHub Secrets | Certificate + provisioning via Fastlane |
| Distribution track | Internal → Production | TestFlight → App Store |

---

## 8. Deferred / Future

- [ ] Apple Watch / Wear OS companion
- [ ] iCloud or Firebase cloud sync
- [ ] Trend charts
- [ ] Localization beyond English
- [ ] Tablet / landscape layout
- [ ] Home screen widgets (iOS + Android)
