#!/usr/bin/env bash
# Pull the latest QA dashboard onto the Raspberry Pi and serve it as static files.
#
# CI publishes the dashboard (index.html, data.json, history.json) to the
# `test-dashboard` branch on every merge to main. This script pulls that branch
# into a local directory; point any static web server at it, or use --serve for
# a quick python http.server.
#
# One-time setup on the Pi:
#   mkdir -p ~/ostomate-dashboard && crontab -e
#   */15 * * * * /home/pi/update-dashboard.sh >> /home/pi/dashboard-update.log 2>&1
#
# Serving options:
#   - nginx/lighttpd: set the web root to $TARGET_DIR
#   - quick and dirty: ./update-dashboard.sh --serve   (listens on :8080)

set -euo pipefail

REPO_URL="${OSTOMATE_REPO_URL:-https://github.com/bhelco1/Ostomate2.git}"
BRANCH="test-dashboard"
TARGET_DIR="${OSTOMATE_DASHBOARD_DIR:-$HOME/ostomate-dashboard}"
PORT="${OSTOMATE_DASHBOARD_PORT:-8080}"

if [ -d "$TARGET_DIR/.git" ]; then
    # CI force-pushes a single fresh commit each run, so reset rather than pull.
    git -C "$TARGET_DIR" fetch --depth 1 origin "$BRANCH"
    git -C "$TARGET_DIR" reset --hard "origin/$BRANCH"
else
    git clone --depth 1 --branch "$BRANCH" "$REPO_URL" "$TARGET_DIR"
fi

echo "$(date -Is) dashboard updated: $(git -C "$TARGET_DIR" log -1 --format=%s)"

if [ "${1:-}" = "--serve" ]; then
    echo "Serving $TARGET_DIR on http://0.0.0.0:$PORT"
    exec python3 -m http.server "$PORT" --directory "$TARGET_DIR"
fi
