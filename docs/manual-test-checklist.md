# MedTrack manual test checklist (on a real Android phone)

Install: `adb install -r app/build/outputs/apk/debug/app-debug.apk` (USB debugging on), or copy the APK to the phone and open it.

## Setup
- [ ] First launch (fresh install) shows "Welcome to Pillhaven", then one screen per permission with "Step N of M": a picture, what it's for and a lock-icon reassurance.
- [ ] "Allow" opens the right system dialog or page; coming back with it allowed moves to the next screen by itself. "Not now" always moves on. Back goes to the previous screen.
- [ ] Permissions already allowed (e.g. exact alarms on Android 13+) get no screen, and the step count leaves them out.
- [ ] "Meal reminders (optional)" offers Connect / Install / Update Health Connect, with "Skip".
- [ ] "All set" lists what was skipped and "Go to Today" lands on Today; reopening the app doesn't show the screens again. Settings still lists every permission.

## Medications
- [ ] Add "pantaprazole" (misspelled): a suggestion "pantoprazole" appears; picking it fills ingredients (40 MG …), formula C16H15F2N3O4S and a "Used for" note.
- [ ] Add "paracetamol": resolves to acetaminophen with formula C8H9NO2.
- [ ] Airplane mode, add "metformin": the medication saves; "Not looked up yet – tap to retry" shows; retry works once you're back online.
- [ ] Camera photo and gallery photo both appear (right way up) in the list, Today and the alarm.
- [ ] Edit a medication, replace its photo, then save. The old photo is gone and the new one shows.

## Indian brands, several schedules and photos
- [ ] Type "dolo" in "Name on the pack": Dolo 650 Tablet appears with "Paracetamol (650mg)". Picking it fills the generic name and ingredients, and then the formula and "Used for" load.
- [ ] A brand that isn't listed (e.g. Mecmaxol): type the composition ("Calcium carbonate 500mg + Calcitriol 0.25mcg"), tap Look up info, save. Next time, typing "mecm" suggests it under "From your medications".
- [ ] Add "after breakfast" and "after dinner" to one medication (Add another time or meal). Today shows both doses separately; taking breakfast doesn't hide dinner.
- [ ] Add three photos from the gallery. Detail shows them as a swipeable gallery; "Make main" changes the photo in lists and on the alarm.

## Drops: which side
- [ ] Add drops with Form → Drops: "Drops for" appears; Save without picking one shows "Choose what the drops are for".
- [ ] Eye drops, Daily 8:00 AM, no side picked: Save shows "Choose which eye". Pick Left, then "Add another time or meal" 9:00 PM Right: saves.
- [ ] Two blocks at the same time, Left and Right: Save shows "… is set for two sides – choose Both instead".
- [ ] Today (By time) shows an amber LEFT EYE / RIGHT EYE badge on each dose; By medication tiles show Left / Right; TalkBack reads "Left eye".
- [ ] The 9 PM alarm shows PATIENT'S RIGHT EYE and the Left/Right picture with Right filled ("As the patient sees it"), in the chosen alarm colours.
- [ ] Unlocked, the notification says "Right eye"; on the lock screen it still says only that a medicine is due.
- [ ] Medications list and the medicine's page read "Daily at 8:00 AM · Left eye; Daily at 9:00 PM · Right eye"; history capsules show Left / Right.
- [ ] Change the form to Tablet and save: new doses have no side; past history still shows Left / Right.
- [ ] Ear and nose drops ask "Which ear" / "Which nostril"; "Mouth or other" drops save with no side.
- [ ] Before/after surgery: two doses (steps), Left until the surgery date, Right from the next day; the alarm follows the date.

## Reminders
- [ ] Daily time 2 minutes from now: the alarm rings with sound on the lock screen, full screen, showing photo, dose and notes.
- [ ] Taken from the notification: the notification clears; Today shows Taken; History shows it.
- [ ] Snooze: it rings again about 10 minutes later.
- [ ] Ignore a dose for 2 hours: it shows as Missed.
- [ ] Two medications at the same minute: both listed; "Take all" marks both.
- [ ] Reboot the phone before a dose and unlock it: it still rings. (After a reboot, reminders resume only once the phone has been unlocked.)
- [ ] Battery saver on, screen off for 30+ minutes: it still rings on time.
- [ ] Change the time zone: today's times follow the new local time.
- [ ] Every-6-hours and weekdays schedules ring on the expected days and times.
- [ ] A course with an end date: no reminders after the end date.
- [ ] Pause a medication: no more reminders; history kept. Delete: it's gone, including its history.

## Meal reminders (Health Connect)
- [ ] Settings → Health Connect → Connect grants nutrition access.
- [ ] An after-breakfast medication with a 0 min delay: log a breakfast in a Health Connect app (e.g. Samsung Health, MyFitnessPal). The reminder arrives within about 15 minutes. The fallback time doesn't ring that day.
- [ ] No meal logged: the fallback time rings.
- [ ] Revoke Health Connect permission: Today shows a warning; the fallback still rings.

## Release build (before each Play upload)
- [ ] `./gradlew :app:installRelease` installs as `com.hemzzdevv.pillhaven` (a separate app from an old `com.medtrack` install), version 1.0.
- [ ] First launch shows Get set; the Battery step's button reads "Open battery settings" and opens Pillhaven's own battery page (the system battery list on Android 9–11); tapping the words Allow background usage, choosing Unrestricted and coming back moves on by itself. The switch alone leaves it "Needs attention".
- [ ] Allow exact alarms; a dose 2 minutes from now rings on time with the full-screen alarm on the lock screen.
- [ ] Deny exact alarms: Settings shows "Needs attention"; a dose still rings (possibly a few minutes late).
- [ ] Brand search: type "Dolo 650" and pick it; the composition fills in.
- [ ] Meal reminders: Settings → Health Connect → Connect works.
- [ ] Settings → About: Privacy policy opens the published page; Medicine brand list opens the Kaggle page; Version shows the current versionName.
