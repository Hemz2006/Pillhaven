# Launching Pillhaven on Google Play

Your step-by-step from "account verified" to "live on the Play Store". The text to paste into each form is in
[`play-store-listing.md`](play-store-listing.md).

Rules checked on 19 October 2026:
- [Testing requirements for new personal accounts](https://support.google.com/googleplay/android-developer/answer/14151465)
- [Target API level](https://support.google.com/googleplay/android-developer/answer/11926878)
- [Full-screen intent](https://support.google.com/googleplay/android-developer/answer/13392821)
- [Health Connect](https://support.google.com/googleplay/android-developer/answer/12991134)
- [Data safety](https://support.google.com/googleplay/android-developer/answer/10787469)

> **Account type (22 October 2026):** Google rejected the first review because Pillhaven is a Medical health app
> (medication reminders) and new **personal** accounts can't publish those. Change the account to an **organization**
> first: follow [`organization-account.md`](organization-account.md), then continue here from step 7.

## 0. Back up your upload key (do this today)

Every update you ever publish must be signed with the same upload key. It lives outside the project, in
`C:\Users\DELL\.pillhaven\`:

- `pillhaven-upload.jks`: the key itself
- `keystore.properties`: its passwords and location (a copy also sits in the project folder, git-ignored)

Copy **both files** to two safe places, e.g. a USB stick and a private cloud folder or password manager. Never commit
them, email them, or put them in the public repo. If they're ever lost, Google can reset the upload key (Play Console →
Setup → App signing → Request upload key reset), but that takes days and needs your account.

To build on another computer, copy both files there and put `keystore.properties` in the project's root folder. Edit
its `storeFile=` line if the `.jks` lives somewhere else.

## 1. Wait for account verification

Google emails you when your developer account is verified. Pillhaven needs an **organization** account (see
[`organization-account.md`](organization-account.md)). Until then, the steps below that need the Console
are locked.

## 2. Publish the privacy policy (GitHub Pages)

1. Push the project to GitHub (`master`).
2. On github.com/Hemz2006/Pillhaven, go to **Settings → Pages**.
3. Under Source, choose **Deploy from a branch**, then branch **master** and folder **/docs**, and **Save**.
4. After a minute or two, open https://hemz2006.github.io/Pillhaven/privacy-policy.html and check that it loads. The
   app's Settings → About → Privacy policy opens this page.

## 3. Build the release bundle

Build from the latest `master` (the bundle must include the newest fixes, such as the alarm that keeps ringing until
the dose is handled):

```sh
./gradlew :app:testDebugUnitTest
./gradlew :app:bundleRelease
```

Before uploading, install the release build on a phone (`./gradlew :app:installRelease`) and run the **Release build**
section of [`manual-test-checklist.md`](manual-test-checklist.md): Get set, the battery page, an alarm ringing on the
lock screen until you tap Taken, brand search and Health Connect.

The file to upload is `app/build/outputs/bundle/release/app-release.aab`. The build stops with a clear message if
`keystore.properties` or the brand list (`app/src/main/assets/brands.db`, see README) is missing.

## 4. Create the app in Play Console

**Home → Create app**:
- App name: `Pillhaven: Medicine Reminder`
- Default language: **English (India) – en-IN**
- Type: **App**. Price: **Free** (this can't be changed to paid later).
- Tick the declarations (Developer Program Policies, US export laws) and **Create app**.

## 5. Fill in App content

**Policy → App content.** Use the answers in `play-store-listing.md` for each item:
- Privacy policy
- Ads (no)
- App access
- Content rating
- Target audience
- News app (no)
- Data safety
- Government apps (no)
- Financial features (none)
- Health apps
- Health Connect permissions
- Full-screen intent
- Foreground services (none), if shown

Every item must show a green tick before you can send a release for review.

## 6. Store listing

**Grow users → Store presence → Main store listing**:
- Paste the name and descriptions from `play-store-listing.md`.
- Upload your graphics: app icon 512×512 PNG, feature graphic 1024×500, and 2–8 phone screenshots.
- Category: **Medical**. Contact email: `hemzz.devv@gmail.com`.

## 7. Closed test with 12 testers for 14 days

Google requires this for new personal accounts: "a closed test … with a minimum of 12 testers who have been opted in
continuously for at least 14 days" before you can apply for production. Your relatives' earlier test doesn't count,
because it wasn't installed through Play.

1. **Testing → Closed testing → Create track** (or use the default "Closed testing – Alpha").
2. **Testers:** create an email list with the Google account (Gmail) of each tester. Add at least **14**, so the track
   still has 12 if someone drops out.
3. **Create new release:**
   - when asked about app signing, choose **Use Google-generated key** (Play App Signing)
   - upload `app-release.aab`, name it `1 (1.0)`, add release notes ("First test release")
   - **Review release → Start rollout to Closed testing**
4. The first review can take a few days. When the track shows **Available**, copy the **opt-in link** (Testers tab).
5. Send each tester:
   - The opt-in link. They open it signed in to the same Google account, tap **Become a tester**, then the Play Store
     link.
   - **Before installing:** uninstall the old Pillhaven you installed for them over USB. The Play version is a separate
     app, so with both installed every dose would ring twice. Their medicines don't carry over; they add them again.
   - When Pillhaven opens, go through every **Get set** screen. On the Battery screen, tap **Open battery settings**,
     tap the words **Allow background usage** (not the switch) and choose **Unrestricted**; on some Realme, Oppo and
     OnePlus phones turn on **Allow background activity** instead. Without this, reminders may not ring.
   - Ask them to **stay opted in for at least 14 days**, keep the app installed, and use it. Google checks
     "opted in continuously"; leaving the test resets their count.
6. Mark day 1 on a calendar. Fix anything they report and upload a new build (step 10) to the same closed track if
   needed; testers keep their 14-day count across updates.

## 8. Health Connect review

Because Pillhaven reads meals from Health Connect (also in the background), Google reviews that access. Watch
**Inbox** and **Policy status** in the Console. If they ask for changes or a video, the justification and video
script are in `play-store-listing.md` → Health Connect. You decided the launch waits for this approval.

## 9. Apply for production

After 14 days with 12+ testers opted in: **Dashboard → Apply for production**. Google asks about your test. Answer
honestly and specifically:
- Who tested: family members managing medicines for parents and themselves.
- How you recruited them.
- What feedback you got, and what you changed because of it.
- Why the app is ready.

Google usually answers within about 7 days.

## 10. Release to production

1. **Production → Countries/regions:** add **India** (and any others you want).
2. **Create new release:** upload the `.aab` (or **Add from library** to reuse the tested one), add release notes,
   **Review → Start rollout to Production**.
3. Review can take from hours to several days. After approval, Pillhaven appears on the Play Store at
   `https://play.google.com/store/apps/details?id=com.hemzzdevv.pillhaven`.

## 11. Updating the app later

1. In `app/build.gradle.kts`, increase `versionCode` by 1 (it must go up with every upload) and set `versionName`
   (e.g. "1.1").
2. Run `./gradlew :app:bundleRelease`.
3. Upload the new `.aab` to Production (or test it on the closed track first).

Remember: from 31 August each year Google raises the minimum target SDK (currently Android 16 / API 36). Updates must
keep up.

## Known risk: the brand list

Brand search uses the "A-Z Medicine Dataset of India" by Shudhanshu Singh (Kaggle), which doesn't state a licence. You
decided to ship it with credit in the app and listing. If its creator, Kaggle or Google objects, brand search will need
a replacement in an update. Keep an eye on your support email and the Console Inbox.
