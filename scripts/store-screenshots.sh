#!/usr/bin/env bash
# Regenerates the store screenshots and the Play feature graphic.
#
#   scripts/store-screenshots.sh ios              # App Store screenshots (6.9" + 6.3")
#   scripts/store-screenshots.sh feature-graphic  # Play feature graphic
#
# iOS: builds the app for the simulator, then for each required display size creates a FRESH
# simulator (never a physical device, never one of the shared simulators — the flow runs
# clearState), cleans the status bar, drops the fake demo backup into Files > On My iPhone,
# runs .maestro/ios/09_ios_store_screenshots.yaml (which restores it through Settings >
# Restore backup) and copies the shots to fastlane/screenshots/en-US/. The simulator is
# deleted afterwards. Set APP=/path/to/Ostomate.app to skip the build, IOS_RUNTIME to pick
# another simulator runtime.
#
# Requires: Xcode, Maestro 2.11.0 (~/.maestro/bin), python3.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MAESTRO="${MAESTRO:-$HOME/.maestro/bin/maestro}"
WORK="$(mktemp -d "${TMPDIR:-/tmp}/ostomate-store.XXXXXX")"
IOS_RUNTIME="${IOS_RUNTIME:-com.apple.CoreSimulator.SimRuntime.iOS-27-0}"

# Device type -> output file prefix. Apple: "At least one screenshot for iPhone with Dynamic
# Island (medium display)" is required; the 6.9" set is the largest accepted size and is what
# App Store Connect scales down for the other large displays.
IOS_DEVICES=(
  "com.apple.CoreSimulator.SimDeviceType.iPhone-17-Pro-Max|iPhone 17 Pro Max|1320x2868"
  "com.apple.CoreSimulator.SimDeviceType.iPhone-17-Pro|iPhone 17 Pro|1206x2622"
)

SIM_UDID=""
cleanup() {
  if [ -n "$SIM_UDID" ]; then
    xcrun simctl shutdown "$SIM_UDID" >/dev/null 2>&1 || true
    xcrun simctl delete "$SIM_UDID" >/dev/null 2>&1 || true
  fi
  rm -rf "$WORK"
}
trap cleanup EXIT

check_size() { # file expected(WxH)
  local w h
  w=$(sips -g pixelWidth "$1" | awk '/pixelWidth/ {print $2}')
  h=$(sips -g pixelHeight "$1" | awk '/pixelHeight/ {print $2}')
  if [ "${w}x${h}" != "$2" ]; then
    echo "ERROR: $1 is ${w}x${h}, expected $2" >&2
    exit 1
  fi
  echo "  ${w}x${h}  ${1#"$ROOT"/}"
}

build_ios() {
  if [ -n "${APP:-}" ]; then return; fi
  echo "== building Ostomate.app for the simulator"
  (cd "$ROOT/iosApp" && xcodebuild -project iosApp.xcodeproj -scheme iosApp -configuration Debug \
    -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' \
    ARCHS=arm64 ONLY_ACTIVE_ARCH=YES CODE_SIGNING_ALLOWED=NO \
    -derivedDataPath "$WORK/dd" build >"$WORK/xcodebuild.log" 2>&1) \
    || { tail -40 "$WORK/xcodebuild.log"; exit 1; }
  APP="$WORK/dd/Build/Products/Debug-iphonesimulator/Ostomate.app"
}

# The Files app's "On My iPhone" is the LocalStorage file provider's app-group container.
place_demo_backup() { # udid json
  local groups="$HOME/Library/Developer/CoreSimulator/Devices/$1/data/Containers/Shared/AppGroup"
  local d id
  for d in "$groups"/*; do
    id=$(plutil -extract MCMMetadataIdentifier raw \
      "$d/.com.apple.mobile_container_manager.metadata.plist" 2>/dev/null || true)
    if [ "$id" = "group.com.apple.FileProvider.LocalStorage" ]; then
      mkdir -p "$d/File Provider Storage"
      cp "$2" "$d/File Provider Storage/ostomate_demo_backup.json"
      return
    fi
  done
  echo "ERROR: no Files 'On My iPhone' container on simulator $1" >&2
  exit 1
}

run_ios() {
  build_ios
  local out="$ROOT/fastlane/screenshots/en-US"
  local backup="$WORK/ostomate_demo_backup.json"
  python3 "$ROOT/scripts/store_demo_backup.py" "$backup"
  mkdir -p "$out"
  rm -f "$out"/*.png

  local entry devtype name size shots f
  for entry in "${IOS_DEVICES[@]}"; do
    IFS='|' read -r devtype name size <<<"$entry"
    echo "== $name ($size) on $IOS_RUNTIME"
    SIM_UDID=$(xcrun simctl create "ostomate-store-screenshots" "$devtype" "$IOS_RUNTIME")
    xcrun simctl boot "$SIM_UDID"
    xcrun simctl bootstatus "$SIM_UDID" -b >/dev/null
    xcrun simctl ui "$SIM_UDID" appearance light
    xcrun simctl status_bar "$SIM_UDID" override --time 9:41 \
      --dataNetwork wifi --wifiMode active --wifiBars 3 \
      --cellularMode active --cellularBars 4 --operatorName "" \
      --batteryState charged --batteryLevel 100
    xcrun simctl install "$SIM_UDID" "$APP"
    place_demo_backup "$SIM_UDID" "$backup"

    shots="$WORK/maestro-$SIM_UDID"
    "$MAESTRO" --device "$SIM_UDID" test --test-output-dir "$shots" \
      "$ROOT/.maestro/ios/09_ios_store_screenshots.yaml"
    # Maestro nests them under a per-flow folder: <dir>/<flow name>/takeScreenshot/.
    while IFS= read -r f; do
      cp "$f" "$out/$name-$(basename "$f")"
      check_size "$out/$name-$(basename "$f")" "$size"
    done < <(find "$shots" -path '*/takeScreenshot/*.png' | sort)
    [ -n "$(ls "$out/$name-"*.png 2>/dev/null)" ] || { echo "ERROR: no screenshots for $name" >&2; exit 1; }

    xcrun simctl shutdown "$SIM_UDID" >/dev/null 2>&1 || true
    xcrun simctl delete "$SIM_UDID"
    SIM_UDID=""
  done
}

run_feature_graphic() {
  local out="$ROOT/fastlane/metadata/android/en-US/images/featureGraphic.png"
  swift "$ROOT/scripts/feature_graphic.swift" "$out"
  check_size "$out" "1024x500"
}

case "${1:-}" in
  ios) run_ios ;;
  feature-graphic) run_feature_graphic ;;
  *)
    echo "usage: $0 ios|feature-graphic" >&2
    exit 2
    ;;
esac
