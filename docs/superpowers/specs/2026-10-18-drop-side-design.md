# Which side the drops go in: design

Date: 2026-10-18
Status: Approved 2026-10-18

## 1. Why

Pillhaven is a free app for people in India managing their family's medicines. Doctors often prescribe the
same drops for different sides at different times:

- "M" in the **left eye in the morning, right eye at night**;
- "M" in the **left eye before surgery, right eye after**.

Today nothing in the app says which side a dose is for, so the person giving the drops (often a family member,
at night, half asleep) can easily put them in the wrong eye. This feature makes every drops dose say which side
it is for, loudly, everywhere the dose appears.

**Success:** at any reminder, nobody has to remember or guess which side. The alarm says it in large type, and
the history shows afterwards which side each dose went to.

## 2. Goals, non-goals, anti-goals

Goals
- Eye, ear and nose drops: choose the body part once per medicine; choose Left, Right or Both per schedule block.
- Show the side on every place a dose is shown: Today (both views), the dose sheet, the medicine's page,
  the Medications list where a dose line shows, the notification (when unlocked), the alarm and the dose history.
- Refuse to save an eye/ear/nose drops medicine until every schedule block has a side.

Non-goals
- Sides for other forms (creams on the left knee, injections in the left arm). Not asked for.
- Alternating sides automatically within one block ("every 6 hours, alternate eyes").
- Separate stock or restock per side.

Anti-goals (a failure even if it works)
- The side is easy to miss: small grey text, or only on some screens.
- The side means the caregiver's left rather than the patient's left.
- Editing a schedule later changes which side old history entries say.
- Existing medicines or other forms get harder to add or edit.

## 3. Decided with the user

**Body part.** When Form is **Drops**, the editor shows **Drops for: Eye / Ear / Nose / Mouth or other** (one choice
per medicine). Ear and nose drops get Left / Right / Both just like eyes; Mouth or other (e.g. vitamin D drops for
babies) asks for no side. For a new drops medicine the choice is required, so nobody skips the side by accident.

**Side per schedule block (option A from the mockups).** Each time-or-meal block in the schedule editor gets
**Which eye (patient's): Left / Right / Both** (wording follows the body part: eye, ear or nostril). All times inside a
block share its side. "Left at 8 AM, right at 9 PM" is two blocks, added with the existing
**Add another time or meal**. "Left before surgery, right after" uses the existing changing-doses steps: step 1's
block is Left, step 2's block is Right. Every-N-hours and after-meal blocks get a side the same way.

**Saving is refused** until every block in every step has a side, when the body part is eye, ear or nose. The message
names the block, in the same style as the existing schedule errors (e.g. "Dose 2: choose which eye").

**Display (option C from the mockups).**
- Every place that shows a single dose (Today by time, each dose tile in Today by medication, the dose sheet, the
  dose history): an amber badge, e.g. **RIGHT EYE**, **LEFT EAR**, **BOTH NOSTRILS**. Amber is the existing warning colour
  (`medColors.amber` on `onAmber`) and works in light and dark.
- Places that show a whole medicine (Medications list, the top of the medicine page, the By medication header):
  no badge by the name, since one medicine can have different sides through the day. Instead the schedule text carries
  the side per block, e.g. "Daily at 8:00 AM · Left eye; Daily at 9:00 PM · Right eye".
- Alarm: very large **PATIENT'S RIGHT EYE** under the medicine name, plus a Left / Right pair with the dosed side lit
  (both lit for Both). The picture is drawn from the patient's point of view and labelled as such.
- Notification, when unlocked: the side in the text ("M · 1 drop · Right eye"). On the lock screen the notification
  keeps saying only that a medicine is due, as now.
- Dose history (medicine page): each entry shows its side.

**Patient's side.** Left and right always mean the patient's. The alarm says "patient's" explicitly; the editor's label
does too.

## 4. Behaviour rules

- **History keeps its side.** A dose's side is the side of the block that produced it, fixed when the dose is recorded.
  Editing the schedule afterwards, or changing the form or body part, must not change past entries: each recorded dose
  stores its body part and side together (e.g. "LEFT EYE"). (Today's dose log has no link back to the block that made it
  except `slot` for meal doses, so this has to be recorded on the dose.) *Builder-check call by Claude.*
- **Same time, two sides.** Dose logs are unique per medicine and due time. If two blocks would ring at the same time for
  different sides, saving is refused with a message suggesting **Both**.
- **Changing the form or body part.** Switching away from Drops, or to Mouth or other, hides the side choices; the
  blocks' sides are ignored for new doses and the schedule text. Switching back shows them again. Past dose
  entries keep what they recorded.
- **Existing medicines.** Drops medicines saved before this feature have no body part, so nothing changes for them
  until they're edited. Editing one asks for the body part like a new drops medicine. Once Eye, Ear or Nose is
  picked, every block needs a side.
- **Brand guess.** `guessForm` already picks Drops from the pack name. Guessing the body part from the name ("Eye Drops",
  "Ear Drops", "Nasal") is allowed but optional; if guessed, the user can still change it.

## 5. Constraints

- Kotlin, Jetpack Compose (Material 3), Room; follow the existing packages (`data/`, `schedule/`, `reminders/`, `ui/`).
- Package ID stays `com.medtrack`; the database file stays `medtrack.db`. Existing data must survive the update:
  any schema change gets a Room migration (current version 4) and a migration test like the existing `MigrationTest`.
- Schedules are stored as JSON (`ScheduleJson`, `ignoreUnknownKeys`); new fields there need defaults so old JSON still reads.
- Accessibility: the badge and the alarm side text must be read out by TalkBack, and stay legible at the app's
  larger size settings (the alarm already scales with them).
- Privacy: the lock-screen notification must not gain the side or the name.

## 6. Left to the builder

- Where the side and body part are stored (a field on each rule or block in the schedule JSON, a column on the
  medication, a column on `dose_logs`), as long as section 4's rules hold.
- How the editor labels read for each body part ("Which ear", "Which nostril"), and the exact badge wording.
- The drawing of the alarm's Left/Right picture (a simple pair of labelled shapes is enough).
- Whether to guess the body part from the pack name.
- Tests: follow the repo's habits. At least: schedule validation (missing side, same time with two sides), the side
  recorded on dose logs and kept after a schedule edit, the migration, and Today model items carrying their side.
- Updating `docs/manual-test-checklist.md` with a drops-side section.
