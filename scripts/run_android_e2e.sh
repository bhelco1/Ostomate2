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
  .maestro/10_biometric_gate_pin.yaml
)

# Journey 10 needs a credential for the biometric gate to ask for. Set only around that
# flow and always cleared after: a PIN left set would put a lock screen in front of every
# later flow.
GATE_PIN_FLOW=10_biometric_gate_pin

DIAG="$PWD/e2e-diagnostics"
RESULTS="$PWD/e2e-results"
mkdir -p "$DIAG" "$RESULTS"

fail=0
passed=()
failed=()

for flow in "${FLOWS[@]}"; do
  name=$(basename "$flow" .yaml)
  echo "::group::maestro $flow"

  adb logcat -c || true
  if [ "$name" = "$GATE_PIN_FLOW" ]; then
    adb shell locksettings set-pin 1234 || echo "::warning::could not set the screen-lock PIN"
  fi

  # --debug-output must come AFTER `test` (2.6.1 rejects it as a global flag). Absolute
  # path: a relative one has not been landing anywhere the upload step can find.
  # One JUnit file per flow: a single --output would be overwritten by each flow in turn.
  if maestro test --debug-output "$DIAG/$name/maestro" \
    --format junit --output "$RESULTS/$name.xml" "$flow"; then
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

  if [ "$name" = "$GATE_PIN_FLOW" ]; then
    adb shell locksettings clear --old 1234 || echo "::warning::could not clear the screen-lock PIN"
  fi
  echo "::endgroup::"
done

# FEAT-04: an emulator has no NFC chip, so fire the intent Android sends for a tag holding
# our link and require that it resolves to MainActivity. The app reads the link from
# intent.data whatever the action, so flow 01 already covers what happens next.
NFC_TEST="Journey 11: NFC sticker opens Ostomate"
nfc_out=$(adb shell am start -W -a android.nfc.action.NDEF_DISCOVERED \
  -d "ostomate://log?item=flange" 2>&1)
echo "$nfc_out"
if echo "$nfc_out" | grep -q "com.ostomate.app/.MainActivity"; then
  passed+=("$NFC_TEST")
  nfc_case="<testcase classname=\"$NFC_TEST\" name=\"$NFC_TEST\"/>"
  nfc_failures=0
else
  failed+=("$NFC_TEST")
  fail=1
  nfc_case="<testcase classname=\"$NFC_TEST\" name=\"$NFC_TEST\"><failure message=\"NDEF_DISCOVERED did not resolve to MainActivity\"/></testcase>"
  nfc_failures=1
fi
cat > "$RESULTS/11_nfc_intent_filter.xml" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<testsuites><testsuite name="$NFC_TEST" tests="1" failures="$nfc_failures">$nfc_case</testsuite></testsuites>
EOF

bash "$(dirname "$0")/sanitize_artifact_names.sh" "$DIAG"

echo "===== E2E SUMMARY ====="
for f in "${passed[@]:-}"; do [ -n "$f" ] && echo "PASS  $f"; done
for f in "${failed[@]:-}"; do [ -n "$f" ] && echo "FAIL  $f"; done
echo "======================="

exit "$fail"
