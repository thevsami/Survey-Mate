# Survey Mate

Android app for field survey staff. During a phone or WhatsApp call, a movable **Add survey** bubble appears. Save the visit, and the app reminds you 2 days before, 1 day before, and 4 hours before.

## What it does
- Floating add button on incoming/outgoing phone calls
- WhatsApp call detection via notification access
- Recent call log: tap a number to create a survey
- Manual add with client name, phone, date (today onward), time, location, notes
- Local storage and reminder notifications
- English / Sinhala

## First-run setup
Open **Setup** and turn on:
1. Phone and call log
2. Contacts (optional, for names)
3. Notifications
4. Display over other apps
5. Notification access (for WhatsApp)
6. Exact alarms
7. Unrestricted battery (important on OPPO / ColorOS)

## Open in Android Studio
Open this folder, let Gradle sync, then run on a device. Emulators often have no real call log or WhatsApp.

## Limits
Android does not give WhatsApp the live phone number. The bubble uses the contact name from the WhatsApp notification when the number is hidden. Phone calls use the call log / phone state number.
