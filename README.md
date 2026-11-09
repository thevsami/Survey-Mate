# Survey Mate

Android app for field survey staff. During a phone or WhatsApp call, a movable **Add survey** bubble appears. Save the visit, and the app reminds you 2 days before, 1 day before, and 4 hours before.

## What it does
- Floating add button on incoming/outgoing phone calls
- WhatsApp call detection via notification access
- Recent call log: tap a number to create a survey
- WhatsApp call history with All, Incoming, Outgoing, and Missed filters
- Manual add with client name, phone, date (today onward), time, location, notes
- Local storage and reminder notifications
- English / Sinhala

## First-run setup
Open **Setup** and turn on:
1. Phone and call log
2. Contacts (optional, for names)
3. Notifications
4. Display over other apps
5. Notification access (optional; required for WhatsApp call history)
6. Exact alarms
7. Unrestricted battery (important on OPPO / ColorOS)

## Open in Android Studio
Open this folder, let Gradle sync, then run on a device. Emulators often have no real call log or WhatsApp.

## Limits
WhatsApp call history is captured from WhatsApp call notifications only while notification access is enabled. Survey Mate discloses this before opening Android's access settings; records stay on-device and are not uploaded. Android's system call log on the tested device does not expose the phone-account metadata needed to reliably identify WhatsApp calls, so it is not used to label phone calls as WhatsApp calls. Calls from before notification access was enabled cannot be recovered. WhatsApp may also hide a caller's phone number; the app can show a contact name when available, but cannot guarantee the number.
