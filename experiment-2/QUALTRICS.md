# Wiring experiment 2 into Qualtrics

The platform is a stop in the middle of the survey. Qualtrics consents, screens and briefs the
participant, hands them over, and takes them back afterwards for the measures.

```
Survey 1   consent, screening, endowment + comprehension, scenario briefing
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

## 1. Survey 1 — up to the handover

### a. Embedded Data (first element in the flow)

Declare these so they exist before anything sets them:

```
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

String is right even though the two factors are booleans: Qualtrics types everything as a string
and the platform accepts `"true"` / `"false"`. `QualtricsRegisterFormatTest` pins that down,
including that an **empty** string is rejected — so a randomiser branch that failed to fire gives a
400 here rather than a participant quietly dropped into a cell nobody assigned.

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

---

## 2. Survey 2 — the measures

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

## 3. What to delete from the survey as it stands

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

## 3b. The microphone check in Qualtrics

The platform checks the microphone at the moment the call starts, which is the only place that can:
permission is per-origin, so granting it to Qualtrics says nothing about the platform's host.

What Qualtrics should do instead is screen out, **before the endowment**, the people who cannot do
this at all — so they are not briefed, not paid for an unusable session, and not left having done
ten minutes of work for a call that could never happen.

Two questions, early, before consent or straight after it:

- **"Are you using a laptop or desktop computer with a working microphone, in a quiet place where
  you can speak out loud for about five minutes?"** Yes / No. A No is a screen-out, not a branch:
  they should be returned on Prolific rather than run.
- **"Which browser are you using?"** — or read it from the user agent. Chrome, Edge, Safari and
  Firefox all do what is needed; an in-app browser (opening the link inside another app) often does
  not, and that is a common way to arrive.

Both are cheap, and neither replaces the platform's own check. They catch different failures: this
one catches people who should not start, and the platform's catches the microphone that is present
but muted, pointed at the wrong device, or refused when asked.

---

## 4. Prolific

Unchanged from the pre-test: `pre-test/PROLIFIC.md` covers the study URL, the completion code, the
screening and the bonus. The only difference is that the bonus is a flat GBP 1.08 for everyone who
completed, because the final reimbursement is standardised.

---

## 5. Checking it before you launch

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
