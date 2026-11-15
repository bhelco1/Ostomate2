#!/usr/bin/env python3
"""Writes the fake demo backup that the store-screenshot flows restore.

The data is invented (no real user's history): ten weeks of bag and flange changes ending
yesterday, so the calendar, stats, history and days-remaining predictions all have something
to show. Two supplies only, so both home cards fit above the navigation bar. Timestamps are
relative to today because the simulator clock is the host clock — a fixed-date file would age
into "no recent changes". A fixed random seed keeps the times identical for a given day.

The file is an ordinary app backup (BackupRepository format 1, schema 3), loaded through
Settings > Restore backup, so no app code knows about it. If the schema version changes,
the restore fails with "different database version" — bump SCHEMA_VERSION here to match.

Usage: python3 scripts/store_demo_backup.py OUT.json
"""

import json
import random
import sys
from datetime import date, datetime, time, timedelta

FORMAT_VERSION = 1
SCHEMA_VERSION = 3  # OstomateDatabase.SCHEMA_VERSION
WEEKS = 10

# Inventory is well above each 14-day warning threshold, so the home shot shows no banner.
SUPPLIES = [
    # id, name, kind, boxSize, onHand, colorIndex, mean days between changes
    (1, "Bag", "BAG", 30, 42, None, 2.0),
    (2, "Flange", "FLANGE", 5, 18, None, 4.0),
]

NOTES = {
    # (supply id, index from the most recent change) -> note
    (1, 3): "Changed early after swimming",
    (2, 5): "New box opened",
}


def millis(dt: datetime) -> int:
    return int(dt.timestamp() * 1000)


def build(today: date) -> dict:
    rng = random.Random(20261101)
    start = today - timedelta(weeks=WEEKS)
    events = []
    for supply_id, _name, _kind, _box, _on_hand, _color, mean_days in SUPPLIES:
        stamps = []
        day = today - timedelta(days=1)
        while day > start:
            hour = rng.choice([7, 7, 8, 8, 9, 20, 21])
            stamps.append(datetime.combine(day, time(hour, rng.randrange(0, 60))))
            jitter = rng.choice([-1, 0, 0, 0, 1]) if mean_days >= 3 else rng.choice([0, 0, 1])
            day -= timedelta(days=max(1, round(mean_days) + jitter))
        for i, ts in enumerate(stamps):
            events.append((supply_id, ts, NOTES.get((supply_id, i))))

    events.sort(key=lambda e: e[1])
    now = millis(datetime.combine(today, time(9, 0)))
    return {
        "formatVersion": FORMAT_VERSION,
        "schemaVersion": SCHEMA_VERSION,
        "exportedAt": now,
        "settings": {
            "onboardingDone": True,
            "lockSettings": False,
            "localeOverride": None,
            "crashReportingEnabled": False,
            "applianceType": "TWO_PIECE",
            "devMode": False,
        },
        "supplyTypes": [
            {
                "id": sid,
                "name": name,
                "kind": kind,
                "boxSize": box,
                "warnThresholdDays": 14,
                "onHand": on_hand,
                "sortOrder": sid - 1,
                "archived": False,
                "colorIndex": color,
            }
            for sid, name, kind, box, on_hand, color, _mean in SUPPLIES
        ],
        "events": [
            {
                "id": i + 1,
                "supplyTypeId": sid,
                "timestampMillis": millis(ts),
                "note": note,
                "tags": None,
                "createdAtMillis": millis(ts),
                "editedAtMillis": None,
            }
            for i, (sid, ts, note) in enumerate(events)
        ],
    }


def main() -> None:
    if len(sys.argv) != 2:
        sys.exit("usage: store_demo_backup.py OUT.json")
    with open(sys.argv[1], "w") as f:
        json.dump(build(date.today()), f, indent=2)
        f.write("\n")


if __name__ == "__main__":
    main()
