# Play Store launch readiness: design

Date: 2026-10-19
Status: Awaiting approval

## 1. Why

Pillhaven is a free Android app for people in India managing their family's medicines. It has been built and tested
by the developer's relatives (installed directly over USB for two weeks). The developer now wants to publish it on
Google Play so that **strangers find it on the Play Store** and install it.

Strangers don't know the developer, so the app has to earn trust from the listing alone. It needs a real privacy
policy, honest Data Safety answers, and permissions that pass Google's review. It must also keep doing what the
relatives already rely on: alarms that ring on time.

**Success:** a release bundle that Play accepts into closed testing without policy rejections. It comes with
everything the developer must paste or answer in the Play Console, written out ready to use, and a step-by-step
guide from "account verified" to "live in production".

## 2. Goals, non-goals, anti-goals

Goals
- The app builds as a signed Android App Bundle (`.aab`) under the permanent app ID `com.hemzzdevv.pillhaven`,
  version 1.0 (versionCode 1).
- Permissions and declarations comply with current Google Play policy for a medication-reminder app.
- A privacy policy page, hosted publicly, linked from the app and ready for the Play Console.
- Credit to the brand dataset's creator in the app and README.
- Store listing text: app name, short description (≤80 chars), full description (≤4000 chars).
- Written answers for the Play Console forms: Data Safety, content rating questionnaire, target audience, Health
  Connect / health apps declaration, full-screen intent declaration, and exact-alarm justification if asked.
- A launch guide (`docs/play-store-launch.md`) covering: account verification, creating the app, uploading to closed
  testing, getting 12 testers for 14 days, production access application, and production release.

Non-goals
- Graphics (icon, screenshots, feature graphic): the developer has them already.
- Ads or any other monetisation. A plan exists for later; v1 has none.
- Uploading to Play or filling in the Console for the developer. The developer does that by following the guide.
- Translations; the listing is English.

Anti-goals (a failure even if it builds)
- Reminders stop ringing on time on real phones (Realme, Xiaomi, Oppo, Samsung) because a permission was weakened
  for policy reasons without the app still asking for what it needs.
- The release bundle ships without the brand database (`brands.db` is git-ignored and built locally), so brand
  search silently returns nothing for every Play user.
- The signing key is lost, or committed to the public repo.
- The privacy policy or Data Safety answers claim something the app doesn't do (or omit something it does), e.g.
  saying "no data leaves the phone" while ingredient lookups go to RxNorm/PubChem/MedlinePlus.

## 3. Decided with the developer

- **Audience:** strangers finding it on Play, mostly in India, managing medicines for family.
- **Account:** personal Play developer account, currently being verified by Google. The relatives' two-week test
  was a direct install and **does not count** toward Play's closed-testing requirement. The guide must cover running
  that closed test (the relatives can be the testers again).
- **App ID:** `com.hemzzdevv.pillhaven`. Permanent once uploaded.
- **Brand database stays in v1**, with a credit to its creator: the "A-Z Medicine Dataset of India" by Shudhanshu
  Singh on Kaggle (https://www.kaggle.com/datasets/shudhanshusingh/az-medicine-dataset-of-india). The dataset
  states no licence. The developer accepts the risk that the owner or Kaggle objects and brand search has to be
  replaced. Record this in the guide as a known risk, not a blocker.
- **Meal reminders (Health Connect) stay in v1**, with whatever extra Play declaration they need.
- **Relatives' existing installs:** no data migration. The guide tells testers to uninstall the old directly
  installed app (`com.medtrack`) and re-enter their medicines in the Play version, so doses don't ring twice.
- **Meal reminders ship in v1; launch waits for them.** If Google's Health Connect review is slow, the launch waits
  for it. No build variant without Health Connect. The guide includes the Health Connect declaration as a step and
  says what to do if Google asks for changes.
- **Android backup stays on** (`allowBackup="true"`): users get their medicines back on a new phone. The privacy
  policy and Data Safety answers must say that Android may back up the app's data (medicines, dose history, photos)
  to the user's own Google account, encrypted, under the user's control, and that the developer never sees it. The
  builder should check whether Google counts Android Auto Backup as "data collected/shared" for Data Safety (it
  generally doesn't) and cite the page. *Claude's call:* add backup rules only if needed to exclude caches or
  anything that must not be restored (e.g. scheduled-alarm state, the camera cache).
- **Testers:** the developer has 12+ relatives/friends with Android phones and Google accounts for the 14-day test.
  The guide only needs to explain how to invite them and keep them opted in.
- **Graphics are supplied by the developer**; only text is produced.
- **Support contact:** hemzz.devv@gmail.com (already in the app's Settings → Support and README); the developer's
  Instagram is https://www.instagram.com/hemzz.devv/.

## 4. Current state (from the repo)

- `app/build.gradle.kts`: `namespace` and `applicationId` are both `com.medtrack`; minSdk 28, targetSdk 35,
  versionCode 1 / versionName "1.0". The release build type is signed with the **debug** key.
- Manifest permissions: INTERNET, POST_NOTIFICATIONS, SCHEDULE_EXACT_ALARM (maxSdk 32), USE_EXACT_ALARM,
  USE_FULL_SCREEN_INTENT, RECEIVE_BOOT_COMPLETED, REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
  health.READ_NUTRITION, health.READ_HEALTH_DATA_IN_BACKGROUND.
- `health/HealthPrivacyActivity.kt` plus an activity-alias exist for Health Connect's privacy-policy requirement.
- `ui/settings/PermissionRequests.kt` owns how each permission is requested (shared by Settings and first-run
  setup); `ui/setup/SetupSteps.kt` owns the first-run steps and their copy.
- The repo is public on GitHub (https://github.com/Hemz2006/Pillhaven).
- Data: local Room database (`medtrack.db`), photos in app storage, no accounts, no analytics. ML Kit text
  recognition runs on-device (its telemetry was removed). Network calls: RxNorm, PubChem and MedlinePlus lookups of
  ingredient names (https only), and opening pharmacy sites in the browser.

## 5. Intended changes (direction; details left to the builder)

1. **App ID.** Change `applicationId` to `com.hemzzdevv.pillhaven`. *Claude's call:* keep the Kotlin `namespace`
   / source packages as `com.medtrack` (renaming them is churn with no user-visible effect). Check every place the
   package name is hard-coded (manifest authorities such as the FileProvider, Health Connect intents, the
   activity-alias, intent actions) and make sure they follow the new ID. The developer's own phone will need one
   uninstall/reinstall; existing data on it is not migrated (only test data).
2. **Release signing.** Create an upload keystore **outside the repo**, read its path and passwords from a
   git-ignored `keystore.properties` (or `~/.gradle/gradle.properties`). Point the release build at it and fail the
   release build clearly if the properties are missing, instead of falling back to the debug key. Play App Signing
   holds the real app-signing key. The guide tells the developer exactly which files to back up and where.
3. **Exact alarms.** Verify the current Play policy. Claude's understanding: `USE_EXACT_ALARM` is restricted to apps
   whose core function is an alarm clock or calendar, and medication reminders may not qualify. If so, remove it and
   use `SCHEDULE_EXACT_ALARM` on all API levels. The setup step and Settings already ask for it. Confirm the scheduler
   handles "not granted" gracefully (falls back to inexact and the UI says so). If the policy does clearly allow
   medication reminders, keep it and record the justification for the Console.
4. **Battery.** Verify the Play policy on `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`. *Claude's call:* if it carries
   rejection risk for this app, drop the permission and have the battery step and Settings row open the system
   battery-optimisation list (`ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`) with short instructions. If
   it is allowed for this use, keep it and write the justification.
5. **Full-screen intent.** Keep it. Write the Console declaration text (medication alarm the user explicitly set up).
   Confirm the app degrades to a high-priority notification when the permission is denied (Android 14+).
6. **Target SDK.** Verify Google's current target-API requirement for new apps (as of Oct 2026) and bump targetSdk if
   needed, fixing anything the bump breaks.
7. **Brand database in the release.** The release bundle must contain `brands.db`. Make a release build without it
   fail (or at least fail loudly) rather than ship an empty brand search. Check its size against Play's limits.
8. **Credits.** Add a "Credits" or "About" area in Settings naming the dataset and its creator with a link, alongside
   the existing font/library credits in the README. Update the README credit too.
9. **Privacy policy.** Write `docs/privacy-policy.md` (or an HTML page) and publish it via GitHub Pages from this
   repo. It must truthfully cover: data stored on the phone (and in Android's backup to the user's own Google
   account), photos, ingredient lookups sent to the three
   services (only the ingredient name, no personal data), Health Connect meal data (read only, used only for
   reminder timing, never leaves the phone), pharmacy links opening external sites, no accounts/analytics/ads,
   deleting data (uninstall / clear data), contact email. Link it from Settings (next to Support) and from
   `HealthPrivacyActivity`.
10. **Store text and Console answers.** Write `docs/play-store-listing.md` with the name, short and full description
    (the full one ends with the dataset credit and a "not medical advice" line), the Data Safety answers, content
    rating answers, target audience (adults, not designed for children), health apps / Health Connect declaration,
    full-screen intent declaration, and any permission justifications.
11. **Launch guide.** `docs/play-store-launch.md`: the developer's step-by-step from account verification to
    production, including the closed test (12+ testers, 14 consecutive days, verify current numbers), how to invite
    the relatives, building the `.aab`, backing up the keystore, uploading, which forms to fill using the listing doc,
    applying for production access, and the dataset-licence risk.
12. **README.** Update install instructions for the new ID and signing setup.

## 6. Constraints

- Kotlin, Jetpack Compose, Room; follow existing packages and the repo's test habits. Existing unit tests (216+)
  must still pass.
- No secrets in git: keystore and passwords stay out of the public repo.
- Policy facts above marked "verify" must be checked against Google's current pages before acting; cite the page in
  the listing/guide docs.
- Accessibility and the existing first-run flow must keep working (Not now always moves on; granted steps skipped).

## 7. Left to the builder

- Exact wording of the privacy policy, store descriptions and Console answers (plain, short, truthful).
- Where the Credits / Privacy links sit in Settings.
- Whether `brands.db` presence is enforced by a Gradle check or a build-time assertion.
- How the battery step is reworded if the permission is dropped.
- Verifying the app on the developer's phone (Realme RMX3750) after the ID change: setup flow, an exact alarm
  ringing, the full-screen alarm, brand search, meal reminder connection.
