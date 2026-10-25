# Wiring experiment 2 into Qualtrics

The platform is a stop in the middle of the survey. Qualtrics consents, screens and briefs the
participant, hands them over, and takes them back afterwards for the measures.

```
Survey 1   consent, screening, MICROPHONE PRECHECK, endowment + comprehension, briefing
           assigns the cell, registers it with the platform, redirects
                                  |
Platform   file the claim -> Salvena's assessment -> contact customer service or accept
           -> the AI assistant -> the human employee -> redirects back
                                  |
Survey 2   manipulation checks, felt-heard, continuity, NPS, the bonus, the debrief
```

**Two surveys, not one.** A redirect out of Qualtrics ends the response; there is no way back into
it. So Survey 1 ends at the handover and Survey 2 is a fresh response, joined to the first by the
Prolific id. This is the same arrangement the pre-test runs on, and it works.

Everything below needs the app's address, which is the `appUrl` the deploy prints:

```
https://exp2-feeling-heard.ambitioussmoke-69776fff.switzerlandnorth.azurecontainerapps.io
```

---

## 1. The microphone precheck

**The survey already has one.** A script on a question in Survey 1 builds the whole check itself —
it replaces the question's own contents at load, so there is no HTML to paste and nothing in the
Qualtrics editor to keep in step with it. It is better than a from-scratch version in three ways
worth keeping: it lets them retry after a denial, it gives up after 60 seconds instead of hanging,
and it warns them that the portal will ask again on its own domain.

Leave the script as it is. What it needs is the flow around it, below.

### Two checks, because there are two different failures

**Qualtrics can find out whether a microphone exists and whether they will grant it.** That is
almost everyone who should not start, and finding them here means they are not briefed, not paid
for a session that cannot work, and not holding a confederate's slot.

**Qualtrics cannot grant permission for the platform.** Browsers scope it per site, so a
participant who allows the microphone on qualtrics.com will still be asked again on the platform's
host. That second ask is what the platform's own check handles, and it will usually pass instantly
because the device is already known to work. The script says so on screen, which stops the second
prompt reading as something having gone wrong.

Put the question early — before the endowment, after consent.

### What the script writes

| Field | Values | Set when |
| --- | --- | --- |
| `mic_ok` | `yes` / `no` / empty | empty while testing, `yes` on success, `no` on denial, timeout, an unsupported browser, or the exit button |
| `session_token` | a UUID | once, at load, if not already set |

Both must be declared as Embedded Data **before** this question in the Survey Flow, or the values
do not survive the response and the branch below cannot see them.

`session_token` is not used by the platform — the platform identifies people by `participantId`,
which is the Prolific id. It is a per-response id for your own records. Harmless to keep.

### It advances by itself

On success the script waits 600 ms and clicks Next for the participant. So the question must be
**alone on its page**: anything sharing the page with it gets skipped unanswered. Put a page break
after it, and put the self-report question below on its own page.

### The script

`qualtrics/mic-check.js` in this folder is the version in force — paste the whole file into the
question's JavaScript editor, replacing everything Qualtrics pre-fills there.

**Test it on the published link, never in preview.** Preview renders the survey in an iframe, and a
browser refuses microphone access to an iframe that was not given `allow="microphone"` — so in
preview the check fails whatever the participant clicks. The script detects that and says so on
screen, which is the difference between a confusing afternoon and a one-line answer.

It differs from what was pasted into the survey first in one way: Next is hidden until the check
runs and the exit button only appears after a failure, so a participant who never clicked *Test
microphone* had no way forward at all. The file reveals the exit button after 20 seconds. Twenty
seconds of a page with no button is long enough that people close the tab, and a closed tab is a
timed-out Prolific slot rather than a return.

Keeping it here rather than only in Qualtrics means a change to it is reviewable and the survey can
be rebuilt from the repository if it is ever lost.

### Then branch on it

After the question, a **Branch**: if `mic_ok` **is not equal to** `yes`, send them to an End of
Survey with no completion code and a message asking them to return the study on Prolific. They
should be returned rather than run — a participant who cannot speak cannot produce the data this
study is about.

Nest the End of Survey *inside* the branch. Beside it instead of inside it, and everyone ends
there.

Count them. The share who fail here is the share of your sample that cannot do voice work at all,
and you need it to know how far past 400 to recruit.

### Why a self-report question is still needed

"Do you have a working microphone?" is answered yes by people whose microphone is muted, by people
in an in-app browser that cannot reach one, and by people who will refuse the prompt when it
appears. Asking the browser costs the same screen and produces a fact instead of an intention —
which is what the script does.

But the browser cannot see the room, so ask one thing it cannot know:

> "Are you somewhere quiet where you can speak out loud for about five minutes?"

A No there is also a screen-out. A participant on a train can grant a microphone perfectly well —
and the transcription will turn the background into sentences they never said.
---

## 2. Survey 1 — up to the handover

### a. Embedded Data (first element in the flow)

Declare these so they exist before anything sets them:

```
mic_ok         (leave blank — the precheck sets it)
session_token  (leave blank — the precheck mints it)
aiAck          (leave blank)
humanAck       (leave blank)
cell           (leave blank)
handoverToken  (leave blank)
PROLIFIC_PID   (leave blank — Prolific sets it from the URL)
STUDY_ID       (leave blank)
SESSION_ID     (leave blank)
```

### b. Randomizer — the 2×2

Add a **Randomizer**, set it to randomly present **1** element, and tick **Evenly Present
Elements**. Inside it, four Embedded Data elements, each setting all three fields:

| Element | `aiAck` | `humanAck` | `cell` |
| --- | --- | --- | --- |
| 1 | `true` | `true` | `AI+/H+` |
| 2 | `true` | `false` | `AI+/H-` |
| 3 | `false` | `true` | `AI-/H+` |
| 4 | `false` | `false` | `AI-/H-` |

**Evenly Present Elements is not optional.** Without it Qualtrics randomises independently per
respondent and the four cells drift apart. At N = 400 that drift is big enough to cost you power in
the interaction term, which is the effect the whole design exists to measure.

`cell` is for your own analysis and display logic. The platform never sees it — it takes the two
factors and derives its own label — so the two are independent records of the same assignment and
disagreeing is informative.

### c. Web Service

Add a **Web Service** element, after the randomiser and before the End of Survey.

- **URL**: `<appUrl>/api/handover`
- **Method**: `POST`
- **Body**: set the type dropdown to `application/json`, then five parameters, each of type
  **String**:

| Parameter | Value |
| --- | --- |
| `participantId` | `${e://Field/PROLIFIC_PID}` |
| `aiAck` | `${e://Field/aiAck}` |
| `humanAck` | `${e://Field/humanAck}` |
| `name` | `Joe Smith` |
| `callbackUrl` | Survey 2's anonymous link, plus the parameters below |

String is right even though the two factors are booleans: Qualtrics types everything as a string.
The platform accepts `"true"` / `"false"` and `"1"` / `"0"`, so a randomiser built on either
convention works and you do not have to rewrite one that already exists.

What it does **not** accept is an empty string — what an embedded-data field that never got set
sends — or anything else. Those are a 400 naming the factor, rather than a participant quietly
dropped into a cell nobody assigned. `QualtricsRegisterFormatTest` pins all of it.

- **Header**: `X-Handover-Secret: <the HANDOVER_SECRET you deployed with>`
- **Set Embedded Data**: map the response field `token` → `handoverToken`

### The callbackUrl

This is the whole trick: the platform sends the participant onward to wherever this points, so
Survey 1 decides where the chain goes next and the platform knows nothing about your survey.

Survey 2 is a separate response and remembers nothing of Survey 1 — not the cell, not the Prolific
ids. Put them on the URL and they survive:

```
https://mtecethz.eu.qualtrics.com/jfe/form/SV_XXXXXXXX?aiAck=${e://Field/aiAck}&humanAck=${e://Field/humanAck}&cell=${e://Field/cell}&PROLIFIC_PID=${e://Field/PROLIFIC_PID}&STUDY_ID=${e://Field/STUDY_ID}&SESSION_ID=${e://Field/SESSION_ID}
```

The platform **merges** its own parameters into this rather than replacing them, and writes only
two names — `participantId` and `escalated` — so nothing here collides.

Carrying `PROLIFIC_PID` looks redundant, since `participantId` comes back from the platform
carrying the same value. It is not: `STUDY_ID` and `SESSION_ID` have no other route at all, and
having the id arrive by two independent paths makes each a check on the other. A row where they
disagree is a row where something went wrong in between, which is otherwise invisible — either one
alone looks perfectly reasonable.

**The cell is registered server-side and never travels in a URL.** What the participant's browser
carries is an opaque token that means nothing on its own, so nobody can read or choose their
condition.

### d. End of Survey — redirect

**Customize** → **Redirect to a URL**:

```
<appUrl>/?t=${e://Field/handoverToken}
```

### When the portal says "no handover token in the URL"

The address bar ends in a bare `?t=`. The redirect is correct and `handoverToken` was empty when
Qualtrics built it. Open the response in Data & Analysis and read three fields:

| `aiAck` / `humanAck` | `handoverToken` | What happened |
| --- | --- | --- |
| blank | blank | the Web Service never ran — preview, or it sits below the End of Survey |
| filled | blank | it ran and failed, so there was no token to write |

A declared field with no value is **absent from the response's embedded-data view**, not shown as
blank — so `handoverToken` missing from that list is the symptom, and says nothing about whether it
was declared. Check the Survey Flow for that, not the response.

**Preview is the usual answer.** Qualtrics does not fire Web Service elements there. Test the
handover on the published anonymous link.

**The order in the flow is load-bearing**, top to bottom: Embedded Data, then the blocks, then the
Randomizer, then the Web Service, then the End of Survey. A randomiser below the Web Service means
the factors are empty at the moment of the call, which is a 400 and then an empty `?t=` — the same
symptom from a different cause.

---

## 3. Survey 2 — the measures

### a. Embedded Data (first element in the flow)

Declare these and set each to **"Value will be set from Panel or URL"** — do **not** type anything
after the `=`:

```
participantId
escalated
aiAck
humanAck
cell
PROLIFIC_PID
STUDY_ID
SESSION_ID
```

An explicit empty value here wipes what the URL carried. That cost the pre-test an afternoon.

### b. Branch on `escalated`

This is new, and it is not optional.

`escalated` is `true` if the participant asked customer service to review the assessment, `false`
if they accepted it and finished. **A participant who accepted never met either agent**, so they
are in no cell of the 2×2.

Add a **Branch** at the top of Survey 2: if `escalated` **is not equal to** `true`, skip the
manipulation checks, the felt-heard block and the continuity block, and go straight to the bonus
and debrief. Asking someone how the employee made them feel, when there was no employee, produces
answers you cannot interpret and a participant who thinks the study is broken.

How many decline is unknown until the pilot. It is also the number that decides how far past 400
you have to recruit, so make sure it is easy to count.

### c. Screen out anyone who never did the task

A missing `participantId` means they never reached the platform. Branch those to an End of Survey
without a completion code, the same way the pre-test does.

---

## 4. What to delete from the survey as it stands

The platform now owns the middle of the study. These blocks have to go, or participants will be
asked the same things twice and shown an assessment they have already been given:

- **Initial Claim Outcome** — the platform shows the assessment, with the same figures and the same
  wording
- **Customer Service Review** — the platform's own screen offers the review
- **AI Voice Assistant** and **Human Handover** — both happen inside the one platform visit, so
  there is nothing to redirect to and no second `RESEARCHER SETUP REQUIRED` block

The **emotion check** no longer has anywhere to sit between the outcome and the dispute decision,
because both are on the platform now. Move it after the call with the other measures, or drop it —
it was marked *"separate pre-test"* in the survey anyway.

The wording fixes that are still outstanding — the debrief saying 900, the endowment sentence that
contradicts it — are listed in `docs/experiment-2/qualtrics-copy-fixes.md`.

---

## 5. Prolific

Unchanged from the pre-test: `pre-test/PROLIFIC.md` covers the study URL, the completion code, the
screening and the bonus. The only difference is that the bonus is a flat GBP 1.08 for everyone who
completed, because the final reimbursement is standardised.

---

## 6. Checking it before you launch

Run one participant through by hand, in a browser, with the survey in preview **off** — previews do
not fire web service elements the same way.

What to check at each stage:

1. **Survey 1 finishes and the browser lands on the Salvena portal**, not an error page. "Could not
   start from the study handover" means the web service did not return a token: check the secret
   and look at the Web Service element's response in the Qualtrics flow.
2. **`<appUrl>/data`** shows the session with the cell you expect.
3. **The redirect back carries both parameters** — look at the address bar on Survey 2 and check
   for `participantId=` and `escalated=`.
4. **Survey 2's embedded data is populated**, not blank. If it is blank, the fields were declared
   with an explicit empty value rather than "set from Panel or URL".
5. **Do it again, accepting the assessment instead**, and confirm Survey 2 skips the agent questions.

Then do all four cells. `./scripts/pilot.sh --list` names them; the survey should produce each one
roughly a quarter of the time.
