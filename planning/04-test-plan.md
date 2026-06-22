# Ostomate 2.0 — Test Plan

> This is the **living checklist** of what we test today and how to run it.
> For the target-state strategy, CI gating model, coverage plan, and reporting,
> see `08-test-strategy.md`.

## Test Pyramid (current reality)

```
         [E2E]          Maestro — 5 flows in CI (Android only), iOS E2E = 0 (manual)
        [device]        Manual — physical iPhone + Android device checklist
      [UI / VM]         Not automated — 7 ViewModels, 0 tests; composables manual
    [data tests]        9 SQLite tests (Room DAO + migrations) — iOS sim only*
    [unit tests]        60 tests (pure domain + DataStore) — JVM + iOS simulator
                        ────────────────────────────────────────────────
                        69 shared tests total, all green
```
*Need native SQLite the ubuntu host JVM lacks; gated on PRs via a path-filtered
 iOS job (Phase 2.5.1), not the ubuntu run.

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

## SQLite Tests (`shared/iosTest`)

Use a real SQLite driver. The android `sqlite-bundled` artifact ships only
Android-ABI native libs, so these **cannot** run on the ubuntu host JVM — they
run on the iOS simulator. As of 2.5.1 they are gated on PRs via a path-filtered
iOS job (fires when iOS or DB-layer files change), not the ubuntu run.

| Suite | Tests | What it covers |
|---|---|---|
| ChangeEventDaoTest | 7 | Room CRUD: insert, query, delete |
| Migration1To2Test | 1 | v1→v2 migration (direct `migrate()` on in-memory SQLite) |
| Migration2To3Test | 1 | v2→v3 migration (adds colorIndex) |
| **Subtotal** | **9** | iOS sim only |

**Grand total: 69 shared tests.** JVM runs 60; iOS simulator runs all 69.

> Migration tests call the `Migration.migrate()` functions directly (no
> `MigrationTestHelper`, which is instrumentation-only on Android). Trade-off:
> they validate the migration's data transformation but not that the resulting
> schema matches the exported JSON — that drift is caught by Room's build-time
> `exportSchema`. Running the 9 SQLite tests on the ubuntu JVM would need
> Robolectric or a desktop SQLite provider (deferred — see `05-dev-plan.md` 2.5.1).

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
