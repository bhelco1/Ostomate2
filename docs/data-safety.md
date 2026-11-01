# Ostomate — Play Data Safety and App Store privacy answers (draft)

Draft answers for Google Play's **Data safety** form, Play's **Health apps declaration**, and
App Store Connect's **App Privacy** section. Copy them in question by question.

Drafted 2026-11-01 against main at 64a4a54. Sources were read on that date. Re-check before
submitting: the forms change, and so does the code.

Updated 2026-11-01 (branch `fix/crash-report-privacy`): crash reports no longer carry any
install or device ID on either platform; the `beforeSend` scrubbers drop the Sentry user (and
with it the `{{auto}}` IP request) plus the ID fields. J3, J4, P-7 and the "what the code
sends" table are updated to match. The answers still assume action 1 (no stored IPs).

Every answer has three parts: the answer, why (one line), and the source it rests on.
"Unverified" means no official source confirmed it.

---

## Read first: what Bobby must decide or do

### Before submitting (actions)

1. **Sentry: turn on "Prevent Storing of IP Addresses".** In Sentry: Project settings →
   Security & Privacy. The app no longer asks Sentry to record the IP (the user, with its
   `{{auto}}` IP, is stripped), but Sentry's server still sees the connecting IP and, for
   Apple events, fills it in by default (see "What the code actually sends" below). The
   answers here and the privacy policy ("configured not to store it") assume this switch is
   on. If it stays off, add *Location → Approximate location* on Play (row P-7b) and expect
   Apple to treat the data as linked to the user.
2. **Check your Sentry plan's retention.** The Play deletion answer (P-3) relies on Sentry
   deleting error events within 90 days. The free Developer plan keeps them 30 days, Team
   90 days.
3. ~~**Add Play's required health disclaimer to the Play description.**~~ **Done
   2026-11-01** in `docs/store-listing.md` ("Not a medical device"). See H-2. The fastlane
   description files (`fastlane/metadata/*/en-US/*description.txt`) are a separate, older text
   and do not have it yet.

### Judgment calls (recommendation first)

| # | Question | Recommended | Alternative |
|---|---|---|---|
| J1 | Does the Android Drive backup count as Play "collection"? | **No.** Play's FAQ exempts a user's own upload to their own Drive. | Declare *Health info*: collected, optional, App functionality. Safer from review, but the listing then says the app collects health info. |
| J2 | Health apps declaration category | **Medical → Diseases and Conditions Management.** | "No health features". Smaller footprint, but it is an ostomy app and Google reviews the declaration. |
| J3 | Sentry's random install ID on Play | **Resolved: do not declare** *Device or other IDs*. The install ID is no longer sent: `beforeSend` drops `user` and `device.id` (fixed 2026-11-01). Before the fix the answer was to declare it. | — |
| J4 | Sentry's random install ID on Apple | **Resolved: do not declare** a Device ID. `beforeSend` drops `user` and `app.device_app_hash` (fixed 2026-11-01), so no install or device identifier is sent. | — |
| J5 | Feedback email (Settings → Support) | **Not collected** on either store. The user's own mail app sends it, each time on purpose. | Play: *Personal info → Email address* + *Crash logs*, optional, Developer communications. |

---

## What the code actually sends (checked 2026-11-01)

Only these leave the device. Everything else stays local.

| Path | Platform | Default | Where it goes | What goes |
|---|---|---|---|---|
| Crash reporting (Sentry) | Android + iOS | **Off** (`crashReportingEnabled = false`) | Developer's Sentry project, HTTPS | Stack trace; device context (maker, model, OS, memory, storage, battery, screen, locale, timezone…); app version. **No user and no install/device ID** (stripped in `beforeSend` since 2026-11-01; before that a random install ID went as `user.id`, plus `device.id` on Android and `device_app_hash` on iOS). Sentry sees the connecting **IP address** in transit and does not store it once action 1 is done |
| Daily Drive backup (FEAT-03) | Android only | **Off**, switch hidden in dev mode | User's own Google Drive, HTTPS (`googleapis.com/drive/v3`) | The backup JSON (supplies, full change history, settings). `drive.file` scope only |
| Google authorization | Android only, only when Drive backup is on | Off | Google Play services | Whatever Play services sends to authorize `drive.file`. The app asks for no name or email |
| Feedback email | Android + iOS | User taps it | Opens the user's mail app addressed to ostomate26@gmail.com | App version, device model, OS version; on Android also the last crash recorded on the device. Sent only if the user presses Send |
| Share-sheet backup | Android + iOS | User taps it | Wherever the user picks | The backup JSON |

Evidence:
- Android Sentry: `shared/src/androidMain/.../CrashReporter.android.kt` (crash only: no sessions,
  traces or breadcrumbs). Sentry Android 7.19.0's `DefaultAndroidEventProcessor.mergeUser` sets
  `user.id = Installation.id(context)` (a random UUID) and `ip_address = "{{auto}}"`
  unconditionally, and `DeviceInfoUtil` sets `device.id` to the same install ID (read from the
  bytecode). **Fixed 2026-11-01:** `scrubCrashEvent` (the `beforeSend`) sets `user = null` and
  clears `device.id`; `SentryClient.captureEvent` runs the event processors before
  `beforeSend`, so they cannot re-add them. Test: `CrashReporterScrubTest`.
- iOS Sentry: `iosApp/iosApp/iOSApp.swift`. sentry-cocoa 9.17.1's `setUserIdIfNoUserSet` sets
  `user.userId` to the install ID, and the crash integration puts `device_app_hash` (a SHA-1
  of `identifierForVendor`, model and bundle ID) in the app context. **Fixed 2026-11-01:** the
  `beforeSend` sets `user = nil` and removes `device_app_hash`; `prepareEvent` calls it after
  `setUserIdIfNoUserSet`. Sentry's docs: "Sentry sets the IP address to `{{auto}}` out of the
  box for Apple" and "setting `sendDefaultPii` to `false` won't stop Sentry from collecting
  users' IP addresses" ([Sentry: Users, Apple](https://docs.sentry.io/platforms/apple/guides/ios/enriching-events/identify-user/)),
  which is why action 1 still matters.
- Privacy text: `docs/privacy.html` and the in-app policy said "only crash stack traces are
  sent"; the feedback email said crash reporting "is planned for a future release". **Fixed
  2026-11-01:** both policies now list what a report contains and describe the feedback email.
- Drive: `DriveBackup.android.kt`, `DriveClient.kt`, `UrlConnectionTransport.kt`.
- No analytics or ads SDK. Dependencies with network reach: `sentry-android`, `sentry-cocoa`,
  `play-services-auth`. `INTERNET` is the only network permission. No camera, location or
  contacts permission.

---

## Google Play — Data safety form

Source for every Play passage below, unless noted:
[Provide information for Google Play's Data safety section](https://support.google.com/googleplay/android-developer/answer/10787469)
(Play Console Help).

### Section 1: Data collection and security

**P-1. Does your app collect or share any of the required user data types?** → **Yes.**
- Why: opt-in Sentry sends crash data off the device.
- Source: "'Collect' means transmitting data from your app off a user's device."
- And: developers must cover "data collected and handled through any third-party libraries
  or SDKs used in their apps".

**P-2. Is all of the user data collected by your app encrypted in transit?** → **Yes.**
- Why: Sentry and Drive both use HTTPS only. Android blocks cleartext by default (targetSdk 36);
  iOS enforces ATS and the app rejects a non-`https://` DSN.
- Source: the question asks whether data is sent "using encryption in transit to protect the
  flow of user data from the end user's device to the server."

**P-3. Do you provide a way for users to request that their data is deleted?** → **Yes**,
by automatic deletion. *Depends on action 2 (Sentry plan retention ≤ 90 days).*
- Why: there is no account and nothing to look a user up by. Sentry deletes events after
  30 days (Developer plan) or 90 days (Team).
- Source: "You may select the deletion request mechanism badge in Data safety form if you:
  provide users with a mechanism to request data deletion; or automatically initiate deletion
  or anonymization of collected data within 90 days of collection."
- Sentry retention: [Data Retention Periods](https://docs.sentry.io/security-legal-pii/security/data-retention-periods/).
- If the plan keeps data longer than 90 days, answer **No**.

**P-4. Account creation (if the form asks).** → **My app does not allow users to create an account.**
- Why: no app account exists. Google authorization for Drive gives the app a Drive token, not
  an identity the developer provides.
- Source: [Understanding Google Play's app account deletion requirements](https://support.google.com/googleplay/android-developer/answer/13327111)
  defines an app account as "A unique user identity that developers provide as a user-facing
  feature to serve the user across applications and/or devices".
- Unverified: that this question still appears on the form, and its exact wording.
- Consequence: the in-app account-deletion requirement does not apply ("If your app enables
  account creation, you must: provide users with an in-app path to delete their app accounts").

**Independent security review** → leave blank. Optional; nothing to declare.

### Section 2: Data types

Tick only these:

| Row | Category → data type | Tick? |
|---|---|---|
| P-5 | App info and performance → **Crash logs** | Yes |
| P-6 | App info and performance → **Diagnostics** | Yes |
| P-7 | Device or other IDs → **Device or other IDs** | **No** (J3: no ID sent since 2026-11-01) |
| P-7b | Location → Approximate location | Only if Sentry still stores IPs (action 1) |
| — | Health and fitness → Health info | **No** (J1) |
| — | Personal info → Email address | **No** (J5) |
| — | Everything else | No |

Definitions relied on:
- Crash logs: "Crash log data from your app. For example, the number of times your app has
  crashed, stack traces, or other information directly related to a crash."
- Diagnostics: "Information about the performance of your app. For example battery life,
  loading time, latency, framerate, or any technical diagnostics." (Sentry's device context:
  memory, model, OS.)
- Device or other IDs: "Identifiers that relate to an individual device, browser or app. For
  example, an IMEI number, MAC address, Widevine Device ID, Firebase installation ID, or
  advertising identifier."
- Approximate location: "Approximate location that is inferred, such as via IP address or
  Access Point Name, must be disclosed here."
- Health info: "Information about a user's health, such as medical records or symptoms."

### Section 3: Data usage and handling (same answers for P-5, P-6, and P-7b if ticked)

| Question | Answer | Why |
|---|---|---|
| Collected, shared, or both? | **Collected** only | Sentry processes it for the developer, which is not "sharing". |
| Processed ephemerally? | **No** | Sentry stores events for weeks. |
| Required or optional? | **Optional** | Crash reporting is off until the user turns it on. |
| Why collected? | **Analytics** | Play's Analytics covers monitoring app health. |

Sources:
- Not shared: sharing excludes "Transferring user data to a 'service provider' that processes
  it on behalf of the developer."
- Ephemeral: "accessing and using it while the data is only stored in memory and retained for
  no longer than necessary to service the specific request in real-time."
- Optional: "'Optional' includes the ability to opt into or opt out of data collection."
- Analytics: "Used to collect data about how users use the app or how it performs. For
  example, to see how many users are using a particular feature, to monitor app health."

### J1 in detail: the Drive backup

**Recommended: not collected, not shared.**
- Play's FAQ asks exactly this: "My app enables users to upload their data directly to Google
  Drive or Dropbox for backup or storage. My app does not access any of this data. Should that
  still be disclosed as 'collection'?"
- Answer: "It depends on the particular implementation. If the user chooses to upload their
  data directly to their own external drive or cloud storage account (such as Google Drive,
  Dropbox, or similar services) and this upload is governed by the external drive or cloud
  storage provider's terms of service and privacy policy, and your app never collects or
  accesses the data in question, then your app does not need to declare the collection of
  this data."
- How Ostomate fits: the user turns it on (off by default). The file goes straight from the
  phone to the user's own Drive under Google's terms. The developer never receives it.
- Where it is weaker: the app itself sends the file, and it lists and deletes its own old
  backups in Drive (`drive.file`). A strict reader could say the app "accesses" the data.

**Alternative: declare it.** *Health and fitness → Health info*: Collected, not shared, not
ephemeral, Optional, purpose App functionality. A change log of ostomy supplies reveals a
medical condition, so it is health info. Cost: the listing shows "health info" collected.

Either way, the privacy policy already describes the backup in full. Keep it that way.

---

## Google Play — Health apps declaration

Source: [Provide information for the Health apps declaration form](https://support.google.com/googleplay/android-developer/answer/14738291)
and the [Health Content and Services policy](https://support.google.com/googleplay/android-developer/answer/16679511).

**H-1. Health features** → tick **Medical → Diseases and Conditions Management** only (J2).
Do **not** tick *Medical Device Apps*.
- Why: the app helps manage supplies for a chronic condition (an ostomy). It does not
  diagnose or treat, and it is not regulated as a medical device.
- Source: Diseases and Conditions Management is "Apps focusing on managing acute or chronic
  physical health conditions and diseases (like diabetes, asthma) and other health areas…".
  Medical Device Apps are "Apps that are regulated as medical devices."
- Everyone must answer: "All developers that have an app published on Google Play must
  complete the Health apps declaration, including apps on closed testing, open testing, or
  production tracks." Whether Internal Testing alone triggers it is unverified; answer it
  before the first upload anyway.
- The declaration "will be reviewed by Google as part of the app review process."

**H-2. Required disclaimer (store listing, not the form).** The policy says:
"Other health and medical apps must include a clear disclaimer in their app description
indicating that the app is 'not a medical device and does not diagnose, treat, cure, or
prevent any medical condition.' Apps must also remind users to consult a healthcare
professional for medical advice, diagnosis, or treatment."
- Done 2026-11-01: `docs/store-listing.md` now has both lines ("Not a medical device").
- Unverified: whether the "remind users" line must also appear inside the app. The quoted
  sentence sits under the app-description requirement; a line in the description is the
  minimum.

**H-3. Privacy policy in the app.** The same policy says: "Your app must post a privacy policy
link in the designated field within Play Console, and a privacy policy link or text within the
app itself." Already met: Settings → Privacy policy shows the text in-app.

---

## Apple — App Privacy (App Store Connect)

Source for every Apple passage below:
[App privacy details on the App Store](https://developer.apple.com/app-store/app-privacy-details/).

The iOS build has Sentry, the feedback email, and the share-sheet backup. It has **no** Drive
backup, so Health data is not collected on iOS.

**A-1. Do you or your third-party partners collect data from this app?** → **Yes.**
- Why: opt-in Sentry crash reports.
- Source: "'Collect' refers to transmitting data off the device in a way that allows you
  and/or your third-party partners to access it for a period longer than what is necessary to
  service the transmitted request in real time."
- Optional-disclosure does not apply. All four criteria must hold, and the last fails: "The
  data is provided by the user in your app's interface, it is clear to the user what data is
  collected, the user's name or account name is prominently displayed in the submission
  form…, and the user affirmatively chooses to provide the data for collection each time."
  Crash reports go automatically once switched on.

**A-2. Data types** → under **Diagnostics**, tick:

| Type | Tick? | Why |
|---|---|---|
| Crash Data | Yes | Stack traces. "Such as crash logs". |
| Performance Data | Yes | sentry-cocoa reports app hangs by default. "Such as launch time, hang rate, or energy use". |
| Other Diagnostic Data | Yes | Device context sent with each crash. "Any other data collected for the purposes of measuring technical diagnostics related to the app". |
| Identifiers → Device ID | No (J4) | |
| Location → Coarse Location | Only if Sentry still stores IPs (action 1) | Apple: for IP address, "Declare the relevant data types based on how you use IP address, such as precise location, coarse location, device ID, or diagnostics." |
| Health, Contact Info, everything else | No | |

The three Diagnostics types match the privacy manifest that ships inside sentry-cocoa 9.17.1
(`PrivacyInfo.xcprivacy`: CrashData, PerformanceData, OtherDiagnosticData, each not linked,
not tracking). Apple builds its privacy report from these manifests, so matching them avoids
a mismatch.

**A-3. For each ticked type:**

| Question | Answer | Why / source |
|---|---|---|
| Used to track you? | **No** | Tracking means linking with "Third-Party Data for targeted advertising or advertising measurement purposes, or sharing … with a data broker." None of that happens. |
| Linked to the user's identity? | **No** | No name, email, account or ID is attached: the `beforeSend` strips Sentry's install ID and device hash (since 2026-11-01). Apple: data is linked "unless specific privacy protections are put in place before collection to de-identify or anonymize it, such as: Stripping data of any direct identifiers, such as user ID or name". Note Apple also says "'Personal Information' and 'Personal Data', as defined under relevant privacy laws, are considered linked" — so action 1 (no stored IPs) matters here. |
| Purpose | **App Functionality** | Apple's App Functionality includes "minimize app crashes". Analytics is about "evaluat[ing] user behavior", which Sentry here does not do. |

**A-4. Privacy policy URL** → https://bhelco1.github.io/Ostomate2/

**If the Drive backup ever ships on iOS**, revisit A-1/A-2 with J1's reasoning. Apple has no
FAQ like Play's for this case (unverified: none found on the App Privacy page).

---

## Play services auth (play-services-auth 22.0.0)

Unverified. Google publishes Data safety notes for some Play services SDKs, but the general page
says only that the base, basement, OSS licenses and tasks SDKs "don't collect any end-user
data", and points to each other SDK's own docs. No disclosure page for play-services-auth was
found ([Google Play services data disclosure](https://developers.google.com/android/guides/play-data-disclosure)).
It runs only when the user turns on the Drive backup, and the app requests only `drive.file`
(no profile or email scope). No extra row is recommended.

---

## Sources

- Play: [Data safety section](https://support.google.com/googleplay/android-developer/answer/10787469) (last changelog entry seen: December 5, 2023)
- Play: [Health apps declaration form](https://support.google.com/googleplay/android-developer/answer/14738291)
- Play: [Health Content and Services policy](https://support.google.com/googleplay/android-developer/answer/16679511)
- Play: [App account deletion requirements](https://support.google.com/googleplay/android-developer/answer/13327111)
- Google: [Play services data disclosure](https://developers.google.com/android/guides/play-data-disclosure)
- Apple: [App privacy details on the App Store](https://developer.apple.com/app-store/app-privacy-details/)
- Sentry: [Users (Apple): install ID and IP](https://docs.sentry.io/platforms/apple/guides/ios/enriching-events/identify-user/)
- Sentry: [Data collected (Android)](https://docs.sentry.io/platforms/android/data-management/data-collected/)
- Sentry: [Data privacy for mobile](https://docs.sentry.io/security-legal-pii/security/mobile-privacy/)
- Sentry: [Data retention periods](https://docs.sentry.io/security-legal-pii/security/data-retention-periods/)
