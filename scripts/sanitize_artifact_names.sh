#!/usr/bin/env bash
# Replaces the characters actions/upload-artifact rejects (" : < > | * ?) in every file and
# directory name under $1. With --format junit, Maestro names its debug files after the flow's
# title ("commands-(Journey 3: Edit/delete event).json"), and a single ':' fails the whole upload,
# so a failing E2E run ships no evidence at all.
set -uo pipefail

root="${1:?usage: sanitize_artifact_names.sh <dir>}"
[ -d "$root" ] || exit 0

# -depth renames children before their parent, so no path is invalidated mid-walk.
find "$root" -depth -name '*[":<>|*?]*' -print0 | while IFS= read -r -d '' path; do
  dir=$(dirname "$path")
  base=$(basename "$path")
  mv "$path" "$dir/$(printf '%s' "$base" | tr '":<>|*?' '_______')"
done
exit 0
