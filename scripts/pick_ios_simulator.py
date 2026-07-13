#!/usr/bin/env python3
"""Print `<runtime-id> <device-type-id>` for the newest available iOS simulator.

Used by the `ios-e2e` CI job to `simctl create` a device without hard-coding an
Xcode-version-specific name. Two things this guards against:

* Device types and runtimes are not paired in `simctl list` output — picking an
  arbitrary iPhone (e.g. the last one listed, an iPhone 6s Plus) against the newest
  runtime fails with "Incompatible device". The runtime's own `supportedDeviceTypes`
  is the only list that is guaranteed compatible.
* Screen size changes tap targets and scrolling, so prefer the newest plain
  "iPhone <N>" — the flows are verified against iPhone 17.
"""

import json
import re
import subprocess
import sys


def main() -> int:
    runtimes = json.loads(
        subprocess.run(
            ["xcrun", "simctl", "list", "runtimes", "-j"],
            capture_output=True, text=True, check=True,
        ).stdout
    )["runtimes"]

    available = [r for r in runtimes if r.get("isAvailable") and r.get("platform") == "iOS"]
    if not available:
        print("no available iOS runtime", file=sys.stderr)
        return 1
    runtime = sorted(available, key=lambda r: [int(x) for x in r["version"].split(".")])[-1]

    iphones = [d for d in runtime.get("supportedDeviceTypes", []) if d.get("productFamily") == "iPhone"]
    if not iphones:
        print(f"no iPhone device type supports {runtime['identifier']}", file=sys.stderr)
        return 1

    def generation(device: dict) -> int:
        match = re.fullmatch(r"iPhone (\d+)", device["name"])
        return int(match.group(1)) if match else -1

    device = max(iphones, key=generation)
    print(f"{runtime['identifier']} {device['identifier']}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
