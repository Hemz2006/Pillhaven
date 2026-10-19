# Pillhaven: Play Store listing and Console answers

Everything here is ready to paste into the Play Console. Policy facts were checked on 19 October 2026 against the
Google pages linked in each section; re-check anything marked *check* if the Console asks differently.

## Store listing (Grow users → Store presence → Main store listing)

**App name** (max 30): `Pillhaven: Medicine Reminder`

**Short description** (max 80):

```
Medicine reminders for your family, with 250,000 Indian brands built in.
```

**Full description** (max 4000):

```
Pillhaven is a safe place for all your family's medicines. Add a medicine in seconds, get reminded like an alarm clock, and tick off each dose as you go. Your medicines stay on your phone: no account, no ads, no tracking.

FIND ANY INDIAN BRAND
• Type the name on the pack, like "Dolo 650" or "Pan 40", and pick from about 250,000 Indian brands. Pillhaven fills in what's in it and the form (tablet, syrup, inhaler…). Brand search works offline.
• Or scan the box or strip with your camera. The pack is read on your phone and the matching brands are suggested.
• See each ingredient's chemical formula and what it's used for.

REMINDERS THAT RING
• A full-screen alarm with Taken, Snooze and Skip, even when the phone is locked.
• Lock-screen notifications only say a medicine is due; names show after you unlock. (The full-screen alarm, if you allow it, shows the medicine and its photo so the right dose is easy to find.)
• After-meal reminders (optional): log your meal in a fitness app through Health Connect, and the reminder comes a set time after it.

SCHEDULES FOR REAL PRESCRIPTIONS
• Times every day, chosen weekdays, every few hours, or after breakfast, lunch or dinner.
• Courses with an end date, and doses that change over time, like 6 times a day for two weeks, then 4.
• Eye, ear and nose drops: choose Left, Right or Both for each time. The alarm says "PATIENT'S RIGHT EYE" in large type, so nobody puts drops in the wrong eye.

TODAY AT A GLANCE
• See today's doses by time or by medicine, and tap to tick them off.
• Look back at what was taken, skipped or missed, or ahead at what's coming.

MADE FOR FAMILIES
• Photos of each pack, so everyone recognises the right medicine.
• Larger text and buttons, light and dark themes, and your choice of alarm colours.
• Order a medicine on Tata 1mg, PharmEasy or Netmeds in one tap, and get a reminder before a course runs out.

PRIVATE BY DESIGN
Everything you enter stays on your phone (and in your own Android backup, if it's on). Pillhaven only goes online to look up ingredient names. There's no account, no analytics and no ads.

Brand list: A-Z Medicine Dataset of India by Shudhanshu Singh (Kaggle).
Pillhaven doesn't give medical advice; follow your doctor's instructions.
Questions or ideas? hemzz.devv@gmail.com
```

**App category:** Medical. **Tags:** medication reminder, pill reminder.

**Contact details:** email `hemzz.devv@gmail.com`. Website: leave empty (or `https://github.com/Hemz2006/Pillhaven`).

**Privacy policy URL:** `https://hemz2006.github.io/Pillhaven/privacy-policy.html`

**Graphics:** your own. Icon 512×512 PNG, feature graphic 1024×500, 2–8 phone screenshots.

## App content (Policy → App content)

### Privacy policy
Paste the URL above.

### Ads
"No, my app does not contain ads."

### App access
"All functionality is available without special access" (no login).

### Content rating (IARC questionnaire)
- Category: **Reference, News, or Educational** (or "All other app types"; pick Reference if offered).
- Violence, sexuality, language, controlled substances, gambling: **No** to all. Pillhaven names medicines but doesn't sell
  them, show drug use, or reference illegal drugs.
- Users interact or share content with each other: **No**. Shares location: **No**. Digital purchases: **No**.
- Expected result: rated for everyone (e.g. IARC 3+ / PEGI 3).

### Target audience and content
- Target age groups: **18 and over** only.
- "Could your app unintentionally appeal to children?": **No**.

### News app
No.

### Data safety
Source: [Data safety help](https://support.google.com/googleplay/android-developer/answer/10787469). The rules that apply:
- "User data accessed by your app that is only processed locally on the user's device and not sent off device does
  **not** need to be disclosed." This covers medicines, doses, photos, notes and Health Connect meals.
- Backups: if the user's data goes "to their own external drive or cloud storage account (such as Google Drive…)" and
  "your app never collects or accesses the data", it needn't be declared. This covers Android backup.
- The ingredient lookup sends the ingredient name the user types (e.g. "Paracetamol", including partial words while typing, for suggestions) to RxNorm, PubChem and MedlinePlus. Treated
  conservatively, this is **Health info** sent off the device, so declare it as below. Google: "User data transmitted
  off device that is processed ephemerally needs to be included in your form response, but … will **not** be disclosed
  in your app's Data safety section." It isn't "sharing": the transfer follows a "user-initiated action, where the user
  reasonably expects the data to be shared" (they type an ingredient to look it up), and the privacy policy
  discloses it.

Answers:
1. Does your app collect or share any of the required user data types? **Yes**
2. Is all of the user data collected by your app encrypted in transit? **Yes** (https only)
3. Which of the following methods of account creation does your app support? **My app does not allow users to create an account**
4. Do you provide a way for users to request that their data is deleted? **Yes**. Deleting a medicine removes it and its
   history; clearing storage or uninstalling removes everything. Point to the privacy policy.
5. Data types: tick only **Health and fitness → Health info**.
   - Collected: **Yes**. Shared: **No**.
   - Processed ephemerally: **Yes**.
   - Required or optional: **Optional** (lookups happen only while the user types or looks up ingredients; the app works offline).
   - Purpose: **App functionality**.

*Check:* when you review the preview, the listing's safety section should say "No data shared with third parties"
and show no collected data, because ephemeral data isn't displayed.

### Government apps
No. **Financial features:** none. **Health apps:** see below.

### Health apps declaration
Source: [Health apps / Health Connect policy](https://support.google.com/googleplay/android-developer/answer/12991134).
- Health features: **Medication management / reminders** (pick "Medication and treatment management" if listed), and
  **Nutrition and weight management** only if the form asks which data the app reads.
- Is the app a medical device / gives diagnosis? **No**.
- Paste the privacy policy URL.

### Health Connect permissions declaration
Data types requested, with the justification to paste for each:
- **Nutrition (read):** "Pillhaven reads meal (nutrition) records the user logs in other apps, only to time reminders
  for medicines the user marked 'after a meal': the reminder rings a set time after the logged meal, or at a fallback
  time. Pillhaven never writes data, and meal data never leaves the device, isn't shared or sold, and isn't used for
  ads or any other purpose."
- **Read health data in the background:** "After-meal medication reminders have to fire while the app is closed. A
  periodic background check reads only nutrition records logged since the last check to schedule the reminder. Nothing
  is stored off-device."
- Limited Use: confirm the app complies. The privacy policy states it.
- If Google asks for a video, record the screen: Settings → Meal reminders → Connect, then a medicine with an
  "after lunch" schedule, then logging a meal in a fitness app, then the reminder arriving.

### Full-screen intent declaration
Source: [Foreground service and full-screen intent requirements](https://support.google.com/googleplay/android-developer/answer/13392821).
- Core functionality: **Alarm** (the closest of the permitted options).
- Justification: "Pillhaven's core function is medication alarms the user schedules. When a dose is due, it shows a
  full-screen alarm, like an alarm clock, so the dose isn't missed while the phone is locked. The user can mark it
  taken, snooze or skip it from that screen. Users who decline the permission get a high-priority notification
  instead."

### Exact alarms
None needed. Pillhaven uses `SCHEDULE_EXACT_ALARM`, which the user grants in system settings. It doesn't use
`USE_EXACT_ALARM`, which Play reserves for alarm-clock and calendar apps.

### Foreground service types
None. Pillhaven never starts a foreground service. The WorkManager library adds the generic `FOREGROUND_SERVICE`
permission to the merged manifest, but no `FOREGROUND_SERVICE_<TYPE>` permission. If the Console still shows the
declaration, answer that the app uses no foreground service types.

For reference, the release build's permissions are:
- INTERNET, ACCESS_NETWORK_STATE
- POST_NOTIFICATIONS
- SCHEDULE_EXACT_ALARM, USE_FULL_SCREEN_INTENT
- RECEIVE_BOOT_COMPLETED, WAKE_LOCK, FOREGROUND_SERVICE (the last two from WorkManager)
- health.READ_NUTRITION, health.READ_HEALTH_DATA_IN_BACKGROUND

## Known risk: the brand list

The ~250,000 brands come from the "A-Z Medicine Dataset of India" by Shudhanshu Singh on Kaggle
(https://www.kaggle.com/datasets/shudhanshusingh/az-medicine-dataset-of-india). The dataset doesn't state a licence. The
app and store listing credit its creator. If the creator or Kaggle objects, or Google receives a complaint, brand search
will need replacing with a licensed source in an update.
