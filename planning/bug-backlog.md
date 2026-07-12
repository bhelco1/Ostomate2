# Ostomate 2.0 — Bug Backlog

Bugs and UX issues found during physical device testing. Work through these before Phase 3.

---

## Setup / Onboarding

### [x] BUG-01: 1-piece appliance users cannot complete setup
**Screen:** Setup/onboarding flow  
**Problem:** No question for appliance type. Users with a 1-piece system hit a 2-piece workflow (separate bag + flange entries) and cannot get through setup.  
**Fix:** Add a "1-piece or 2-piece appliance?" question early in setup. Store the preference. 1-piece users should only log one item per change with no separate flange entry. Preference must flow through throughout the app.

### [x] BUG-02: "How many on hand?" field shows leading zero (e.g. "0200") — fixed (OnboardingViewModel: empty init + "0" placeholder + Number keyboard); verified 2026-07-12
**Screen:** Setup — supply quantity entry  
**Problem:** The field initializes to `0`. When the user taps and types a number, the `0` stays, producing values like `0200`.  
**Fix:** Clear the field on focus (select-all on tap), or use `KeyboardType.Number` with an empty initial value and a `0` placeholder.

### [x] BUG-03: "Next" button not reachable on iPhone — fixed (OnboardingScreen: imePadding + verticalScroll); verified 2026-07-12
**Screen:** Setup flow  
**Problem:** On a physical iPhone, the keyboard covers the bottom of the screen and the Next button is hidden behind it. Users have no way to proceed.  
**Fix:** Wrap the setup screen content in a `ScrollView` (or `imePadding` + `verticalScroll`) so the Next button scrolls into view above the keyboard.

---

## Navigation

### [x] BUG-04: Settings sub-menu stays open when tapping Settings tab again or switching tabs — fixed (App.kt: Settings tab restoreState=false + popUpTo start); verified 2026-07-12
**Screen:** Settings → Manage Supplies (and other sub-menus)  
**Problem 1:** While inside a sub-menu (e.g. Manage Supplies), tapping the Settings bottom-nav button again does nothing — it should return to the Settings root.  
**Problem 2:** Navigating away (e.g. tap Home) then tapping Settings returns to the sub-menu instead of the Settings root.  
**Fix:** On Settings tab re-tap, pop to root of the Settings nav stack. On tab switch-away and back, reset the Settings nav stack to root.

---

## QR Labels / Printing

### [x] BUG-05: Print button visible and tappable when no printer is configured — fixed (QrLabelsScreen: gated by qrPrinter.isPrintingAvailable()); verified 2026-07-12
**Screen:** QR Labels  
**Problem:** If the device has no printer set up, the Print button still appears active. Tapping it either fails silently or shows a confusing error. Poor UX.  
**Fix:** Check printer availability before showing the button (e.g. `UIPrintInteractionController.isPrintingAvailable` on iOS). If no printer is available, hide or disable the button, or show a tooltip explaining that no printer is set up.

### [x] BUG-06: QR code Share button sends text instead of a QR code image — fixed (QrLabelsScreen: encodeToPng + shareBytes image/png); verified 2026-07-12
**Screen:** QR Labels — Share  
**Problem:** The share sheet sends raw text data rather than an image of the QR code. The recipient gets a string that does not function as a QR code.  
**Fix:** Render the QR code composable to a bitmap/image first, then share the image (PNG or PDF). Do not share the raw string.

### [x] BUG-07: Print button does not work with HP printer app on iPhone
**Screen:** QR Labels — Print  
**Problem:** On an iPhone with the HP Smart app installed and a printer configured, tapping Print does nothing (or fails).  
**Fix:** Investigate whether the iOS print path is using `UIPrintInteractionController` correctly. Ensure the printable content is provided as a `UIPrintPageRenderer` or `UIPrintFormatter` compatible type. Test with HP Smart.

---

## Backup & Restore

### [ ] FEAT-00: Full-state backup & restore — survive a "new phone"
**Goal:** Back up and restore so a new phone is indistinguishable from the old one — all data and settings, IDs and relationships intact. Replaces the CSV backup entirely (see "Retire CSV" below).

**Motivating bug (confirmed on-device):** the current CSV restore silently drops all events for custom supply types. Bobby's 2026-06-17 backup held 17 events (11 Bag, 3 Flange, 3 custom: rings ×2, junk ×1); after restore the 14 Bag/Flange events returned but all 3 custom events were gone. Root cause: `BackupRepository.importCsv` remaps rows by `supply_kind`, handling only BAG/FLANGE (`else -> null -> continue`, `BackupRepository.kt:50-55`), and the CSV never carries supply *definitions*, so custom supplies can't be recreated on a fresh device. The full-state backup below subsumes this fix — a backup that carries supply definitions and maps by ID cannot drop custom supplies.

**What must be captured (the entire persisted surface):**
- Room `supply_types` — id, name, kind, boxSize, warnThresholdDays, onHand, sortOrder, archived, colorIndex
- Room `change_events` — id, supplyTypeId, timestampMillis, note, tags, createdAtMillis, editedAtMillis
- DataStore `AppSettings` — onboardingDone, lockSettings, localeOverride, crashReportingEnabled, applianceType, devMode
- **Not** captured (rebuildable): notification schedule, QR labels, framework caches.

**Format & approach:** a single versioned structured document (JSON) produced by reading **through the DAOs** in commonMain — sections `settings`, `supplyTypes[]` (with ids), `events[]` (with FKs + all columns), wrapped in `{ formatVersion, schemaVersion, exportedAt }`.
- Query-based read (not a raw `.db` file copy) is deliberate: **verified today that Bobby's main `.db` was 4 KB with 1.3 MB of live data in the uncheckpointed `-wal`** — a file copy would back up an almost-empty DB. Reading through Room always sees live data. It's also cross-platform-clean and version-mappable; a raw file copy is schema-coupled and the DataStore file isn't portable Android↔iOS.
- **Preserve supply_type IDs** so history stays wired to the right supply and ordering/colors/on-hand/thresholds survive.

**Two operations, kept distinct:**
- **Restore (replace)** — new capability for migration: wipe the fresh device's seeded defaults and load the backup as an exact clone. Destructive → requires an explicit confirmation.
- (No merge/import mode ships with this unless v1 migration is retained — see below.)

**Retire CSV and all v1 compatibility (decided 2026-07-12):** JSON full-backup becomes the only format.
- Remove CSV *export* entirely (no one analyzes the data).
- Remove v1 CSV *import* and everything v1-related: `CsvV1Importer`, the v1 branch in `BackupRepository.importCsv`, the "Import v1" Settings item + its strings, and the v1-specific tests (`CsvImporterTest`/`CsvMigrationTest` v1 cases). Bobby has already migrated his v1 data; new users have none.
- Separate judgment call (not "CSV", so decide explicitly): the Room `MIGRATION_1_2` schema migration (v1 spike DB → v2) is DB plumbing, not CSV import. Removing it permanently blocks upgrading any surviving v1-schema DB and touches the migration-test suite CLAUDE.md gates on. Bobby's device is already at schema v3; likely safe to drop, but confirm no v1-schema DB exists anywhere before removing.

**Privacy:** the backup is all health-adjacent data in plaintext. Fine under the local-first posture (sharing is user-initiated), but label the file as sensitive.

**Tests:** round-trip a full clone (supplies incl. archived + custom, events incl. notes/tags + same-millisecond rows, all settings) on both platforms; restore-replace into a seeded-default DB yields an exact match of the source.

---

## Duplicate Change Events

### [ ] BUG-09: Duplicate change events — Android replays the deep-link intent on Activity recreation
**Screen:** QR log deep link → `MainActivity` → `ChangeEventRepository.handleDeepLink`
**Problem:** One QR scan produces 2–3 change events for the same supply within the same minute. **Confirmed on-device:** survivors show the fingerprint — ids 37/38 are a bag+flange written **1 ms apart** (consecutive rows), and ids 36/38 are two flanges **8 s apart**, both outside the existing debounce window.
**Root cause (prime suspect):** `MainActivity.onCreate` calls `handleDeepLink(intent)` unconditionally (`MainActivity.kt:38`) with no `savedInstanceState == null` guard and never consumes the intent. `launchMode=singleTask` prevents new-instance dupes, but any Activity recreation (rotation, dark-mode toggle, POST_NOTIFICATIONS dialog at first launch, process-death restore) re-runs `onCreate` with the *same* `ostomate://log?item=…` still attached, logging again. The only protection is a 3 s, in-memory, per-item debounce (`ChangeEventRepository.kt:12,63-68`), which is escaped by:
- any recreation landing >3 s after the scan;
- a **non-atomic check-then-set** across coroutines (`onCreate` + `onNewIntent` each `launch` a coroutine; both can read a stale `lastScanMillis` before either writes — explains the 1 ms-apart pair);
- process death wiping the in-memory map;
- per-item keying (a `bag` and `flange` never debounce each other).
**Fix:**
- In `onCreate`, only handle the deep link when `savedInstanceState == null`, and consume the intent after handling (`intent.data = null` / `setIntent`) so no recreation can replay it. This kills *phantom* re-fires at the source — silently, with no user-facing prompt (see FEAT-01: never confirm an event the user did not consciously trigger).
- Make the debounce atomic (confine the check-and-set) and durable (persist last-scan) so races and process death can't defeat it.
- Belt-and-suspenders at the data layer: reject an identical `(supply, source=qr)` insert within a short window regardless of entry path.
- Regression test the recreation-replay path (rotation / config change after a deep link inserts exactly one event).

---

## Feature Requests

### [ ] FEAT-01: Confirm rapid-repeat changes instead of silently suppressing
**Area:** Change logging (home-screen log buttons + QR deep link)
**Request:** If a genuine, user-initiated change is logged for a supply within a configurable window (default ~10 min) of the previous one, show a confirmation ("You logged a Bag 2 minutes ago — add another?") instead of writing it blindly. Catches fat-finger double-taps (notably older/less-technical users mashing buttons) and re-scans by someone unsure the first registered, while reassuring them it did.
**Why not silent suppression:** a silent long window would eat *legitimate* immediate re-changes (a bag/flange that fails right after application and is redone within minutes) — the same class of quiet data loss as BUG-08. Confirmation hands the ambiguous case to the only one who knows: the user.
**Key rule:** confirm what the **user** did twice; silently drop what the **framework** did twice. Phantom re-fires (BUG-09 intent replay) must be eliminated at the source and must **never** raise this dialog, or the user sees a prompt for something they never did.
**Implementation note:** the QR path currently commits in `handleDeepLink` and only posts a snackbar. Confirming means holding a *pending* change and routing it through the UI (dialog → commit) rather than writing straight to the DB — moves the commit decision from the repository to the UI layer.

### [ ] FEAT-02: Exportable local diagnostic/support log
**Area:** Diagnostics / Support (local-first)
**Request:** A rolling on-device log the user can export and send on request (reuse `FileSharer`, same plumbing as CSV backup). Diagnostics now (confirms BUG-09 the next time it recurs), support channel later as more users come on.
**Privacy constraint:** must stay local-first — no analytics, no network, no silent collection (`06-security-privacy.md`). Support = the user chooses to export and share; nothing is phoned home.
**First entries — scan audit for BUG-09:** each `handleDeepLink` call records `{uri, entry-point (onCreate / onNewIntent / iOS onOpenURL), savedInstanceState-was-null, wall-clock, debounce decision, resulting event id}`. Optionally tag `change_events` with a `source` (`qr` / `manual` / `widget`; the unused `note`/`tags` columns can hold it) so duplicates self-identify.
