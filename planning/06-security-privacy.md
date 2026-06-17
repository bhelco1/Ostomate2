# Ostomate 2.0 — Security & Privacy

## Privacy Posture: Local-First

- **No network calls.** The app makes zero outbound requests. No analytics, no telemetry, no usage tracking.
- **No account system.** There is no login, no server, no cloud sync.
- **All data on-device.** Room database lives in the app's private storage. Never exported without explicit user action.
- **Crash reporting only.** Release builds include Firebase Crashlytics — stack traces only, no health data, no supply counts, no PII.

## Biometric Authentication

Settings editing is protected by `BiometricAuthenticator`:
- Android: `BIOMETRIC_STRONG or DEVICE_CREDENTIAL` — Face, fingerprint, or PIN fallback
- iOS: `LAContext` with `LAPolicyDeviceOwnerAuthentication` — Face ID, Touch ID, or passcode fallback
- If no biometric or credential enrolled: auto-unlock (graceful fallback, matches v1 behavior)
- Re-locks on navigate-away from Settings

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
| Data exfiltration | No network; share sheet is user-initiated |
| Supply data in crash reports | Crashlytics captures stack traces only; no custom logging of health data |
| Screenshot of sensitive screen | FLAG_SECURE on Android Settings window |
| Lost/replaced device | Android Auto Backup (encrypted); iOS: no iCloud backup (future work) |

## Cost Rule for New Dependencies

Any new third-party SDK must be reviewed for:
1. Does it send data off-device? If yes, document what and why in this file.
2. Does it add a recurring cost? Document in `07-business-plan.md`.
3. Privacy policy: must the policy be updated?
