# Pillhaven

*A safe place for all your medicines.*

A personal Android app for keeping track of your medicines and getting reminded to take them. It's built for Indian brands: type the name on the pack, or photograph the box, and the app fills in what's in it.

## What it does

- **Scan the box or strip.** On Add medication, tap **Scan medicine** and take (or choose) a photo of the pack. The app reads it on the phone, offline, and suggests the matching brands, including your own medicines, to tap the right one. The photo becomes the medicine's photo. It also uses the generic name printed on the pack, so brands in a stylised logo are still found.
- **Find medicines by the name on the pack.** Type "Dolo 650", "Pan 40" or "Budecort" and pick from about 250,000 Indian brands. The app fills in the composition (e.g. Paracetamol 650mg) and the form (tablet, inhaler, nebuliser…). The search works offline.
- **Look up each ingredient online.** The app shows each ingredient's chemical formula and a short note on what it's used for. It uses free official sources: [RxNorm](https://lhncbc.nlm.nih.gov/RxNav/), [PubChem](https://pubchem.ncbi.nlm.nih.gov/) and [MedlinePlus](https://medlineplus.gov/). This is general information, not medical advice.
- **Add brands that aren't listed.** Type the composition yourself, e.g. "Calcium carbonate 500mg + Calcitriol 0.25mcg". The app looks up each ingredient and suggests the brand the next time you type it.
- **Forms and doses:**
  - Forms: tablet, capsule, syrup, inhaler, Rotacap, nebuliser (Respules), drops, injection, cream.
  - One-tap doses that suit the form, such as "2 puffs" or "1 respule in nebuliser".
- **Which side the drops go in.** For drops, choose what they're for: eye, ear, nose, or mouth/other. Then pick **Left**, **Right** or **Both** for each schedule time, e.g. left eye in the morning and right eye at night, or left eye before surgery and right eye after. Left and right always mean the patient's side.
  - The medicine won't save until every time has a side, or if two times would ring together for different sides.
  - Every dose shows an amber badge such as **RIGHT EYE**, and the schedule text says the side for each time.
  - The alarm says **PATIENT'S RIGHT EYE** in large type, with a Left/Right picture.
  - The dose history records which side each dose went to. Editing the schedule later doesn't change past entries.
- **Photos and notes.** Add several photos of the strip or box, while editing or straight from a medicine's page (**Add photos**). The main one appears in lists and alarms. Add notes on how you take the medicine.
- **Flexible schedules.** A medicine can have several schedules at once:
  - times every day, with a "times a day" shortcut that spreads doses from 6 AM to 10 PM
  - chosen weekdays
  - every N hours, from 6 AM by default; each day's doses stop at midnight rather than running into the next day
  - after breakfast, lunch or dinner
  - a course with an end date
- **Doses that change over time.** Split a schedule into doses, e.g. 6 times a day for two weeks, then 4 times a day. Each dose has its own length ("for how many days"), amount and timings; the next one starts the day after.
- **Today, ticked off as you go.**
  - A ring around the date fills as you take the day's doses.
  - View **By time**, or **By medication** with each dose as a tile you tap to tick off (tap again to undo).
  - Step back to yesterday or pick any past date from the calendar to see what was taken, skipped or missed, or look ahead to see the doses planned for coming days.
  - Times are shown in 12-hour format.
- **Reminders that ring like an alarm.** A full-screen alarm with Taken, Snooze 10 min and Skip. On the lock screen, notifications say only that a medicine is due; names appear once the phone is unlocked.
- **Order online.** Each medicine on the Medications page has an **Order** button that opens Tata 1mg, PharmEasy or Netmeds on that medicine, searching by the name on the pack or the generic name. You finish the order on the pharmacy's site.
- **Restock reminder.** When a course is about to end (within 3 days of its last day), the app asks once whether you'd like to buy more, with the same pharmacy choices.
- **Appearance and accessibility.** In Settings, choose System, Light (green and white) or Dark (green-black), make text, buttons and icons smaller or larger, and choose the full-screen alarm's colour and text colour. A short opening animation plays in the matching light or dark style while the app loads underneath.
- **After-meal reminders (optional).** Connect Health Connect and log a meal in a fitness app. The reminder comes a set time after the meal. If no meal is logged, it rings at a fallback time.
- **First-run setup.** The first time the app opens, a short **Get set** walkthrough asks for each permission on its own screen and says what it's for: notifications, exact alarms, full-screen alarm, unrestricted battery, and meal reminders (optional).
  - **Not now** always moves on, and permissions already allowed are skipped.
  - It ends with a checklist of what you allowed and what you skipped. You can change any of them later in Settings.

## Install on your phone

You need Android 9 or newer.

1. Install [Android Studio](https://developer.android.com/studio). This gives you the Android SDK and Java.
2. Clone this repository:

   ```sh
   git clone https://github.com/Hemz2006/Pillhaven.git
   ```

3. Build the brand list (one-time, see below). If you skip this, the app still works, but typing a brand name won't suggest anything.
4. Turn on **USB debugging** on the phone, connect it, then run:

   ```sh
   ./gradlew :app:installRelease
   ```

   This is the optimised build: much faster than the debug build, and its data can't be read over USB. It's signed with the Play upload key, so it needs `keystore.properties` (see [the launch guide](docs/play-store-launch.md)); without it, use `./gradlew :app:installDebug` instead.

5. Open Pillhaven and follow the **Get set** screens. Allow notifications, exact alarms, full-screen alarms, and choose **Unrestricted** on the battery page it opens; anything you skip can be allowed later in **Settings**.
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

This writes `app/src/main/assets/brands.db` (about 14 MB, git-ignored). It adds about 4 MB to the APK.

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
| Reading packs | ML Kit text recognition, bundled so it works offline |
| Release build | R8 minified and resource-shrunk (about 24 MB), not debuggable; 64-bit ARM phones only (`arm64-v8a`), which keeps ML Kit's native code to one copy |

Run the tests (unit tests plus Robolectric):

```sh
./gradlew :app:testDebugUnitTest
```

On Windows, if Gradle can't find Java, point `JAVA_HOME` at the JDK that comes with Android Studio. For example:

```sh
JAVA_HOME="/c/Program Files/Android/openjdk/jdk-21.0.8" ./gradlew :app:testDebugUnitTest
```

## Privacy

Everything stays on the phone: medicines, doses, photos and history. The app goes online only to look up ingredients (RxNorm, PubChem, MedlinePlus, over https) and when you choose a pharmacy, which opens its website. Reading a pack happens on the phone; ML Kit's usage statistics are switched off, so nothing about your photos is sent anywhere. Note that Android's backup is on, so your medicines and history are included in your encrypted Google backup (photos are left out, to stay under Android's 25 MB limit). The full [privacy policy](https://hemz2006.github.io/Pillhaven/privacy-policy.html) is published from `docs/privacy-policy.md`.

The app ID is `com.hemzzdevv.pillhaven`; the Kotlin code keeps its original `com.medtrack` package. Publishing steps are in [the Play Store launch guide](docs/play-store-launch.md), and the listing text and Console answers in [docs/play-store-listing.md](docs/play-store-listing.md).

Other docs:

- [Design](docs/superpowers/specs/2026-10-13-medtrack-design.md)
- [Implementation plan](docs/superpowers/plans/2026-10-13-medtrack.md)
- [Checklist for testing on a real phone](docs/manual-test-checklist.md)

### Code layout

```
app/src/main/java/com/medtrack/
  data/       Room entities, DAOs, migrations, photo storage, medicine forms, pharmacies, restock
  lookup/     Indian brand search, matching text read from packs, composition parsing, online lookups
  schedule/   Schedule rules, dose steps and dose-time maths
  reminders/  Reminder engine, alarms, notifications, alarm screen
  health/     Health Connect meal checks
  ui/         Compose screens: Today, Medications, Detail, Edit, Settings; splash, crop, order menu
tools/        build_brand_index.py
```

## Support

Questions, problems or ideas? Get in touch:

- Email: [hemzz.devv@gmail.com](mailto:hemzz.devv@gmail.com)
- Instagram: [@hemzz.devv](https://www.instagram.com/hemzz.devv/)

In the app, both are under **Settings → Support**: tapping Email opens your mail app with the subject "Pillhaven support", and tapping Instagram opens the profile.

## Credits

- Brand list: [A-Z Medicine Dataset of India](https://www.kaggle.com/datasets/shudhanshusingh/az-medicine-dataset-of-india) by Shudhanshu Singh (Kaggle).
- Font: [Newsreader](https://github.com/productiontype/Newsreader) by Production Type, under the SIL Open Font License (`app/src/main/assets/licenses/Newsreader-OFL.txt`).
- The pharmacy logos belong to Tata 1mg, PharmEasy and Netmeds; they're shown only to label the links to their sites.
- The navigation island, calendar, view menu and alerts are Compose versions of [EasyUI](https://github.com/Surajmaurya1/easyui) components; the alert icons are from [Lucide](https://lucide.dev) (ISC licence).
