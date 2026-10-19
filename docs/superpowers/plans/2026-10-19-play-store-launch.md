# Play Store Launch Readiness Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Pillhaven build as a signed, policy-compliant Play release (`com.hemzzdevv.pillhaven`, 1.0) and write the
privacy policy, store text, Console answers and launch guide.

**Architecture:** Build-level changes live in `app/build.gradle.kts` (ID, target SDK, signing, brand-db guard). Permission
changes stay in their owners: the manifest, `ui/settings/PermissionRequests.kt` (how each permission is asked) and
`ui/setup/SetupSteps.kt` (first-run copy). Backup rules are XML resources. Public text lives in `docs/`, and GitHub
Pages serves the privacy policy from `docs/`.

**Tech Stack:** Kotlin, Jetpack Compose, Room, Gradle Kotlin DSL, Robolectric unit tests, GitHub Pages (Jekyll).

**Spec:** `docs/superpowers/specs/2026-10-19-play-store-launch-design.md`

## Global Constraints

- App ID `com.hemzzdevv.pillhaven`; Kotlin `namespace` and source packages stay `com.medtrack`.
- versionCode 1, versionName "1.0".
- targetSdk 36 (Play: new apps must target Android 16 / API 36 from 31 Aug 2026; source
  support.google.com/googleplay/android-developer/answer/11926878). compileSdk is already 36.
- No keystore, password or `keystore.properties` in git (the repo is public).
- `USE_EXACT_ALARM` removed: Play allows it only for alarm-clock and calendar apps. Use `SCHEDULE_EXACT_ALARM` on all API levels.
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` removed: medication reminders aren't an acceptable exemption use case
  (developer.android.com/training/monitoring-device-state/doze-standby#exemption-cases). Open the system list instead.
- Full-screen intent kept; Play Console declaration required (answer/13392821).
- Personal account: closed test, 12 or more testers opted in continuously for 14 days, then apply for production (answer/14151465).
- Privacy policy URL: `https://hemz2006.github.io/Pillhaven/privacy-policy.html`.
- Dataset credit: "A-Z Medicine Dataset of India" by Shudhanshu Singh,
  `https://www.kaggle.com/datasets/shudhanshusingh/az-medicine-dataset-of-india`.
- Support email `hemzz.devv@gmail.com`.
- All existing unit tests keep passing: `JAVA_HOME="/c/Program Files/Android/openjdk/jdk-21.0.8" ./gradlew :app:testDebugUnitTest`.

## Review Focus

1. **The phone was restored from a backup:** a new phone restoring Pillhaven must still show Get set, because permissions
   aren't restored. The `medtrack` shared-prefs file, which holds `setupDone`, `lastScanAt` and `pendingCapture`, is
   excluded from backup (Task 4).
2. **Exact alarms denied (default on Android 14+ for `SCHEDULE_EXACT_ALARM`):** the app must still schedule an inexact alarm and show
   "Needs attention". `AlarmScheduler.set` already falls back; Task 3 re-runs the reminder tests and checks this on the phone.
3. **Release built on a machine without `keystore.properties` or without `brands.db`:** the build must fail with a message
   saying what's missing, never produce a debug-signed bundle or one with an empty brand search (Task 1).
4. **Battery step on phones whose settings app has no optimisation list:** `open()` already falls back to app details.
   The step copy must tell the user what to tap (Task 3).
5. **Auto Backup's 25 MB limit:** many photos could push the backup over it, and Android then skips the backup. Photos are
   ≤1200 px JPEGs (~150–250 KB each). Record this in the privacy policy as "may not include every photo" rather than
   promising photos are always backed up (Task 6).

---

### Task 1: Release identity, signing and brand-db guard

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `.gitignore` (add `keystore.properties`, `*.jks`)
- Create (NOT committed): `keystore.properties` at the repo root; keystore at `C:\Users\DELL\.pillhaven\pillhaven-upload.jks`

**Interfaces:**
- Produces: `./gradlew :app:bundleRelease` → `app/build/outputs/bundle/release/app-release.aab`, signed with the upload key.
  `keystore.properties` keys are `storeFile`, `storePassword`, `keyAlias`, `keyPassword`.

- [ ] **Step 1:** Create the upload key, alias `upload`, RSA 2048, valid 10000 days, with a random 24-character password
  (the same password for store and key). Use `"/c/Program Files/Android/openjdk/jdk-21.0.8/bin/keytool" -genkeypair ...`
  with `-dname "CN=Hemz, O=Pillhaven, C=IN"`. Write `keystore.properties` pointing `storeFile` at the absolute path.
- [ ] **Step 2:** Add `keystore.properties` and `*.jks` to `.gitignore`. Run `git status --short` and check that neither file is listed.
- [ ] **Step 3:** In `build.gradle.kts`:
  - set `applicationId = "com.hemzzdevv.pillhaven"`, `targetSdk = 36`
  - read `rootProject.file("keystore.properties")` when it exists into `signingConfigs.create("upload")`, and use it
    for `release`
  - replace the debug-key comment with one saying release builds are signed with the upload key from `keystore.properties`
  - add a check that runs only for release tasks (`gradle.taskGraph.whenReady` or a `doFirst` on `preReleaseBuild`). It
    throws `GradleException`:
    - `"keystore.properties is missing: release builds need the upload key (see docs/play-store-launch.md)"` when the
      file is absent
    - `"app/src/main/assets/brands.db is missing: run tools/build_brand_index.py first"` when the brand db is absent

  Debug builds must not need either file.
- [ ] **Step 4:** Robolectric 4.14.1 tops out at SDK 35. Create `app/src/test/resources/robolectric.properties` containing `sdk=35`.
- [ ] **Step 5:** Run the unit tests (Global Constraints command). Expected: all pass.
- [ ] **Step 6:** Run `./gradlew :app:bundleRelease`. Expected: BUILD SUCCESSFUL and the `.aab` exists. Rename
  `keystore.properties` temporarily and run it again: expect a failure with the keystore message. Restore the file.
- [ ] **Step 7:** Check the bundle is signed with the upload key: `keytool -printcert -jarfile app/build/outputs/bundle/release/app-release.aab`
  shows `CN=Hemz, O=Pillhaven, C=IN`.
- [ ] **Step 8:** Commit `app/build.gradle.kts`, `.gitignore` and `robolectric.properties` with "build: Play release identity
  (com.hemzzdevv.pillhaven, target 36), upload-key signing, brand db required".

### Task 2: Android 16 target check

**Files:** Modify only what the check below finds broken.

- [ ] **Step 1:** Run `./gradlew :app:lintRelease`. Expected: no new errors. Fix anything that targetSdk 36 raises (e.g.
  edge-to-edge, predictive back, `elegantTextHeight`). Note anything you only warn about.
- [ ] **Step 2:** Check that every Activity calls `enableEdgeToEdge()`; Android 16 enforces edge-to-edge. Check `MainActivity`,
  `AlarmActivity` and `HealthPrivacyActivity`. Add it where missing and make sure content keeps its insets padding.
- [ ] **Step 3:** Run the unit tests and commit any fixes with "fix: Android 16 target adjustments". If there's nothing to
  change, skip the commit.

### Task 3: Exact-alarm and battery permissions

**Files:**
- Modify: `app/src/main/AndroidManifest.xml:7-11`
- Modify: `app/src/main/java/com/medtrack/ui/settings/PermissionRequests.kt` (`battery()`)
- Modify: `app/src/main/java/com/medtrack/ui/setup/SetupSteps.kt` (`BATTERY`)
- Test: `app/src/test/java/com/medtrack/ui/setup/SetupStepsTest.kt`

**Interfaces:**
- Consumes: `SetupStatus.batteryUnrestricted` (unchanged; `isIgnoringBatteryOptimizations` needs no permission).

- [ ] **Step 1: Write the failing test.** Add `batteryStepSendsUserToTheList()` to `SetupStepsTest`:

```kotlin
@Test fun batteryStepSendsUserToTheList() {
    assertEquals("Open battery settings", SetupStep.BATTERY.allowLabel)
    assertTrue(SetupStep.BATTERY.reassurance.contains("Pillhaven"))
}
```

- [ ] **Step 2:** Run `./gradlew :app:testDebugUnitTest --tests "com.medtrack.ui.setup.SetupStepsTest"`. Expected: FAIL (label is
  "Allow unrestricted battery").
- [ ] **Step 3:** Set the `BATTERY` copy, verbatim:
  - title "Battery"
  - body "So Android doesn't put Pillhaven to sleep and silence a reminder."
  - reassurance "In the list, find Pillhaven and choose Don't optimise (or Unrestricted). It wakes only at dose times, so it uses very little battery."
  - allowLabel "Open battery settings"
- [ ] **Step 4:** In `PermissionRequests.battery()`, open `Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)`
  without a package Uri. Keep using `context.open`, which falls back to app details.
- [ ] **Step 5:** In the manifest:
  - delete the `USE_EXACT_ALARM` and `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` lines
  - drop `android:maxSdkVersion="32"` from `SCHEDULE_EXACT_ALARM`
- [ ] **Step 6:** Run all unit tests. Expected: PASS, including `ReminderEngineTest` and `SetupStepsTest`.
- [ ] **Step 7:** Grep for `USE_EXACT_ALARM|REQUEST_IGNORE_BATTERY` in `app/src/main`. Expected: no hits.
- [ ] **Step 8:** Commit with "fix(permissions): user-granted exact alarms and the battery list, per Play policy".

### Task 4: Backup rules

**Files:**
- Create: `app/src/main/res/xml/data_extraction_rules.xml` (Android 12+)
- Create: `app/src/main/res/xml/backup_rules.xml` (Android 9–11)
- Modify: `app/src/main/AndroidManifest.xml` (`android:dataExtractionRules`, `android:fullBackupContent` on `<application>`)

- [ ] **Step 1:** Both files back up everything by default, excluding only `sharedpref` `medtrack.xml`. It holds
  `setupDone`, `lastScanAt` and `pendingCapture`, which must not carry to a new phone. In
  `data_extraction_rules.xml`, put the exclude under both `<cloud-backup>` and `<device-transfer>`. The cache and
  `noBackupFilesDir` (where `brands.db` is copied) are never backed up anyway.
- [ ] **Step 2:** Add an XML comment explaining why `medtrack.xml` is excluded (a new phone must run Get set again,
  because permissions aren't restored).
- [ ] **Step 3:** Build with `./gradlew :app:assembleDebug`. Expected: success and no lint error about backup rules.
- [ ] **Step 4:** Commit with "feat(backup): keep medicines in Android backup, but not setup state".

### Task 5: Credits and privacy policy link in the app

**Files:**
- Modify: `app/src/main/java/com/medtrack/ui/settings/SettingsScreen.kt` (new `About()` after `Support()`)
- Modify: `app/src/main/java/com/medtrack/health/HealthPrivacyActivity.kt`

**Interfaces:**
- Produces: `const val PRIVACY_POLICY_URL = "https://hemz2006.github.io/Pillhaven/privacy-policy.html"`. Make it public in
  `SettingsScreen.kt` so `HealthPrivacyActivity` uses the same value.

- [ ] **Step 1:** Add the About section, following the existing `Support()` pattern: a `ListItem` with
  `clickable(onClickLabel=…)`, an `ACTION_VIEW` intent, and a Toast if no app can open it. It has two rows:
  - "Privacy policy", supporting "What Pillhaven stores and sends", opens `PRIVACY_POLICY_URL`.
  - "Medicine brand list", supporting "A-Z Medicine Dataset of India by Shudhanshu Singh (Kaggle)", opens the dataset
    URL.
  - Below the rows, the muted text "Version 1.0", taken from `packageManager.getPackageInfo(packageName, 0).versionName`.
- [ ] **Step 2:** In `HealthPrivacyActivity`:
  - after the existing paragraph, add a `TextButton` "Read the full privacy policy" that opens `PRIVACY_POLICY_URL`
  - change "the data stays on this phone" to "the data stays on this phone (and in your own Android backup, if it's on)"
- [ ] **Step 3:** Run `./gradlew :app:installRelease` on the phone (serial F6CAX8X8ZH5HYXIZ; wrap adb in `timeout`). It
  installs as a new app next to `com.medtrack`. Open Settings: check that About shows, both links open the browser, and
  the version reads 1.0.
- [ ] **Step 4:** Commit with "feat(settings): About with privacy policy, brand list credit and version".

### Task 6: Privacy policy page

**Files:**
- Create: `docs/privacy-policy.md` (Jekyll front matter `title: Pillhaven privacy policy`, `permalink: /privacy-policy.html`)
- Create: `docs/_config.yml` (`title: Pillhaven`, `theme: jekyll-theme-minimal`; exclude `superpowers/` so only public pages publish)

- [ ] **Step 1:** Write the policy, plain and truthful, dated 19 October 2026, with these sections:
  - **Who we are:** Pillhaven, by Hemz, hemzz.devv@gmail.com.
  - **What stays on your phone:** medicines, schedules, dose history, photos and notes. No account, no analytics, no ads.
    The developer can't see any of it.
  - **Android backup:** Android may back up the app's data to your own Google account, encrypted and under your control.
    Turn it off in phone settings. Backups may not include every photo if they exceed Android's size limit.
  - **Online lookups:** to show ingredient info, the app sends only the ingredient name (e.g. "Paracetamol") over https
    to RxNorm (US NLM), PubChem (US NIH) and MedlinePlus (US NLM). No personal or device data is sent.
  - **Reading packs:** on-device ML Kit, with usage statistics switched off. Photos never leave the phone.
  - **Health Connect:** reads only the nutrition (meal) records you log, including in the background, only to time
    after-meal reminders. It never writes, and the data never leaves the phone or is shared. Its use follows the Health
    Connect Permissions policy, including the Limited Use requirements.
  - **Pharmacy links:** Order opens Tata 1mg, PharmEasy or Netmeds in your browser. Their privacy policies apply there.
  - **Children:** the app is not directed at children under 13 (or 18 in India).
  - **Deleting your data:** delete a medicine in the app, or uninstall or clear the app's storage. Backups are managed in
    your Google account.
  - **Changes and contact.**
- [ ] **Step 2:** Commit with "docs: privacy policy for GitHub Pages". The page goes live once Pages is turned on (Task 8).

### Task 7: Store listing and Console answers

**Files:**
- Create: `docs/play-store-listing.md`
- Modify: `docs/_config.yml` exclude list (keep this file unpublished)

- [ ] **Step 1:** Write these sections, ready to paste:
  - **App name:** "Pillhaven: Medicine Reminder" (≤30 chars).
  - **Short description:** ≤80 chars. Check the length.
  - **Full description:** ≤4000 chars. Cover:
    - the features from README "What it does": scan the pack, 250,000 Indian brands, schedules, drop sides,
      alarm-style reminders, Today, meal reminders, ordering, privacy
    - end with the dataset credit, "Pillhaven doesn't give medical advice; follow your doctor's instructions.", and the
      support email
  - **Category:** Medical.
  - **Contact:** hemzz.devv@gmail.com. Privacy policy URL from Global Constraints.
  - **Data Safety:** no data collected and none shared. Reasons:
    - Ingredient names sent to RxNorm, PubChem and MedlinePlus aren't user data tied to the user. Cite Google's
      Data Safety help (answer/10787469): data processed ephemerally or not linked to the user.
    - Android Auto Backup isn't collection by the developer.
    - Data encrypted in transit: yes. Users can request deletion: in-app and uninstall.
    - Verify each claim against answer/10787469 and quote the line used.
  - **Content rating questionnaire:** reference/utility app, no violence, sexual content, gambling or user-generated
    content. It mentions medicines, but doesn't sell them or promote drug use.
  - **Target audience:** 18+. Not designed for children.
  - **Ads:** no.
  - **Health apps declaration and Health Connect:** the data type (Nutrition, read), the background read, the use case
    ("time medication reminders after meals"), and that data isn't shared or sold.
  - **Full-screen intent declaration:** "Medication alarm the user schedules; it rings like an alarm clock so a dose isn't
    missed."
  - **Exact alarms:** none needed now (`SCHEDULE_EXACT_ALARM` is user-granted). Say so.
  - **Known risk:** the dataset has no stated licence. If the owner or Kaggle objects, replace brand search.
- [ ] **Step 2:** Commit with "docs: Play Store listing and Console answers".

### Task 8: Launch guide and README

**Files:**
- Create: `docs/play-store-launch.md` (excluded from Pages)
- Modify: `README.md` (install steps, credits, privacy)
- Modify: `docs/manual-test-checklist.md` (add a "Release build" section: new ID installs, Get set shows, exact alarm
  rings, full-screen alarm, brand search finds "Dolo 650", meal reminder connects, About links)

- [ ] **Step 1:** Write the guide as numbered steps:
  0. Back up the key **now**: `C:\Users\DELL\.pillhaven\pillhaven-upload.jks` plus `keystore.properties` (passwords) to two
     places, e.g. a USB stick and a password manager. Never commit them. If lost, the upload key can be reset through Play support.
  1. Wait for account verification.
  2. Turn on GitHub Pages: repo Settings → Pages → Deploy from branch `master`, folder `/docs`. Open the privacy URL to check.
  3. Build: `./gradlew :app:bundleRelease`. It needs `brands.db` and `keystore.properties`.
  4. Play Console → Create app: name, English (India), App, Free, accept the declarations.
  5. App content: privacy policy, ads (no), app access (no login), content rating, target audience, data safety, health
     apps declaration, Health Connect, full-screen intent, government apps (no), financial features (none). Paste from
     `docs/play-store-listing.md`.
  6. Store listing: text from the listing doc, plus your graphics (icon 512×512, feature graphic 1024×500, 2–8 phone
     screenshots).
  7. Testing → Closed testing:
     - create a track and add testers by email list (12 or more of your relatives)
     - upload the `.aab`, opt in to Play App Signing, and roll out
     - send testers the opt-in link
     - testers uninstall the old directly-installed Pillhaven first, install from Play, re-add their medicines, and must
       **stay opted in 14 days**
  8. Health Connect review: watch the Inbox/Policy status. Answer requests; launch waits for approval.
  9. After 14 days with 12 or more testers: Dashboard → Apply for production. Answer the testing questions (who tested,
     what feedback, what changed).
  10. After approval: Production → create a release with the same or a newer `.aab` (bump versionCode for every upload),
      choose countries (India, or more), and roll out. Review can take several days.
  11. For updates: bump `versionCode` and `versionName`, run `bundleRelease`, and upload to Production.
  - Note the dataset-licence risk, and cite the Google pages used.
- [ ] **Step 2:** Update the README:
  - install step 4 says `installRelease` needs `keystore.properties` (see the launch guide), or use `installDebug`
  - Credits gains the dataset creator line
  - Privacy links the policy URL
- [ ] **Step 3:** Commit with "docs: Play Store launch guide, README and release checklist".

### Task 9: Final verification

- [ ] **Step 1:** Run the unit tests. Expected: all pass. Run `./gradlew :app:lintRelease`: no errors.
- [ ] **Step 2:** Run `./gradlew :app:bundleRelease`. Expected: the `.aab` exists, signed with CN=Hemz. Check that it contains
  `base/assets/brands.db`: `unzip -l app/build/outputs/bundle/release/app-release.aab | grep brands.db`.
- [ ] **Step 3:** On the phone, run the release checklist from Task 8. Ask the developer before uninstalling the old
  `com.medtrack` app on their phone.
- [ ] **Step 4:** Push `master` only after the developer says so, then give them the launch-guide steps.
