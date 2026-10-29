# Ostomate 2.0 — Security & Privacy

## Privacy Posture: Local-First

- **No analytics, no telemetry, no usage tracking.** Nothing reports how you use the app.
- **One outbound path, opt-in and off by default: crash reporting.** Everything else stays
  on-device. This document previously said "zero outbound requests", which was false — the
  app ships a crash reporter (see below). Store forms must be answered against reality.
- **No account system.** There is no login, no server, no cloud sync.
- **All data on-device.** Room database lives in the app's private storage. Never exported without explicit user action.
- **Crash reporting: Sentry, opt-in, OFF by default.** `sentry-android` on Android and
  sentry-cocoa on iOS (started from `iOSApp.swift`, gated on `SentryBridgeKt.sentryEnabled()`),
  both behind the `crashReportingEnabled` setting. Stack traces only — no health data, no
  supply counts, no PII. (Earlier drafts said *Firebase Crashlytics*; that is not what ships
  and never was — corrected 2026-07-13.)

## Biometric Authentication

Supply-count editing (Manage Supplies: the set-count dialog and the +/− box buttons) is
protected by `BiometricAuthenticator`, once per visit to that screen:
- Android: `BIOMETRIC_STRONG or DEVICE_CREDENTIAL` — Face, fingerprint, or PIN fallback.
  **Android 9–10 (API 28–29) use `BIOMETRIC_WEAK or DEVICE_CREDENTIAL`**: androidx.biometric
  rejects STRONG | DEVICE_CREDENTIAL there as unsupported (before checking enrollment), which
  silently blocked every locked edit until 2026-10-07. WEAK is the only combination with a
  PIN fallback on those versions (Bobby's decision, 2026-10-07).
- iOS: `LAContext` with `LAPolicyDeviceOwnerAuthentication` — Face ID, Touch ID, or passcode fallback
- If no biometric or credential enrolled: auto-unlock (graceful fallback, matches v1 behavior)
- Re-locks on navigate-away from Manage Supplies

## Data Export

Full-state JSON backup via `BackupRepository.exportBackup()` + `FileSharer` (FEAT-00; the
old CSV `CsvExporter` was deleted):
- Contents: supply types, every change event (ids and foreign keys preserved) and all
  `AppSettings` — enough to restore a new phone to the same state. Health data, so treat the
  file as sensitive; it is only created when the user taps Backup.
- Delivered via the platform share sheet (no third-party service); restore reads it back
  through Room (`BackupRepository.restoreBackup`), never by copying database files.

## Android-Specific

- Cleartext HTTP is blocked by the platform default (targetSdk 36; no
  `usesCleartextTraffic` attribute or network security config overrides it)
- Camera permission absent — app generates QR codes but never reads the camera
- `android:allowBackup="false"` — Android Auto Backup is **off**; device replacement relies on
  the user's own JSON backup (Data Export above). Earlier text here said `true`; the manifest
  has said `false` (checked 2026-10-29).
- No `FLAG_SECURE`: screenshots of the app are not blocked (an earlier draft claimed it)

## iOS-Specific

- App Transport Security enforced by default (no cleartext)
- No background refresh capabilities beyond local notifications
- Local notifications require explicit user permission (`UNUserNotificationCenter.requestAuthorization`)

## Threat Model

| Threat | Mitigation |
|---|---|
| Unauthorized change of supply counts | Biometric lock on count edits in Manage Supplies (once per visit) |
| Data exfiltration | No network except opt-in crash reporting (off by default); share sheet is user-initiated |
| Supply data in crash reports | Sentry captures stack traces only; no custom logging of health data |
| Screenshot of sensitive screen | Not mitigated (no `FLAG_SECURE`); the screens show supply counts, not identity |
| Lost/replaced device | User's JSON backup (Settings → Backup); Android Auto Backup is off; iOS: no iCloud backup |

## Cost Rule for New Dependencies

Any new third-party SDK must be reviewed for:
1. Does it send data off-device? If yes, document what and why in this file.
2. Does it add a recurring cost? Document in `07-business-plan.md`.
3. Privacy policy: must the policy be updated?
