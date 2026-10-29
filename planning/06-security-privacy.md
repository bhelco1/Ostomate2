# Ostomate 2.0 — Security & Privacy

## Privacy Posture: Local-First

- **No analytics, no telemetry, no usage tracking.** Nothing reports how you use the app.
- **Two outbound paths, both opt-in and off by default: crash reporting, and (Android only)
  the daily Google Drive backup.** Everything else stays on-device. This document previously
  said "zero outbound requests", which was false — the app ships a crash reporter (see below).
  Store forms must be answered against reality.
- **No account system.** There is no login, no server of ours, no cloud sync. The Drive
  backup uses the user's own Google account through Google's authorization; the app never
  sees a password and does not request name or email.
- **All data on-device.** Room database lives in the app's private storage. Never exported
  without explicit user action — turning on the daily Drive backup is that action, once.
- **Crash reporting: Sentry, opt-in, OFF by default.** `sentry-android` on Android and
  sentry-cocoa on iOS (started from `iOSApp.swift`, gated on `SentryBridgeKt.sentryEnabled()`),
  both behind the `crashReportingEnabled` setting. Stack traces only — no health data, no
  supply counts, no PII. (Earlier drafts said *Firebase Crashlytics*; that is not what ships
  and never was — corrected 2026-07-13.)

## Daily Google Drive backup (FEAT-03, Android only, opt-in, hidden)

- **Off by default, and hidden:** the switch is only visible in developer mode (5 taps on
  the About header). Once on, it stays on when dev mode is turned off, and a status line in
  the regular Settings (Data section) says so, with the last run's time and outcome.
- **What leaves the device:** once a day, the exact JSON that Settings → Export backup
  produces (supplies, full change history, AppSettings), uploaded over HTTPS straight from
  the device to the Drive REST v3 API, into an "Ostomate backups" folder in the **user's own**
  Google Drive. Nothing goes to the developer or any other server.
- **Manual Backup while on:** Settings → Backup uploads the same file to the same folder
  (interactive authorization) instead of sharing it, and counts toward the 14. With the
  feature off, Backup shares the file as before.
- **Scope:** `https://www.googleapis.com/auth/drive.file` only — the app can see and change
  only files and folders it created. It keeps the newest 14 backups and deletes its own older
  ones; it never deletes a file it cannot date or that is not named like its backups.
- **Auth:** Play services Identity `AuthorizationClient` (play-services-auth). Consent is
  given interactively when the switch is turned on; daily runs ask silently and, if Google
  needs the user again, record "Google sign-in needed" for the status line instead of showing
  UI. The app does not store the access token (Play services caches it).
- **Failure handling:** a run never crashes the app or job. Only a network failure is
  retried (twice, 15-minute exponential backoff); anything else waits for the next day.
- **Per-device state:** the switch and last-run outcome live in DataStore but **outside**
  `AppSettings`, so they are not in the backup file and a restore neither turns the feature
  on nor off on the new device (pinned by `SettingsRepositoryTest`).
- **Turning it off** cancels the daily job; backups already in Drive stay until the user
  deletes them. Uninstalling the app does not delete them either.

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

CSV export via `CsvExporter` + `FileSharer`:
- Format: `id,type,timestamp_iso8601` — no PII
- Delivered via platform share sheet (no third-party service)
- File written to app cache directory and cleaned up after sharing

## Android-Specific

- `android:usesCleartextTraffic="false"` — enforced in `AndroidManifest.xml`
- Camera permission absent — app generates QR codes but never reads the camera
- `android:allowBackup="true"` — intentional; preserves usage history on device replacement (Google Auto Backup, encrypted)
- `FLAG_SECURE` on Settings window (prevents screenshots of biometric-protected screen)

## iOS-Specific

- App Transport Security enforced by default (no cleartext)
- No background refresh capabilities beyond local notifications
- Local notifications require explicit user permission (`UNUserNotificationCenter.requestAuthorization`)

## Threat Model

| Threat | Mitigation |
|---|---|
| Unauthorized settings access | Biometric lock on every Settings visit |
| Data exfiltration | No network except opt-in crash reporting and the opt-in Drive backup (both off by default); share sheet is user-initiated |
| Drive backup reaches the wrong place / too much | `drive.file` scope only; uploads go to the user's own Drive over HTTPS; no developer endpoint exists |
| Backups pile up or a run deletes user files | Retention keeps the newest 14 of the app's own, backup-named, datable files; drive.file cannot see anything else |
| Supply data in crash reports | Sentry captures stack traces only; no custom logging of health data |
| Screenshot of sensitive screen | FLAG_SECURE on Android Settings window |
| Lost/replaced device | Android Auto Backup (encrypted); iOS: no iCloud backup (future work) |

## Cost Rule for New Dependencies

Any new third-party SDK must be reviewed for:
1. Does it send data off-device? If yes, document what and why in this file.
2. Does it add a recurring cost? Document in `07-business-plan.md`.
3. Privacy policy: must the policy be updated?

Reviewed: `play-services-auth` 22.0.0 (FEAT-03) talks to Google only for the authorization
the user starts by turning the Drive backup on, and for the silent token refresh of each
daily run. No recurring cost; the published privacy policy (`docs/privacy.html`) describes it.
