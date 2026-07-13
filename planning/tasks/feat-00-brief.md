# Implementation Brief — FEAT-00: Full-state backup & restore ("new phone" clone)

You are implementing a feature in the Ostomate 2.0 KMP repo. Work on a dedicated branch
`feat-00-full-backup` created off `main` (create it if it does not exist). Follow the repo's
`CLAUDE.md` and architecture rules. Full spec: `planning/bug-backlog.md` → **FEAT-00**.

## Goal
Back up and restore so a new phone is indistinguishable from the old one — ALL persisted
state, IDs and relationships intact. Replace the CSV backup/import entirely and remove all
v1 compatibility.

## PRECONDITION — dependency (confirm before coding)
This feature serializes to JSON in `shared/commonMain`. The serialization plugin is in the
version catalog (`kotlinxSerialization`) but is NOT applied to `shared`, and no
`kotlinx-serialization-json` runtime dependency exists. **Bobby has approved adding
`kotlinx-serialization-json` to the `shared` module** (first-party, no network, matches the
existing nav usage in composeApp). Apply the `kotlinxSerialization` plugin to
`shared/build.gradle.kts` and add `kotlinx-serialization-json` to the version catalog +
`commonMain` dependencies. If for any reason this cannot be added, STOP and report — do not
hand-roll a JSON parser.

## Backup format (single versioned JSON document)
```
{
  "formatVersion": 1,
  "schemaVersion": 3,            // OstomateDatabase @Database version at export time
  "exportedAt": <epochMillis>,
  "settings": {                 // mirrors AppSettings
    "onboardingDone": bool, "lockSettings": bool, "localeOverride": string|null,
    "crashReportingEnabled": bool, "applianceType": "TWO_PIECE|ONE_PIECE", "devMode": bool
  },
  "supplyTypes": [ { id, name, kind, boxSize, warnThresholdDays, onHand, sortOrder,
                     archived, colorIndex } ],   // ALL columns; ids preserved
  "events": [ { id, supplyTypeId, timestampMillis, note, tags, createdAtMillis,
                editedAtMillis } ]               // ALL columns; ids + FKs preserved
}
```
- Define `@Serializable` DTOs in `shared/commonMain` (do NOT annotate the Room entities
  themselves — keep a clean mapping layer). Enums (`SupplyKind`, `ApplianceType`) serialize
  as their `name` string.

## Export
- New `BackupRepository.exportBackup(): String` reads through the DAOs: all supply types, all
  raw change events, and the current `AppSettings`; builds the envelope; returns pretty JSON.
- Reading through Room (not a raw `.db` file copy) is REQUIRED — verified that live data can
  sit uncheckpointed in the `-wal`; a file copy would miss it.

## Restore (REPLACE — this is the migration path)
- New `BackupRepository.restoreBackup(json: String): RestoreResult`.
- Order of operations (correctness-critical):
  1. Enforce the existing ~10 MB size guard first.
  2. **Parse and validate the ENTIRE document in memory before touching the DB.** If parsing
     fails, or `formatVersion`/`schemaVersion` is unsupported, return an error result and
     leave all existing data untouched. NEVER wipe first and fail second.
  3. Only when valid: in a single DB transaction, `clearAllTables()`, insert `supplyTypes`
     (preserving ids) then `events` (preserving ids/FKs — supplies first for the FK), then
     write settings via a new bulk `SettingsRepository.restore(AppSettings)`.
- `schemaVersion` handling: if the backup's schemaVersion != the app's current DB version,
  reject with a clear message (same-app clone only, for now). Do not attempt migration.
- Restore is destructive → the UI MUST show a confirmation ("This replaces all current data
  on this device") before calling it.

## DAO / repository additions
- `ChangeEventDao`: add `@Query("SELECT * FROM change_events") suspend fun getAllRaw(): List<ChangeEventEntity>`.
- Use `OstomateDatabase.clearAllTables()` for the wipe (respects FK order). Inject the
  database or add narrow DAO delete queries if cleaner — your call, keep it minimal.
- `SupplyTypeDao.getAll()` already exists for export.
- `SettingsRepository`: add `suspend fun restore(settings: AppSettings)` that writes every key.

## Remove CSV + all v1 (per decision 2026-07-12)
- Delete CSV export path and the whole `shared/.../domain/CsvExporter.kt`
  (CsvExporter + CsvV2Importer + CsvV1Importer).
- Rewrite `BackupRepository` to the JSON export/restore above (drop `exportCsv`/`importCsv`
  and `ImportSummary`).
- composeApp `SettingsScreen`: replace the "Export" and "Import v1" list items with
  "Export backup" (share the JSON) and "Restore backup" (pick JSON → confirm → replace).
  Update `SettingsViewModel` accordingly. Remove now-unused strings
  (`settings_import_v1`, `settings_import_v1_sub`, and the CSV export string if v1-specific);
  add new externalized strings for the new items + the confirmation dialog.
- `FileImportLauncher` call site: mimeType `text/csv` → `application/json`; file name
  `ostomate_backup_<ts>.json`, share mimeType `application/json`.
- Delete v1/CSV tests: `CsvExporterTest`, `CsvImporterTest`, `CsvMigrationTest`.
- **Remove `MIGRATION_1_2` (v1-spike → v2) — confirmed safe** (no v1-schema DB exists; sole
  v1 user has migrated to v3). Precise, minimal removal:
  - `OstomateDatabase.kt`: delete the `MIGRATION_1_2` object; change
    `.addMigrations(MIGRATION_1_2, MIGRATION_2_3)` → `.addMigrations(MIGRATION_2_3)`;
    update the comment on `SEED_DEFAULT_SUPPLIES_SQL` (it's no longer used by a migration).
    **KEEP** `SEED_DEFAULT_SUPPLIES_SQL` (the fresh-install `onCreate` callback uses it) and
    **KEEP** `MIGRATION_2_3`. Leave `@Database(version = 3)` unchanged.
  - `shared/src/commonTest/.../db/MigrationScenarios.kt`: remove
    `migrate1To2SeedsCatalogAndRemapsSpikeRows`; keep `migrate2To3AddsColorIndexColumnWithNullDefault`.
  - `shared/src/androidHostTest/.../db/MigrationTest.kt`: remove the `migrate1To2_*` test method;
    keep the `migrate2To3_*` one.
  - Delete the stale `shared/schemas/.../1.json` export (optional but tidy).
  - After this, run the migration test and confirm the 2→3 test still executes (non-zero count).

## Tests (required, both targets)
Add `BackupRepositoryTest` (or similar) in `shared/src/commonTest`, mirroring the phase 2.5.4
real-Room setup:
- **Full round-trip clone:** seed supplies (incl. an archived one and a CUSTOM one with a
  colorIndex), events (incl. notes/tags and two at the SAME millisecond), and non-default
  settings (e.g. applianceType=ONE_PIECE) → export → restore into a FRESH seeded-default DB →
  assert supply_types, change_events (all columns, ids, both same-ms rows), and settings
  match the source exactly.
- **Bad input safety:** malformed JSON and a wrong-schemaVersion doc are rejected AND leave
  the pre-existing data untouched (prove the DB still has its original rows).
- **Oversized input** (> guard) rejected untouched.

## Validation (checked when done — real results, not just BUILD SUCCESSFUL)
```
./gradlew :shared:testAndroidHostTest
./gradlew :shared:iosSimulatorArm64Test
./gradlew :composeApp:testDebugUnitTest   # if ViewModel logic changed
./gradlew ktlintCheck detekt
./gradlew :androidApp:assembleDebug        # compiles end to end
```
- Confirm new tests actually EXECUTED via `shared/build/test-results/*/TEST-*.xml`
  (a skipped test task looks like success — CLAUDE.md). Report counts.
- `shared` still has NO Compose imports; no `!!`; all user-facing strings externalized.

## Deliverable
Commit on `feat-00-full-backup`, message co-authored line:
`Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>`. Do not push/PR unless asked.
Report changed files, the round-trip test evidence, lint result, and any deviations.
Note in the report that `01-product-spec.md` §3.6/§3.10 still describe CSV export and should
be updated to the JSON backup (flag it; a docs pass can follow).
```
