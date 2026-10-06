# Running the pre-test on Prolific

Prolific wraps the chain at both ends: it sends participants to Survey 1 with an id on the URL, and
takes them back from Survey 2 when they are done.

```
Prolific  →  Survey 1 (Intro)  →  Salvena  →  Survey 2 (Outro)  →  Prolific
             consent               the task     emotion T1          submission
             briefing                           filler, T2          complete
             randomiser                         checks, debrief
```

Prolific's interface is redesigned often, so the labels below may not match word for word. What
each step has to achieve does not change; where the wording differs, look for the setting that does
that thing.

---

## Before you start

None of this works until all four are true:

- [ ] **Both surveys are Active**, not drafts. A draft's anonymous link does not accept responses.
- [ ] **The app is deployed** and `<appUrl>/data` asks for a password.
- [ ] **The chain works end to end** without Prolific — you have run all four arms through Survey
      1's link by hand and `./scripts/check_join.py` came back clean.
- [ ] **The pilot data is gone**: `./scripts/azure_db_reset.sh`, and the test responses deleted in
      both surveys. Prolific participants must not land in a dataset that already has `test-` rows.

Do not debug the chain and Prolific at the same time. Every problem below assumes the chain itself
is known good.

---

## Step 1 — the three Prolific fields in Survey 1

Prolific puts three values on the URL. Survey 1's Embedded Data block must be ready to receive them:

```
PROLIFIC_PID
STUDY_ID
SESSION_ID
```

All three in the **"Value will be set from Panel or URL"** state — the one with **no `=`** beside
the field name.

This is the single most common way this chain breaks, and it breaks silently. A field given an
explicit empty value is *assigned* empty when the flow runs, overwriting whatever arrived on the
URL. The symptom is a handover rejected with an empty `participantId` and a participant staring at
an error page, several screens after the thing that caused it.

If a field shows `=` with nothing after it: delete the row, add it again with **Add a New Field**,
type the name, and **do not touch "Set a Value Now"**.

---

## Step 2 — carry the ids across the platform

Survey 2 is a separate response and remembers nothing of Survey 1. Only what is on the URL arrives.

In Survey 1's **Web Service** element, the `callbackUrl` body parameter should read:

```
https://mtecethz.qualtrics.com/jfe/form/SV_3NS5s4iVRF36h38?arm=${e://Field/arm}&arm_label=${e://Field/arm_label}&PROLIFIC_PID=${e://Field/PROLIFIC_PID}&STUDY_ID=${e://Field/STUDY_ID}&SESSION_ID=${e://Field/SESSION_ID}
```

(That `SV_` is Survey 2's — the **Outro** survey. Getting Survey 1's own id in there sends
participants back to the beginning.)

The platform merges `participantId` and `platformArm` into whatever is already on this URL rather
than replacing it, so nothing collides.

`PROLIFIC_PID` looks redundant beside the `participantId` the platform returns. It is not: the two
travel by different routes and should be identical for every participant, so a row where they
differ is a row where the chain crossed two people over — one participant's emotion measures
attached to another's behaviour. `check_join.py` compares them.

---

## Step 3 — screen out anyone arriving without an id

In Survey 1's flow, immediately after the Embedded Data block, add a **Branch**:

> If **Embedded Data** `PROLIFIC_PID` **Is Empty** → **End of Survey**

Someone who opens the survey link directly cannot be paid and cannot be joined to anything. Without
this they go through consent, the bonus explanation and the briefing before hitting an error at the
portal, which looks like a broken study rather than a closed door.

---

## Step 4 — Survey 2's fields

In Survey 2's flow, as the **first** element, a Set Embedded Data with seven fields, every one of
them in the **"Value will be set from Panel or URL"** state:

```
participantId     from the platform
platformArm       from the platform
arm               carried across by Survey 1
arm_label         carried across by Survey 1
PROLIFIC_PID      carried across by Survey 1
STUDY_ID          carried across by Survey 1
SESSION_ID        carried across by Survey 1
```

Then, directly below it, a **Branch**:

> If **Embedded Data** `participantId` **Is Empty** → **End of Survey**

Survey 2's link is live on the internet. Without that branch, anyone who finds it starts at Emotion
T1 having never done the task, and their response looks exactly like a real one.

---

## Step 5 — create the Prolific study

You need the study to exist before Survey 2 can be finished, because the completion URL comes from
Prolific. Create it as a **draft** and do not publish yet.

**New study**, then:

| Setting | What to put |
| --- | --- |
| Title | What a participant sees in the list. Describe the task, not the hypothesis. |
| Description | What they will do and roughly how long. No mention of arms or of what is being measured. |
| Study URL | see below |
| Completion | "URL redirect" / completion code — see Step 6 |
| Devices | Desktop only |

### The study URL

Choose **"I'll use URL parameters"** and give it Survey 1's anonymous link with Prolific's three
placeholders:

```
https://mtecethz.qualtrics.com/jfe/form/SV_835PoG7bRKVwS58?PROLIFIC_PID={{%PROLIFIC_PID%}}&STUDY_ID={{%STUDY_ID%}}&SESSION_ID={{%SESSION_ID%}}
```

Prolific substitutes those per participant. The `{{%…%}}` braces are Prolific's syntax, not
Qualtrics' — type them exactly.

### Desktop only

The portal is a full dashboard with a claims table, and the task card sits in the corner. On a
phone it is a different stimulus from the one everyone else saw. Restrict to desktop.

### Screening

Filter to **United Kingdom** residence and fluent English. The persona is a Manchester
policyholder, the insurer is British and the amounts are in pounds. A participant who has never
held a household contents policy costs a data point and some goodwill.

### Reward and time

Estimate the time from your own pilot rather than guessing. The platform recorded it: on
`<appUrl>/data`, each session row carries `startedAt` and `endedAt`, and that is the portal leg.
Add what the two surveys took — Qualtrics records a duration per response — and round up.

Prolific enforces a minimum hourly rate. Set the reward from your estimate, not the other way
round: a study that pays below the rate will not publish, and one that underestimates the time
collects complaints that are visible to future participants.

---

## Step 6 — the completion URL goes in Survey 2

Prolific gives the draft study a completion URL of the form:

```
https://app.prolific.com/submissions/complete?cc=XXXXXXXX
```

Copy it. In **Survey 2**, open the **End of Survey** element at the bottom of the flow →
**Customize** → **Override Survey Options** → **Redirect to a URL**, and paste it.

It goes on **Survey 2**, not Survey 1. A participant who never reaches it stays unsubmitted and has
to be approved by hand — which, if you get it wrong, means every participant.

---

## Step 7 — pilot it before you publish

Prolific can preview a study without publishing it. Use that, or run the chain once more by hand
with a made-up id:

```
https://mtecethz.qualtrics.com/jfe/form/SV_835PoG7bRKVwS58?PROLIFIC_PID=prolific-dry-run-1&STUDY_ID=x&SESSION_ID=y
```

Walk the whole thing in a private window. You are checking four joints:

1. Consent → briefing → **the portal** (Survey 1's redirect fired)
2. The claim files and the outcome screen appears (the arm fired)
3. Continue → **Survey 2**, and the address bar carries all five parameters
4. Survey 2's end → **Prolific's completion page**

Then `<appUrl>/data` should show exactly one session for that id, and Survey 2's response should
have all seven embedded fields populated — not blank.

**Wipe that run** before publishing: `./scripts/azure_db_reset.sh`, and delete the two responses.

---

## Step 8 — publish small

Publish for a handful of participants first — five is enough — and stop.

Then, before releasing the rest:

```bash
./scripts/check_join.py ~/Downloads/Intro.csv ~/Downloads/Outro.csv ~/Downloads/behavioural-log-*.csv
```

A clean result means the chain holds for real participants on real machines, which is a different
claim from holding for you on yours. A dirty one costs you five participants instead of all of
them.

Look especially for **"On the platform, never reached Survey 2"**. One or two is ordinary
attrition. Several is a broken handback, and every one of them is a person who did the work and
cannot be matched to their own answers.

---

## Step 9 — while it runs

- **Submissions** shows who finished. Approve them; Prolific auto-approves after a few days
  otherwise.
- A participant who contacts you because something broke is worth answering — they are also the
  only source of information about failures that leave no trace in the data.
- Watch the arm balance in `check_join.py`'s output, not the randomiser's. They diverge the moment
  anyone drops out, and the second number is the one your analysis has.

---

## Step 10 — the bonus

**Decide this before you publish, not after.** The briefing promises a task bonus and the arms name
different amounts (GBP 1.08 in S0, GBP 0.06 in S2). Prolific pays the base reward automatically;
bonuses are a separate action you take per submission.

Two defensible positions:

- **Pay the arm's amount.** Then S2 and S4 participants really are paid less for the same work
  because of a condition they were assigned to, and the ethics application has to say so.
- **Pay everyone the full amount at the end**, with the differing figure being part of the
  manipulation only. The debrief then has to tell them so.

What is not defensible is leaving it unstated in the briefing and deciding afterwards.

Prolific takes bonuses as a list of `participant_id,amount`. The participant ids are the
`PROLIFIC_PID` column in Survey 2's export, and the arm is beside it.

---

## When something goes wrong

| What you see | Where it is |
| --- | --- |
| Participants land on the portal's error page | `PROLIFIC_PID` is being wiped in Survey 1 — Step 1. Check the app logs: `az containerapp logs show -g rg-e2-feeling-heard -n pretest-feeling-heard --tail 30` will show the rejected register and why. |
| Survey 2 responses have blank embedded data | Those fields have explicit empty values instead of "set from Panel or URL" — Step 4. |
| Survey 2 has rows with no platform record | Someone opened Survey 2's link directly. Step 4's branch. |
| Nobody is marked complete in Prolific | The completion URL is missing from Survey 2's End of Survey, or it is on Survey 1 by mistake — Step 6. |
| `check_join.py` says the two id copies disagree | The chain crossed two participants over. Stop the study; this corrupts the pairing and is not fixable after the fact. |
| Participants report CHF, or a Zürich address | The deploy did not pick up the content override or the library version — redeploy and check `<appUrl>` in a private window. |
