#!/usr/bin/env bash
# Android Maestro E2E runner.
#
# Lives in a file rather than inline in ci.yml because reactivecircus/android-emulator-runner
# executes its `script:` one LINE at a time (each line is its own `sh -c`), so multi-line
# shell constructs are a syntax error and variables do not survive between lines.
#
# Runs every flow even after one fails, so a red run reports ALL broken flows instead of
# only the first — otherwise each bug costs a full ~20-minute CI cycle to find.
set -uo pipefail

FLOWS=(
  .maestro/02_log_and_undo.yaml
  .maestro/03_edit_delete_event.yaml
  .maestro/04_set_inventory.yaml
  .maestro/05_backup_round_trip.yaml
  .maestro/08_biometric_gate.yaml
)

fail=0
passed=()
failed=()

for flow in "${FLOWS[@]}"; do
  echo "::group::maestro $flow"
  # --debug-output is a GLOBAL flag and must precede the `test` subcommand — placed after
  # it, it is silently ignored and no artifacts are written at all. It keeps the UI
  # hierarchy + screenshots, without which "element not found" can only be guessed at.
  if maestro --debug-output maestro-debug test "$flow"; then
    passed+=("$flow")
  else
    failed+=("$flow")
    fail=1
  fi
  echo "::endgroup::"
done

echo "===== E2E SUMMARY ====="
for f in "${passed[@]:-}"; do [ -n "$f" ] && echo "PASS  $f"; done
for f in "${failed[@]:-}"; do [ -n "$f" ] && echo "FAIL  $f"; done
echo "======================="

exit "$fail"
