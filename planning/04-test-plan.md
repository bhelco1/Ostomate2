# Ostomate 2.0 — Test Plan

## Test Pyramid

```
         [E2E]          Maestro flows — Android emulator (CI, non-PR)
        [device]        Manual — physical iPhone + Android device
      [UI / CMP]        Not yet automated — manual smoke test per screen
    [shared tests]      JVM + iOS simulator — 60 tests, all green
```

## Shared Tests (`shared/commonTest`)

Run on both JVM (`testAndroidHostTest`) and iOS simulator (`iosSimulatorArm64Test`).

| Suite | Tests | What it covers |
|---|---|---|
| DeepLinkParserTest | 15 | All URI parse cases including edge cases |
| PredictionEngineTest | 13 | Days-remaining calculation, averaging, edge cases |
| CsvImporterTest | 8 | CSV parse, round-trip, malformed input |
| ChangeEventDaoTest | 7 | Room CRUD: insert, query, delete |
| CalendarAggregatorTest | 7 | Month aggregation, boundary days |
| CsvMigrationTest | 5 | V1 CSV format import |
| SettingsRepositoryTest | 3 | DataStore read/write |
| Migration1To2Test | 1 | Room schema migration |
| Migration2To3Test | 1 | Room schema migration |
| **Total** | **60** | |

**Run locally:**
```bash
./gradlew :shared:testAndroidHostTest          # JVM — fast
./gradlew :shared:iosSimulatorArm64Test        # iOS simulator (M1 Mac)
```

**Verify execution counts** (BUILD SUCCESSFUL alone is not enough):
```bash
grep -h "<testsuite" shared/build/test-results/iosSimulatorArm64Test/TEST-*.xml
```
Every suite must show `failures="0" errors="0" skipped="0"`.

## E2E Tests (Maestro — `.maestro/`)

Runs on Android emulator in CI. Triggered on `main` merges and `workflow_dispatch` only.

| Flow | What it tests |
|---|---|
| `01_cold_start_qr_log.yaml` | Deep link log on cold start |
| `02_log_and_undo.yaml` | Log a change, verify count, undo |
| `03_edit_delete_event.yaml` | Edit history entry, delete it |
| `04_set_inventory.yaml` | Set inventory counts in Settings |
| `05_backup_round_trip.yaml` | Export CSV, re-import, verify events |
| `08_biometric_gate.yaml` | Settings locked before auth |
| `09_store_screenshots.yaml` | Capture Play Store screenshots |

**Run locally (requires Android emulator or connected device):**
```bash
maestro test .maestro/02_log_and_undo.yaml
```

## Manual Device Testing Checklist

Run before any release candidate.

### iOS (physical device)
- [ ] Cold launch — home screen shows inventory
- [ ] Log bag change — count decrements, snackbar appears
- [ ] Log via QR scan — camera app fires deep link, toast shown, app returns
- [ ] Calendar — month view shows correct day counts
- [ ] Settings — biometric lock engages; Touch ID / Face ID unlocks
- [ ] Settings → Manage Supplies — add custom supply type
- [ ] Settings → QR Labels — share sheet appears with PNG
- [ ] Settings → Export CSV — share sheet appears with .csv
- [ ] Notifications — reduce days remaining below threshold, verify notification fires
- [ ] Dark mode — toggle in Settings app, verify all screens readable
- [ ] VoiceOver — navigate home screen, all elements read correctly

### Android (physical device)
- [ ] Same checklist as iOS
- [ ] QR deep link via camera
- [ ] Notification via WorkManager (set supply below threshold, wait up to 24h or use ADB to trigger)
- [ ] TalkBack navigation

## Coverage Rules

- All new domain/data logic in `shared/` requires a test in `commonTest` before merge
- UI composables are not unit-tested — cover the ViewModel's UiState logic instead
- Migration tests required for every Room schema version bump
- A skipped/disabled test task looks like success — always verify execution counts in XML, not just BUILD SUCCESSFUL
