# Implementation Brief — BUG-09: Duplicate change events (Android deep-link intent replay)

You are implementing a bug fix in the Ostomate 2.0 KMP repo. Work **only** on the current
git branch `bug-09-duplicate-events` (already created off `main`). Do not start other
backlog items. Follow the repo's `CLAUDE.md` and architecture rules exactly.

## The bug (confirmed, not speculative)

One QR scan of `ostomate://log?item=bag|flange` produces 2–3 change events for the same
supply within the same minute. On-device evidence: consecutive rows written 1 ms apart
(a bag+flange race) and same-item duplicates 8 s apart (outside the existing debounce).
Full write-up: `planning/bug-backlog.md` → **BUG-09**.

Two independent causes, both must be fixed:

1. **Android replays the launch intent on Activity recreation.** `MainActivity.onCreate`
   calls `handleDeepLink(intent)` unconditionally, with no `savedInstanceState == null`
   guard and never consuming the intent. Any recreation (rotation, dark-mode toggle, the
   POST_NOTIFICATIONS permission dialog on first launch, process-death restore) re-runs
   `onCreate` with the same deep-link URI still attached → logs again. `launchMode=singleTask`
   (see `androidApp/src/main/AndroidManifest.xml`) already prevents new-instance dupes, so
   the fix is the guard + intent consumption.

2. **The debounce is a non-atomic check-then-set across coroutines.**
   `ChangeEventRepository.handleDeepLink` (`shared/.../data/ChangeEventRepository.kt`) reads
   `lastScanMillis[item]`, checks a 3 s window, then writes — on a plain `mutableMapOf`,
   from coroutines launched by both `onCreate` and `onNewIntent`. Two near-simultaneous
   deliveries both read the stale value and both insert (the 1 ms-apart race).

## Required changes

### 1. `androidApp/src/main/kotlin/com/ostomate/app/MainActivity.kt`
- In `onCreate`, only handle the deep link on a fresh start:
  `if (savedInstanceState == null) handleDeepLink(intent)`.
- In `onNewIntent`, call `setIntent(intent)` before `handleDeepLink(intent)` so
  `getIntent()` reflects the latest.
- In `handleDeepLink`, **consume** the link after reading it so nothing can replay it:
  after `val uri = intent?.data?.toString() ?: return`, set `intent.data = null`.

### 2. `shared/src/commonMain/kotlin/com/ostomate/app/data/ChangeEventRepository.kt`
- Make the debounce check-and-set atomic with a `kotlinx.coroutines.sync.Mutex`
  (`private val scanMutex = Mutex()`), wrapping the read-window-check-and-write in
  `scanMutex.withLock { ... }` so it returns a single boolean "allow" decision. Keep the
  existing `DEEP_LINK_DEBOUNCE_MS = 3_000L` and per-item keying. Do NOT widen the window
  here — the longer confirmation window is a separate item (FEAT-01).
- Keep `shared` free of Compose imports (architecture rule).

### Scope boundaries — do NOT do these here
- No confirmation dialog / longer window (that is FEAT-01).
- No diagnostic logging (that is FEAT-02) — though if trivial, a single-line debug log of
  the debounce decision is acceptable, not required.
- No changes to iOS deep-link handling unless a test proves a defect there.
- Do not touch the backup/CSV code (FEAT-00).

## Tests (required — code lands with tests)

Add to `shared/src/commonTest` a `ChangeEventRepository` concurrency test that proves the
atomic debounce. Follow the existing real-Room repository test pattern from phase 2.5.4
(look for existing repository tests using an in-memory Room DB in `shared/src/commonTest`;
mirror their setup). The test must:
- Seed a BAG supply, then invoke `handleDeepLink("ostomate://log?item=bag")` **twice
  concurrently** (e.g. `awaitAll(async{...}, async{...})`) within the debounce window.
- Assert **exactly one** `change_events` row was inserted and `onHand` decremented by
  exactly one.
- A second test: two sequential calls >3 s apart (advance/override the clock) insert two.

For the Android Activity fix (intent replay), add a Robolectric/host test if the repo has a
pattern for driving `MainActivity`; otherwise document the manual check in the PR body:
scan once → one event; rotate the device → still one event.

## Validation (what will be checked when you are done)

Run and confirm green — and verify **execution counts**, not just BUILD SUCCESSFUL
(per CLAUDE.md, a skipped test task looks like success):
```
./gradlew :shared:testAndroidHostTest
./gradlew :shared:iosSimulatorArm64Test
./gradlew ktlintCheck detekt
```
- Confirm the new tests actually ran: check `shared/build/test-results/*/TEST-*.xml` for the
  new test methods and non-zero counts.
- Confirm `MainActivity.onCreate` has the `savedInstanceState == null` guard and the intent
  is consumed (`intent.data = null`).
- Confirm `ChangeEventRepository` uses a `Mutex` around the debounce.
- No `!!`, no new hard-coded user-facing strings, `shared` has no Compose imports.

## Deliverable
Commit on `bug-09-duplicate-events` with a clear message. End the commit message with:
`Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>`
Do not open a PR or push unless asked. Summarize what changed and paste the test-count
evidence so it can be validated.
