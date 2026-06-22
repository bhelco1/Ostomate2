# Ostomate 2.0 — Test Strategy & QA Plan

> **Purpose.** This is the *target-state* document: what good looks like, why,
> and the staged path to get there. For the day-to-day "what exists and how to
> run it" checklist, see `04-test-plan.md`. For cost rules that constrain CI
> choices, see `07-business-plan.md`.
>
> **Status:** proposal / not yet implemented. Created 2026-06-22 from a QA audit.
> Nothing here changes code or CI until each item is scheduled in `05-dev-plan.md`.

---

## 1. Where we are (audited 2026-06-22)

### Test inventory — measured from source, not docs

| Type | Location | Count | Runs on | Gated on PRs? |
|---|---|---:|---|---|
| Domain unit tests | `shared/commonTest` | 57 | JVM + iOS sim | ✅ JVM only |
| Data / integration tests | `shared/iosTest` | 12 | iOS sim only | ❌ no |
| E2E flows (Maestro) | `.maestro/` | 7 files (5 in CI) | Android emulator | ❌ no |
| Static analysis | CI | ktlint, detekt, SwiftLint | mixed | ⚠️ partial |
| Manual device checklist | `04-test-plan.md` | ~13 items × 2 OS | human | ❌ no |
| **ViewModel tests** | — | **0** | — | — |
| **Compose UI tests** | — | **0** | — | — |
| **iOS native / XCUITest** | — | **0** | — | — |
| **Code coverage** | — | **0 (no tooling)** | — | — |

**69 shared tests total** (57 JVM-runnable + 12 iOS-sim-only), all green.

### The pyramid is inverted and hollow

```
                    MANUAL ONLY
        ┌───────────────────────────────────┐
  E2E   │ Maestro: 5 flows in CI (Android)   │  iOS E2E = 0 (manual)
        │ + 2 flows not wired to CI          │
        ├───────────────────────────────────┤
  UI /  │   ███   EMPTY   ███   (0 tests)    │  7 ViewModels, 0 tests —
  VM    │                                    │  contradicts our own policy
        ├───────────────────────────────────┤
  DATA  │ 12 tests (Room/DataStore/migrate)  │  iOS-sim only, ungated on PRs
        ├───────────────────────────────────┤
  UNIT  │ 57 tests (pure domain logic)       │  the only solid layer
        └───────────────────────────────────┘
```

### What CI actually gates, by trigger

```
PULL REQUEST (the gate that matters most):
  ✅ ktlint   ✅ detekt   ✅ Android APK build   ✅ 57 JVM tests
  ❌ 12 data/migration tests   ❌ iOS build   ❌ iOS tests   ❌ E2E   ❌ SwiftLint

MERGE TO main (after the fact — too late to block a bad merge):
  everything above + iOS build/tests + SwiftLint + 5 Maestro flows
```

This is the central structural problem: **the heavy validation runs *after*
code already lands on `main`.** A PR can be green while breaking the entire iOS
build, every Room migration, and every E2E flow. The macOS-runner cost rule in
`07-business-plan.md` is the *reason* for this, but the current split throws away
more safety than it needs to (see §4).

---

## 2. Coverage gaps, ranked

1. **ViewModels have zero tests — and this contradicts our written policy.**
   `04-test-plan.md` says "cover the ViewModel's UiState logic instead" of
   composables. 7 ViewModels exist (`Home`, `Settings`, `Calendar`, `History`,
   `Stats`, `Onboarding`, `ManageSupplies`); none are tested. The stated
   mitigation does not exist.

2. **The bug backlog is the proof the suite tests the wrong layer.** Every device
   bug (BUG-02 leading zero, BUG-03 keyboard covers Next, BUG-04 nav stack,
   BUG-05 print button, BUG-06 share-as-text) is a UI/integration/state defect.
   The 69 tests caught **none** of them. We test the calculator under the app,
   not the app.

3. **iOS has no automated UI/E2E coverage at all.** Maestro is Android-only.
   Most backlog bugs are iOS-specific. iOS correctness is 100% manual.

4. **PRs don't gate the things most likely to break** — migrations, iOS build,
   E2E. The architecture rule "every version bump ships a migration + migration
   test" is undermined when those tests don't run on PRs.

5. **Untested shared classes:** `BackupRepository`, `ChangeEventRepository`,
   `SupplyRepository`, `SupplyTypeDao`, `NotificationScheduler`. Backup/restore
   round-trip and reorder-notification logic are core, data-loss-adjacent paths.

6. **No coverage measurement exists.** Kover is in the catalog but disabled
   (incompatible with the KMP Android library plugin). We cannot answer "what %
   is covered?" today — every "coverage gate" in the docs is unmeasured.

7. **Migration tests are shallow.** Schema is at v3; each migration test is a
   single happy-path case with no data-preservation assertions.

8. **Weak E2E assertions.** The biometric flow (`08`) asserts emulator
   auto-unlock — it validates the emulator, not our gate logic.

9. **No record of runs over time** — no history, trend, flakiness tracking, or
   human-readable report artifact. Results vanish into CI logs.

10. **Documentation drift** (now corrected in `04`): the count read 60 vs the
    actual 69, and the suite table conflated `commonTest` with `iosTest`.

---

## 3. Target state — the full pyramid

Ambition level: **comprehensive** (chosen 2026-06-22). We build the complete
pyramid, not just the gating reshuffle. Layers, bottom to top:

| Layer | Target | Tooling | Notes |
|---|---|---|---|
| **Unit (domain)** | Keep ≥ current 57; add as logic grows | kotlin.test | Already strong. Add mutation testing here (small, pure → high ROI). |
| **Repository / data** | Test all 5 untested repos + deeper migrations | kotlin.test + in-mem Room | Backup/restore round-trip is priority #1 (data-loss risk). |
| **ViewModel** | All 7 ViewModels: every `UiState` transition | kotlin.test + Turbine-style StateFlow asserts, fake repos | Fulfills existing policy. Where the real bugs live. |
| **Compose UI / screenshot** | Key screens: Home, Onboarding, Calendar, QrLabels | Compose UI test + Roborazzi/Paparazzi-style screenshot diff | Catches layout regressions like BUG-03. |
| **E2E — Android** | All 7 Maestro flows wired; stronger assertions | Maestro | Wire orphan flows `01`, `09`; fix the biometric assertion. |
| **E2E — iOS** | Onboarding, log, share, deep-link flows | Maestro iOS sim (preferred) or XCUITest | Closes the biggest hole; targets where device bugs cluster. |
| **Accessibility** | Home + Settings read correctly | Compose semantics asserts + manual VoiceOver/TalkBack | Automate the structural checks; keep manual for screen-reader feel. |
| **Manual device** | Keep the checklist as release sign-off | human | Always the last gate before release. |

### Regression-test discipline

Every closed bug in `bug-backlog.md` must leave a test behind at the lowest
layer that can catch it (BUG-02 → ViewModel/input test; BUG-03 → screenshot
test; BUG-04 → nav E2E; BUG-06 → share-payload unit/integration test). A bug
without a regression test is not "done."

---

## 4. Release gating — staged checkpoints

Each stage blocks promotion to the next. The design goal: **catch breakage at
the cheapest stage that can catch it**, while respecting the 10× macOS-runner
cost rule in `07-business-plan.md`.

```
COMMIT (local, free)
   └─ pre-commit hook: ktlint + detekt + SwiftLint
PULL REQUEST (ubuntu — cheap; this is the gate to strengthen)
   ├─ ALL 69 shared tests   ← move the 12 iOS-sim/migration tests onto PRs *
   ├─ Android APK build
   ├─ coverage report + floor check (JaCoCo, see §5)
   └─ iOS build — ONLY when iOS files changed (path filter), see below
MERGE TO main (allow macOS spend here)
   ├─ full iOS build + iOS shared tests + SwiftLint
   ├─ Maestro E2E — all 7 flows, Android
   ├─ Maestro E2E — iOS simulator (once §3 lands)
   └─ screenshot-diff suite
RELEASE CANDIDATE
   ├─ manual device checklist signed off (both OS)
   ├─ all E2E green
   └─ version-bump → migration-test-present check
PUBLISH
   ├─ store-listing / privacy-policy lint
   └─ signed artifact provenance
```

### The migration / iOS-on-PR decision (needs Bobby's sign-off)

The 12 `iosTest` data tests don't run on PRs today because they're in the iOS
target, which is post-merge-only to save macOS minutes. Two facts make this
fixable cheaply:

- **Migration + DAO + DataStore correctness can be proven on the JVM.** The
  recommendation is to make these tests run in the **JVM host run** too (e.g. a
  `commonTest`-visible Room/SQLite path, or duplicating them onto
  `testAndroidHostTest`) so a PR on ubuntu — **no macOS cost** — gates them.
- **Full iOS *build* still costs macOS minutes.** Run it on PRs **only when iOS
  files change**, via a GitHub Actions `paths:` filter
  (`iosApp/**`, `composeApp/src/iosMain/**`, `shared/src/ios*/**`,
  `*.gradle.kts`). Pure-domain PRs pay nothing extra; iOS-touching PRs get
  gated before merge.

> **APPROVED 2026-06-22:** migration/DAO tests → every PR on ubuntu; iOS build →
> PRs only via path filter; full Maestro/screenshot suites → post-merge. This
> recovers most PR safety at near-zero added cost.

---

## 5. Coverage measurement & floor

**Decision (2026-06-22): adopt JaCoCo on the JVM target now**; revisit Kover when
it supports `com.android.kotlin.multiplatform.library`.

- Wire JaCoCo to `:shared:testAndroidHostTest` (the 57 JVM tests). This is the
  unblocked path today; Kover's TODO in `shared/build.gradle.kts` stays as-is.
- **Publish before gating.** First, surface the number on every PR. Once we know
  the real baseline, set a **floor at that baseline** and ratchet upward — never
  set an aspirational target that fails the build on day one.
- Scope the floor to the **domain + ViewModel + repository** packages. Do not
  count generated Room code, DI wiring, or `@Composable` UI in the denominator —
  it distorts the signal.
- Coverage is a **floor, not a goal.** 100% line coverage of trivial getters is
  worthless; an untested backup round-trip at 60% overall is the real risk.

---

## 6. Recording & presenting results to a human

**Decision (2026-06-22): open to a third-party reporting service.**

> ⚠️ **Cost gate.** Per `07-business-plan.md`, any new recurring cost needs a
> written justification in that doc *before* adoption. Candidates below are split
> into free-tier-for-OSS vs paid. Default to the zero-cost GitHub-native baseline
> and only add a service if it earns its keep.

### Baseline (zero recurring cost — implement regardless)

- **Per-run summary:** write a `## Test Summary` block to the GitHub Actions
  **job summary** (rendered markdown): pass/fail per suite, coverage %, delta vs
  `main`. Readable without opening logs.
- **Artifacts:** upload JUnit XML + an HTML report each run, 90-day retention.
- **Trend:** a static dashboard page generated from JUnit XML, published via
  **GitHub Pages** (already used for `docs/`): green/red grid by suite, last-run
  timestamp, coverage sparkline. No hosting cost.
- **Badge:** README status + coverage badges updated by CI.

### Optional third-party (requires a cost case in `07-business-plan.md`)

| Service | What it adds | Cost posture |
|---|---|---|
| **Codecov** | Coverage trends, PR annotations, diff coverage | **Free — repo is public (confirmed 2026-06-22).** No cost case needed. |
| **Maestro Cloud** | Hosted iOS + Android E2E, parallelism, video | Paid. Evaluate only if local iOS E2E proves too flaky/slow. |
| **Datadog CI / Trunk** | Flaky-test detection, historical analytics | Paid. Defer until flakiness is an actual problem. |

**Recommendation:** ship the zero-cost baseline first, then add **Codecov** — the
repo is public so it's free, and its diff-coverage PR annotations pair well with
the §5 coverage floor. The GitHub Pages dashboard and Codecov are complementary,
not redundant (dashboard = suite health grid; Codecov = coverage-over-time +
per-PR diff). Reassess a paid E2E service only after the iOS automation lands.

---

## 7. Sequenced rollout (proposed — schedule into `05-dev-plan.md`)

Ordered by risk-reduction-per-effort:

1. **Close the PR gate** — migration/DAO/DataStore tests on ubuntu PRs; iOS build
   path-filter. *(highest impact, low effort, ~zero cost)*
2. **JaCoCo coverage** wired + published on PRs. Set floor at baseline.
3. **ViewModel tests** for all 7 ViewModels. *(fulfills existing policy)*
4. **Repository tests** — backup/restore round-trip first, then the other four.
5. **Zero-cost reporting** — Actions job summary + JUnit artifacts + Pages
   dashboard + badges.
6. **iOS E2E** via Maestro simulator (onboarding, log, share, deep-link).
7. **Screenshot tests** for Home, Onboarding, Calendar, QrLabels.
8. **Wire orphan Maestro flows** `01`, `09`; strengthen the biometric assertion.
9. **Comprehensive extras** — mutation testing on domain, automated a11y
   semantics, flakiness tracking (and the Codecov/third-party decision).

Each item lands with its own tests and a `05-dev-plan.md` checklist entry. None
of this is implemented yet — this document is the plan, not the change.

---

## 8. Decisions

- [x] **Migration/iOS-on-PR split** (§4) — *Approved 2026-06-22.* Migration/DAO
      tests run on ubuntu PRs; iOS build runs on PRs only via path filter; full
      Maestro/screenshot suites stay post-merge.
- [x] **Repo visibility** — *Public, confirmed 2026-06-22.* Codecov is free;
      adopted in §6 with no cost case required.
- [ ] **Screenshot-test engine** — Roborazzi vs Paparazzi vs Compose-native.
      *Deferred 2026-06-22* — decide when rollout item 7 is scheduled.
