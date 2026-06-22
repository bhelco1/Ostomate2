# Ostomate 2.0 — Test Plan

> This is the **living checklist** of what we test today and how to run it.
> For the target-state strategy, CI gating model, coverage plan, and reporting,
> see `08-test-strategy.md`.

## Test Pyramid (current reality)

```
         [E2E]          Maestro — 5 flows in CI (Android only), iOS E2E = 0 (manual)
        [device]        Manual — physical iPhone + Android device checklist
      [UI / VM]         Not automated — 7 ViewModels, 0 tests; composables manual
    [data tests]        9 SQLite tests (Room DAO + migrations) — JVM host + iOS sim
    [unit tests]        60 tests (pure domain + DataStore) — JVM host + iOS sim
                        ────────────────────────────────────────────────
                        69 shared tests, all green — every PR runs all 69 on ubuntu
```

## Common Tests (`shared/commonTest`)

Run on **both** JVM (`testAndroidHostTest`, gated on every PR) and iOS simulator
(`iosSimulatorArm64Test`). Pure-Kotlin domain logic plus the DataStore tests
(DataStore uses an okio file path — no native SQLite — so it runs on the host JVM).

| Suite | Tests | What it covers |
|---|---|---|
| DeepLinkParserTest | 15 | All URI parse cases including edge cases |
| PredictionEngineTest | 13 | Days-remaining calculation, averaging, edge cases |
| CsvExporterTest | 9 | CSV serialization, escaping, round-trip |
| CsvImporterTest | 8 | CSV parse, round-trip, malformed input |
| CalendarAggregatorTest | 7 | Month aggregation, boundary days |
| CsvMigrationTest | 5 | V1 CSV format import |
| SettingsRepositoryTest | 3 | DataStore read/write (moved from iosTest in 2.5.1) |
| **Subtotal** | **60** | Run on JVM + iOS sim |

## SQLite Tests (Room DAO + migrations)

Use a real SQLite driver, and **run on both targets**. The assertions live once in
`commonTest` (`ChangeEventDaoScenarios`, `MigrationScenarios`); thin per-platform
test classes feed them the right driver:

- **JVM host** (`androidHostTest`, gated on every PR): Robolectric supplies a
  `Context` + a desktop-capable SQLite via `AndroidSQLiteDriver` — the
  `sqlite-bundled` native is Android-ABI-only and can't load on a desktop JVM.
- **iOS simulator** (`iosTest`): the bundled native driver — the real iOS path.

| Scenario set | Tests | What it covers |
|---|---|---|
| ChangeEventDao | 7 | Room CRUD: seed, insert/join, delete, archive, repository |
| Migration 1→2 | 1 | spike→catalog remap (direct `migrate()` on in-memory SQLite) |
| Migration 2→3 | 1 | adds colorIndex |
| **Subtotal** | **9** | JVM host + iOS sim |

**Grand total: 69 shared tests, run on both JVM host and iOS simulator.**

> Migration tests call the `Migration.migrate()` functions directly (no
> `MigrationTestHelper`, which is instrumentation-only on Android). Trade-off:
> they validate the migration's data transformation but not that the resulting
> schema matches the exported JSON — that drift is caught by Room's build-time
> `exportSchema`.

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

| Flow | In CI? | What it tests |
|---|---|---|
| `01_cold_start_qr_log.yaml` | ❌ not wired | Deep link log on cold start |
| `02_log_and_undo.yaml` | ✅ | Log a change, verify count, undo |
| `03_edit_delete_event.yaml` | ✅ | Edit history entry, delete it |
| `04_set_inventory.yaml` | ✅ | Set inventory counts in Settings |
| `05_backup_round_trip.yaml` | ✅ | Export CSV, re-import, verify events |
| `08_biometric_gate.yaml` | ✅ | Settings locked before auth |
| `09_store_screenshots.yaml` | ❌ not wired | Capture Play Store screenshots |

> Only 5 of 7 flows run in CI, and only on Android. iOS has **no** automated
> E2E coverage. See `08-test-strategy.md` for the plan to close this.

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
