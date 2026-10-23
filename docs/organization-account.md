# Moving Pillhaven to an organization developer account

Why: Google rejected Pillhaven (22 October 2026) under the Play Console Requirements policy. Pillhaven is a medication
reminder, which Google's health app form lists under **Medical → Medication and Treatment Management**, and since
31 August 2024 new developer accounts must be **organizations** to publish Medical health apps. The fix is the
account type, not the app: keep the honest health declaration and change the account to an organization.

Rules checked on 23 October 2026:
- [Choose a developer account type](https://support.google.com/googleplay/android-developer/answer/13634885)
- [Change your account type](https://support.google.com/googleplay/android-developer/answer/16260648)
- [Organization account requirements](https://support.google.com/googleplay/android-developer/answer/13628312)
- [Health app categories](https://support.google.com/googleplay/android-developer/answer/14738291)
- [D-U-N-S Number, Dun & Bradstreet India](https://www.dnb.co.in/duns/get-a-duns)

## What changes and what doesn't

- **Nothing in the app changes.** Same package name (`com.hemzzdevv.pillhaven`), same upload key, same bundle.
- **No transfer is needed.** Google lets a verified personal account change itself into an organization account; the
  app, its App content answers and its closed-testing track stay where they are.
- **It's one-way.** An organization account can't be turned back into a personal one.
- **Your developer name on Play** can stay as it is; it doesn't have to be the legal business name.
- **The legal name and address become public-facing** on the payments profile and must match Dun & Bradstreet
  exactly, everywhere, for as long as the account exists.

## Step 1. Register a business (sole proprietorship)

Dun & Bradstreet registers businesses, not people. An individual developer in India usually registers as a sole
proprietor: you, trading under a name such as "Hemzz Devv".

1. **Udyam (MSME) registration**, free, online at [udyamregistration.gov.in](https://udyamregistration.gov.in) with your
   Aadhaar and PAN. Choose an activity like software / computer programming. You get an Udyam certificate with the
   business name and address. Use the official site only; look-alike sites charge fees.
2. **GST registration** is not required for a free app with no turnover. Leave GSTIN blank on the D&B form.
3. **Business address:** your home address is allowed (the D&B form asks whether the business is home-based). Use
   exactly the same name and address, spelled the same way, on Udyam, D&B, Google Payments and Play Console.
4. **Business phone and email:** you can use your own. They must stay working; Google verifies them with one-time
   passwords.

## Step 2. Get a D-U-N-S number (free, up to about 30 business days)

1. First search the [D&B lookup](https://www.dnb.co.in/duns/get-a-duns) in case your business is already listed.
2. If not, fill in the free registration form on dnb.co.in:
   - Primary reason: **Google Developer**
   - Legal name: the Udyam business name; DBA: the name you use (or the same)
   - Address, phone, contact name (you) and title (Proprietor), employees: 1, home-based: Yes if it is
   - GSTIN: blank
3. Keep the Udyam certificate, PAN and an address proof (e.g. electricity bill) ready. D&B may email or call to check.
4. **Never pay a third party.** D&B says it has no authorised partners in India; sites promising a "fast" D-U-N-S for a
   fee aren't needed.

Wait for the email with your 9-digit D-U-N-S number. D&B can take a few days more to share it with Google, so if
Google can't find it at first, wait 2 to 3 days and try again.

## Step 3. Get a website Google can verify

Google requires an organization website and verifies it before the **Change account type** button appears.

- **Simplest:** the existing GitHub Pages site, `https://hemz2006.github.io/Pillhaven/` (it already hosts the privacy
  policy). Add a short home page there that names the business, says what Pillhaven is, and links the privacy
  policy and support email. Ask Claude to add it.
- **Safer:** a cheap custom domain (e.g. `pillhaven.app` or `hemzzdevv.in`) pointed at the same GitHub Pages site.
  Google may be stricter with shared hosting addresses like `github.io`; if verification of the github.io address
  fails, use a custom domain.
- Verification is done through Google Search Console (add a file or a meta tag to the site). Play Console links to
  the instructions when you click **Send verification request**.

## Step 4. Change the account type in Play Console

Your personal account must be fully verified with no warnings on the Play Console home page.

1. **Developer account → About you:** enter and save the organization website, then **Send verification request**.
2. Once the website is verified, click **Change account type**.
3. **Create payments profile** (a personal payments profile can't be reused): organization type (sole
   proprietorship / individual business), size, phone, and your **D-U-N-S number**. The legal name and address are
   taken from D&B and must match.
4. Enter contact details. Google sends one-time passwords to each email and phone.
5. If asked, upload documents on **Account details** (e.g. the Udyam certificate). Google emails the result.
6. **About you → Link your payments profile to your developer account → Confirm → Save.**
7. **Wait at least 72 hours** before submitting anything, as Google advises, so the account details sync.

## Step 5. Resubmit Pillhaven

1. Check **Policy → App content → Health apps** still says **Medication and Treatment Management** (Medical). It's
   accurate; keep it.
2. **Publishing overview → Send changes for review.** If the Console still shows the rejection, reply through the
   **Policy status** page (or **Submit an appeal**) saying the account is now an organization account, with the
   D-U-N-S number.
3. Carry on from step 7 of [play-store-launch.md](play-store-launch.md).

**Closed testing (12 testers, 14 days):** Google states this for personal accounts created after 13 November 2023.
Its pages don't say whether it still applies once such an account becomes an organization. Check Play Console's
Dashboard after the change: if **Apply for production** is gone and production releases are open, the closed test
is no longer required; if not, run it as in step 7.

## Checklist

- [ ] Udyam certificate (business name + address)
- [ ] D-U-N-S number from D&B (free)
- [ ] Organization website live and verified
- [ ] Payments profile for the organization, linked
- [ ] Account type shows Organization; waited 72 hours
- [ ] Pillhaven sent for review again
