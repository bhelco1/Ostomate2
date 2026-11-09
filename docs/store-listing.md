# Ostomate — Store Listing

> **This file is the single source of truth for the store text.** The fastlane files are
> copies of it, and fastlane's upload lanes push those copies to the stores:
>
> - Google Play: `fastlane/metadata/android/en-US/` — `title.txt`, `short_description.txt`,
>   `full_description.txt`, `changelogs/default.txt`
> - App Store: `fastlane/metadata/ios/en-US/` — `name.txt`, `subtitle.txt`, `keywords.txt`,
>   `description.txt`
>
> Change the text here first, then copy it into those files in the same PR, character for
> character. Never edit the fastlane files on their own: the two versions drifted once, and the
> older one claimed features the app does not have. Every claim must be true of the shipped
> build on that platform; check the code before adding one.

## Store fields

| Field | Store | Limit | Text |
|---|---|---|---|
| Title | Google Play | 30 | `Ostomate — Ostomy Tracker` |
| Short description | Google Play | 80 | `Log ostomy bag and flange changes and know when to reorder your supplies.` |
| Name | App Store | 30 | `Ostomate` |
| Subtitle | App Store | 30 | `Ostomy Supply Tracker` |
| Keywords | App Store | 100 | `ostomy,colostomy,ileostomy,urostomy,stoma,supply,tracker,inventory,bag,flange` |

Keywords: comma-separated, no spaces after commas, no words from the name.

## Full Description (≤ 4000 chars)

Plain text: Google Play does not render markdown, so headings are capitals and bullets are "•".
The block below is the Google Play text (`full_description.txt`). The App Store text
(`description.txt`) is the same with two changes:

1. Delete the bullet that starts "Home screen widget" (the iOS widget is not built yet).
2. The lock bullet reads: `• Optional lock: confirm with Face ID, Touch ID or your passcode before supply counts can be changed`

```text
Ostomate helps you keep track of your ostomy supplies, so you know when to reorder before you run out.

LOG A CHANGE IN ONE TAP
Tap once to record a bag or flange change. Logged one by mistake? Tap Undo. Or print a QR code label, stick it on your supply box, and scan it with your phone's camera: Ostomate opens and logs the change for you.

KNOW WHEN TO REORDER
Ostomate works out how often you change each supply from your recent changes and estimates how many days of supply you have left. Your on-hand count goes down each time you log a change.

REORDER REMINDERS
Set a warning for each supply, in days. When you get close, Ostomate sends you a notification and shows a warning on the home screen.

WHAT'S INCLUDED
• One-tap bag and flange change logging, with Undo
• Printable QR code labels: scan one to log a change
• Home screen widget showing days left, with a Log button for each supply
• Days-of-supply estimate based on your own usage
• Low-supply reminder notifications
• Custom supply types with your own names, colors, box sizes and warning thresholds
• Monthly calendar of your changes: tap a day to edit or delete an entry
• History with optional notes and tags (Routine, Leak, Skin irritation)
• Stats for the last 7 days, 30 days or year: changes per supply, average days between changes, and a chart
• Backup and restore: export one backup file and restore it on a new phone with your supplies, history and settings intact
• Optional lock: confirm with your fingerprint, face or screen lock before supply counts can be changed

YOUR DATA STAYS YOURS
No account, no ads, no analytics. Your supplies and change history are stored on your phone and are never sent to the developer. Crash reports are optional and stay off unless you turn them on. Privacy policy: https://bhelco1.github.io/Ostomate2/

FREE
No subscription, no ads, no in-app purchases.

NOT A MEDICAL DEVICE
Ostomate is a supply-tracking tool, not a medical device. It does not diagnose, treat, cure or prevent any medical condition, and it does not give medical advice. Always consult your doctor, stoma nurse or another qualified healthcare professional about your ostomy care.
```

The "Not a medical device" paragraph is required by Google Play's Health Content and Services
policy (see `docs/data-safety.md`, H-2). Keep it in both stores' text.

## Release notes (Google Play, `changelogs/default.txt`, ≤ 500 chars)

```text
First release of Ostomate. Log bag and flange changes with one tap or a QR label, see how many days of supplies you have left, get reorder reminders, and back up your data to move it to a new phone.
```

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
  - iPhone only (`TARGETED_DEVICE_FAMILY = 1`), so no iPad set is needed.
- **Google Play** ([preview assets](https://support.google.com/googleplay/android-developer/answer/9866151)):
  at least 2 phone screenshots, JPEG or 24-bit PNG (no alpha), 320–3840 px per side, long
  side at most twice the short side. For promotion, at least 4 at 1080 px+, 9:16 portrait.
  A Pixel 6 (1080 × 2400, 20:9) is too tall for the 2:1 limit — capture on a **1080 × 1920**
  emulator profile: AVD `store_pixel2_api35` (Pixel 2, API 35), captured with
  `scripts/store-screenshots.sh android` and status-bar demo mode.

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
