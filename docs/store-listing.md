# Ostomate — Store Listing

## Short Description (≤ 80 chars)

Track ostomy bag and flange usage. Know when to reorder.

## Full Description (≤ 4000 chars)

Ostomate helps you take control of your ostomy supply management.

**Log changes in seconds**
Tap once to record a bag or flange change. Or skip the app entirely — stick a QR code label
on your supply box and scan it with your camera. The change logs automatically.

**Know when to reorder**
Ostomate tracks your personal usage patterns and tells you how many days of supply you have
left. No more guessing, no more running out.

**Supply warning alerts**
Get a notification before you run low. Set your own warning threshold — Ostomate will remind
you when it's time to reorder.

**Your data stays on your phone**
There is no account, no cloud sync, and no server. Everything is stored locally on your device.
Your health data is private.

**Calendar view**
See your usage history at a glance on a monthly calendar. Each day shows how many changes you made.

**QR label printing**
Generate printable QR code labels for your supply boxes. Scan with any camera app to log a
change instantly — no app-opening required.

**Features:**
• One-tap bag and flange change logging
• Custom supply types with your own names and colors
• QR code scan-to-log (no app required after setup)
• Days-of-supply estimate based on your personal usage rate
• Low-supply push notifications
• Monthly calendar usage view
• Usage stats: weekly, monthly, yearly
• Full backup and restore — move to a new phone with everything intact
• Biometric-protected settings (Face ID / Touch ID / fingerprint)
• Developer mode for safe testing

---

## Screenshot Capture Checklist

Regenerate with `scripts/store-screenshots.sh` (see the header of that script). Every shot
uses fake demo data (`scripts/store_demo_backup.py`, loaded through Settings > Restore
backup), light mode, and a clean status bar (9:41, full battery, no notifications).

Requirements (checked 2026-11-01):
- **App Store** ([screenshot specifications](https://developer.apple.com/help/app-store-connect/reference/app-information/screenshot-specifications/)):
  "At least one screenshot for iPhone with Dynamic Island (medium display)" is required;
  1–10 per size, PNG/JPEG, no alpha. We also ship the 6.9" set, the largest accepted size.
  - 6.9" — iPhone 17 Pro Max simulator, **1320 × 2868**
  - 6.3" (Dynamic Island, medium) — iPhone 17 Pro simulator, **1206 × 2622**
  - The old 6.7" + 5.5" pair is no longer required; smaller sizes are scaled by Apple.
  - The app also targets iPad (`TARGETED_DEVICE_FAMILY = "1,2"`), which makes a 13" iPad
    set (2064 × 2752) required too — not captured yet.
- **Google Play** ([preview assets](https://support.google.com/googleplay/android-developer/answer/9866151)):
  at least 2 phone screenshots, JPEG or 24-bit PNG (no alpha), 320–3840 px per side, long
  side at most twice the short side. For promotion, at least 4 at 1080 px+, 9:16 portrait.
  A Pixel 6 (1080 × 2400, 20:9) is too tall for the 2:1 limit — capture on a **1080 × 1920**
  emulator profile (e.g. Pixel 2). Android capture is not scripted yet: no emulator system
  image is installed on the Mac.

Output (fastlane layout):
- iOS: `fastlane/screenshots/en-US/<device>-NN_name.png`
- Android: `fastlane/metadata/android/en-US/images/phoneScreenshots/`
- Feature graphic: `fastlane/metadata/android/en-US/images/featureGraphic.png`

### Screens to capture:
1. **Home** — inventory counts and days remaining, no low-stock warning
2. **History** — the bag change log, with one note
3. **Logged** — the "Bag logged · N left" confirmation with Undo
4. **Calendar** — last month, full of bag and flange changes
5. **Stats** — 30-day averages per supply
6. **Settings** — overview
7. **Manage Supplies** — on-hand counts and box sizes
8. **QR Labels** — both QR codes with supply titles

Ensure no personal data visible in screenshots — never use a real backup.

**Feature graphic (Google Play):** 1024 × 500 px, 24-bit PNG — the icon's glyph centered on
brand blue #2563EB. Drawn by `scripts/feature_graphic.swift` from the adaptive icon vector
(`scripts/store-screenshots.sh feature-graphic`).
**App icon source:** 1024 × 1024 px — no rounded corners (stores add them).
