# Ostomate 2.0 — Bug Backlog

Bugs and UX issues found during physical device testing. Work through these before Phase 3.

---

## Setup / Onboarding

### [ ] BUG-01: 1-piece appliance users cannot complete setup
**Screen:** Setup/onboarding flow  
**Problem:** No question for appliance type. Users with a 1-piece system hit a 2-piece workflow (separate bag + flange entries) and cannot get through setup.  
**Fix:** Add a "1-piece or 2-piece appliance?" question early in setup. Store the preference. 1-piece users should only log one item per change with no separate flange entry. Preference must flow through throughout the app.

### [ ] BUG-02: "How many on hand?" field shows leading zero (e.g. "0200")
**Screen:** Setup — supply quantity entry  
**Problem:** The field initializes to `0`. When the user taps and types a number, the `0` stays, producing values like `0200`.  
**Fix:** Clear the field on focus (select-all on tap), or use `KeyboardType.Number` with an empty initial value and a `0` placeholder.

### [ ] BUG-03: "Next" button not reachable on iPhone
**Screen:** Setup flow  
**Problem:** On a physical iPhone, the keyboard covers the bottom of the screen and the Next button is hidden behind it. Users have no way to proceed.  
**Fix:** Wrap the setup screen content in a `ScrollView` (or `imePadding` + `verticalScroll`) so the Next button scrolls into view above the keyboard.

---

## Navigation

### [ ] BUG-04: Settings sub-menu stays open when tapping Settings tab again or switching tabs
**Screen:** Settings → Manage Supplies (and other sub-menus)  
**Problem 1:** While inside a sub-menu (e.g. Manage Supplies), tapping the Settings bottom-nav button again does nothing — it should return to the Settings root.  
**Problem 2:** Navigating away (e.g. tap Home) then tapping Settings returns to the sub-menu instead of the Settings root.  
**Fix:** On Settings tab re-tap, pop to root of the Settings nav stack. On tab switch-away and back, reset the Settings nav stack to root.

---

## QR Labels / Printing

### [ ] BUG-05: Print button visible and tappable when no printer is configured
**Screen:** QR Labels  
**Problem:** If the device has no printer set up, the Print button still appears active. Tapping it either fails silently or shows a confusing error. Poor UX.  
**Fix:** Check printer availability before showing the button (e.g. `UIPrintInteractionController.isPrintingAvailable` on iOS). If no printer is available, hide or disable the button, or show a tooltip explaining that no printer is set up.

### [ ] BUG-06: QR code Share button sends text instead of a QR code image
**Screen:** QR Labels — Share  
**Problem:** The share sheet sends raw text data rather than an image of the QR code. The recipient gets a string that does not function as a QR code.  
**Fix:** Render the QR code composable to a bitmap/image first, then share the image (PNG or PDF). Do not share the raw string.

### [ ] BUG-07: Print button does not work with HP printer app on iPhone
**Screen:** QR Labels — Print  
**Problem:** On an iPhone with the HP Smart app installed and a printer configured, tapping Print does nothing (or fails).  
**Fix:** Investigate whether the iOS print path is using `UIPrintInteractionController` correctly. Ensure the printable content is provided as a `UIPrintPageRenderer` or `UIPrintFormatter` compatible type. Test with HP Smart.
