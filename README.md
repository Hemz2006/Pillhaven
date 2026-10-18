# Pillhaven

A personal Android app for keeping track of your medicines and getting reminded to take them. It's built for Indian brands: type the name on the pack and the app fills in what's in it.

## What it does

- **Find medicines by the name on the pack.** Type "Dolo 650", "Pan 40" or "Budecort" and pick from about 250,000 Indian brands. The app fills in the composition (e.g. Paracetamol 650mg) and the form (tablet, inhaler, nebuliser…). The search works offline.
- **Look up each ingredient online.** The app shows each ingredient's chemical formula and a short note on what it's used for. It uses free official sources: [RxNorm](https://lhncbc.nlm.nih.gov/RxNav/), [PubChem](https://pubchem.ncbi.nlm.nih.gov/) and [MedlinePlus](https://medlineplus.gov/). This is general information, not medical advice.
- **Add brands that aren't listed.** Type the composition yourself, e.g. "Calcium carbonate 500mg + Calcitriol 0.25mcg". The app looks up each ingredient and suggests the brand the next time you type it.
- **Forms and doses:**
  - Forms: tablet, capsule, syrup, inhaler, Rotacap, nebuliser (Respules), drops, injection, cream.
  - One-tap doses that suit the form, such as "2 puffs" or "1 respule in nebuliser".
- **Photos and notes.** Add several photos of the strip or box, while editing or straight from a medicine's page (**Add photos**). The main one appears in lists and alarms. Add notes on how you take the medicine.
- **Flexible schedules.** A medicine can have several schedules at once:
  - times every day, with a "times a day" shortcut that spreads doses from 8 AM to 10 PM
  - chosen weekdays
  - every N hours
  - after breakfast, lunch or dinner
  - a course with an end date
- **Doses that change over time.** Split a schedule into steps, e.g. 6 times a day for two weeks, then 4 times a day. Each step has its own length, dose and timings; the next step starts the day after.
- **Today, ticked off as you go.**
  - A ring around the date fills as you take the day's doses.
  - View **By time**, or **By medication** with each dose as a tile you tap to tick off (tap again to undo).
  - Step back to yesterday or pick any past date from the calendar to see what was taken, skipped or missed.
  - Times are shown in 12-hour format.
- **Reminders that ring like an alarm.** A full-screen alarm with Taken, Snooze 10 min and Skip. On the lock screen, notifications say only that a medicine is due; names appear once the phone is unlocked.
- **Appearance and accessibility.** In Settings, choose System, Light (green and white) or Dark, and make text, buttons and icons smaller or larger.
- **After-meal reminders (optional).** Connect Health Connect and log a meal in a fitness app. The reminder comes a set time after the meal. If no meal is logged, it rings at a fallback time.

## Install on your phone

You need Android 9 or newer.

1. Install [Android Studio](https://developer.android.com/studio). This gives you the Android SDK and Java.
2. Clone this repository.
3. Build the brand list (one-time, see below). If you skip this, the app still works, but typing a brand name won't suggest anything.
4. Turn on **USB debugging** on the phone, connect it, then run:

   ```sh
   ./gradlew :app:installRelease
   ```

   This is the optimised build: much faster than the debug build, and its data can't be read over USB. It's signed with your local debug key, so it updates a debug install in place.

5. Open Pillhaven and go to **Settings**. Allow notifications, alarms, full-screen alarms and unrestricted battery.
   - On Realme, Oppo, Xiaomi, Vivo and Samsung phones, also allow Pillhaven to **auto-start** and **run in the background** in the phone's battery settings.
   - Without these, reminders may not ring.

After the phone restarts, reminders resume once you unlock it.

## Building the brand list

The Indian brand list comes from the [A-Z Medicine Dataset of India](https://www.kaggle.com/datasets/shudhanshusingh/az-medicine-dataset-of-india) (about 250,000 medicines, prices and availability as of November 2022).

The dataset doesn't state a licence, so the processed file **is not included in this repository**. To build it on your own machine:

1. Download `A_Z_medicines_dataset_of_India.csv` from the Kaggle page.
2. Run:

   ```sh
   python tools/build_brand_index.py path/to/A_Z_medicines_dataset_of_India.csv
   ```

This writes `app/src/main/assets/brands.db` (about 14 MB, git-ignored). The app adds about 4 MB to the APK.

Limits of the list:

- It shows at most two ingredients per brand.
- Brands launched after 2022, or ones the pharmacy site didn't carry, are missing.

In both cases, add the composition yourself once and the app remembers it.

## Development

| | |
|---|---|
| Language and UI | Kotlin, Jetpack Compose (Material 3) |
| Storage | Room (SQLite); the schemas are in `app/schemas/` |
| Reminders | AlarmManager exact alarms, notifications, full-screen alarm activity |
| Meals | Health Connect, checked every 15 minutes by WorkManager |
| Android | minSdk 28, targetSdk 35 |
| Release build | R8 minified and resource-shrunk (about 8 MB), not debuggable |

Run the tests (unit tests plus Robolectric):

```sh
./gradlew :app:testDebugUnitTest
```

On Windows, if Gradle can't find Java, point `JAVA_HOME` at the JDK that comes with Android Studio. For example:

```sh
JAVA_HOME="/c/Program Files/Android/openjdk/jdk-21.0.8" ./gradlew :app:testDebugUnitTest
```

Other docs:

- [Design](docs/superpowers/specs/2026-10-13-medtrack-design.md)
- [Implementation plan](docs/superpowers/plans/2026-10-13-medtrack.md)
- [Checklist for testing on a real phone](docs/manual-test-checklist.md)

### Code layout

```
app/src/main/java/com/medtrack/
  data/       Room entities, DAOs, migrations, photo storage, medicine forms
  lookup/     Indian brand search, composition parsing, RxNorm/PubChem/MedlinePlus lookups
  schedule/   Schedule rules, dose steps and dose-time maths
  reminders/  Reminder engine, alarms, notifications, alarm screen
  health/     Health Connect meal checks
  ui/         Compose screens: Today, Medications, Detail, Edit, Settings
tools/        build_brand_index.py
```

## Credits

- Font: [Newsreader](https://github.com/productiontype/Newsreader) by Production Type, under the SIL Open Font License (`app/src/main/assets/licenses/Newsreader-OFL.txt`).
- The navigation island, calendar, view menu and alerts are Compose versions of [EasyUI](https://github.com/Surajmaurya1/easyui) components; the alert icons are from [Lucide](https://lucide.dev) (ISC licence).
