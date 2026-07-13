#!/usr/bin/env bash
# Android Maestro E2E runner.
#
# Lives in a file rather than inline in ci.yml because reactivecircus/android-emulator-runner
# executes its `script:` one LINE at a time (each line is its own `sh -c`), so multi-line
# shell constructs are a syntax error and variables do not survive between lines.
#
# Runs every flow even after one fails, so a red run reports ALL broken flows instead of
# only the first — otherwise each bug costs a full ~20-minute CI cycle to find.
#
# On failure it captures evidence, because "Element not found" is undiagnosable without it:
#   logcat    — a FATAL EXCEPTION here means the app crashed (a real bug), not a test bug
#   focus     — names the window that actually had focus; if it is the launcher, the app
#               was backgrounded (e.g. a tap landed on the system nav bar), not crashed
#   hierarchy — the tree Maestro was actually looking at when it failed to find the element
#   screen    — what was on screen at that moment
set -uo pipefail

FLOWS=(
  .maestro/01_cold_start_qr_log.yaml
  .maestro/02_log_and_undo.yaml
  .maestro/03_edit_delete_event.yaml
  .maestro/04_set_inventory.yaml
  .maestro/05_backup_round_trip.yaml
  .maestro/08_biometric_gate.yaml
  .maestro/09_store_screenshots.yaml
)

DIAG="$PWD/e2e-diagnostics"
mkdir -p "$DIAG"

fail=0
passed=()
failed=()

for flow in "${FLOWS[@]}"; do
  name=$(basename "$flow" .yaml)
  echo "::group::maestro $flow"

  adb logcat -c || true

  # --debug-output must come AFTER `test` (2.6.1 rejects it as a global flag). Absolute
  # path: a relative one has not been landing anywhere the upload step can find.
  if maestro test --debug-output "$DIAG/$name/maestro" "$flow"; then
    passed+=("$flow")
  else
    failed+=("$flow")
    fail=1

    echo "--- capturing failure evidence for $name ---"
    mkdir -p "$DIAG/$name"
    adb logcat -d > "$DIAG/$name/logcat.txt" 2>&1 || true
    adb shell dumpsys window \
      | grep -E "mCurrentFocus|mFocusedApp|mFocusedWindow" > "$DIAG/$name/focus.txt" 2>&1 || true
    adb shell dumpsys activity activities \
      | grep -E "mResumedActivity|topResumedActivity" >> "$DIAG/$name/focus.txt" 2>&1 || true
    adb exec-out screencap -p > "$DIAG/$name/screen.png" 2>/dev/null || true
    adb shell uiautomator dump /sdcard/hier.xml >/dev/null 2>&1 \
      && adb pull /sdcard/hier.xml "$DIAG/$name/hierarchy.xml" >/dev/null 2>&1 || true

    # Surface the two decisive facts straight into the job log, so the answer is visible
    # without downloading anything.
    echo "### FOCUS AT FAILURE ($name)"
    cat "$DIAG/$name/focus.txt" 2>/dev/null || echo "(none captured)"
    echo "### APP CRASH? ($name)"
    grep -aiE "FATAL EXCEPTION|ANR in|Process com.ostomate.app .*died|force-finishing" \
      "$DIAG/$name/logcat.txt" | head -20 || echo "(no crash signature in logcat)"
  fi

  echo "::endgroup::"
done

echo "===== E2E SUMMARY ====="
for f in "${passed[@]:-}"; do [ -n "$f" ] && echo "PASS  $f"; done
for f in "${failed[@]:-}"; do [ -n "$f" ] && echo "FAIL  $f"; done
echo "======================="

exit "$fail"
