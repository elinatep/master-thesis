# Wiring the pre-test into Qualtrics

There are two ways to put the platform in front of a participant, and the choice is not cosmetic.

**The redirect chain (recommended).** Two surveys. Survey 1 ends by redirecting to the platform;
the platform ends by redirecting to Survey 2. The participant gets the whole browser window for
the task.

**The iframe.** One survey, with the platform embedded in a question that hides the Next button
until the task reports finished.

Use the redirect chain. The reason is the thing this study is actually measuring: the participant
has to believe they are on an insurance company's website. Salvena is a full dashboard — navbar,
policy cards, a claims table — and a Qualtrics question column is around 750px wide with survey
chrome above and below it. Embedded, it reads as a screenshot of a website inside a questionnaire.
Given a window, it reads as a website.

The cost of redirecting is that the post-measures live in a second response and have to be joined
to the first. That is a known, checkable cost. An unconvincing manipulation is neither.

> The argument against redirecting is that Emotion T1 must come immediately after the outcome, and
> sending the participant out to another URL puts a page load in between. That is true, and it is
> smaller than it sounds: advancing a Qualtrics block is itself a full page load, so the embedded
> route has one too. The difference is a DNS lookup, not a change of activity.

---

# Option A — the redirect chain

```
Prolific  →  Survey 1  →  Salvena  →  Survey 2  →  Prolific completion
             consent        the task    emotion T1
             briefing                   filler, T2
             randomiser                 checks, debrief
```

Survey 1 cannot be returned to. A Qualtrics response ends when it redirects out, and there is no
way back into the middle of it — which is exactly why this is two surveys rather than one survey
the participant leaves and comes back to.

## 1. Survey 1 — up to the handover

### a. Embedded Data (first element in the flow)

Declare these so they exist before anything sets them:

```
arm            (leave blank)
handoverToken  (leave blank)
PROLIFIC_PID   (leave blank — Prolific sets it from the URL)
```

### b. Randomizer

Add a **Randomizer**, set it to randomly present **1** element, and tick **Evenly Present
Elements**. Inside it, four Embedded Data elements:

| Element | Sets |
| --- | --- |
| 1 | `arm` = `S0` |
| 2 | `arm` = `S1` |
| 3 | `arm` = `S2` |
| 4 | `arm` = `S4` |

**Evenly Present Elements** is what balances the cells. Without it Qualtrics randomises
independently per respondent and the arms drift apart — with 60 participants that drift is easily
large enough to matter.

### c. Web Service

Add a **Web Service** element, after the randomiser and before the End of Survey.

- **URL**: `https://<app-fqdn>/api/handover`
- **Method**: `POST`
- **Body**: JSON

```json
{
  "participantId": "${e://Field/PROLIFIC_PID}",
  "arm": "${e://Field/arm}",
  "name": "Joe Smith",
  "callbackUrl": "https://mtecethz.eu.qualtrics.com/jfe/form/SV_XXXXXXXX"
}
```

- **Header**: `X-Handover-Secret: <the HANDOVER_SECRET you deployed with>`
- **Set Embedded Data**: map the response field `token` → `handoverToken`

`callbackUrl` is **Survey 2's anonymous link**. This is the whole trick: the platform sends the
participant onward to wherever this points, so Survey 1 decides where the chain goes next without
the platform knowing anything about your survey.

The arm is registered here, server-side, and never travels in a URL. What the participant's browser
carries is an opaque token that means nothing on its own.

### d. End of Survey — redirect

On Survey 1's **End of Survey** element: **Customize** → **Redirect to a URL**:

```
https://<app-fqdn>/?t=${e://Field/handoverToken}
```

## 2. Survey 2 — the post-measures

### a. Embedded Data (first element in the flow)

```
participantId  (leave blank)
arm            (leave blank)
```

Qualtrics fills these from the query string automatically, as long as the names match exactly.
The platform appends both when it hands the participant on:

```
…/SV_XXXXXXXX?participantId=<PROLIFIC_PID>&arm=S4
```

`participantId` is your join key back to Survey 1 and to the platform's own data — it is the same
Prolific ID all the way through. `arm` is a manipulation check: it lets you confirm in the Qualtrics
data alone that the arm assigned in Survey 1 is the arm the participant actually saw.

Treat `arm` as a check, not as truth. It has been through the participant's browser. The
authoritative record is the handover row in the platform database, which only ever existed
server-side.

### b. Screen out anyone who did not do the task

Add a **Branch** at the top of Survey 2's flow:

> If `participantId` **Is Empty** → **End of Survey** (screened out)

Survey 2's link is a live URL. Without this, anyone who opens it directly lands straight into the
emotion measures with no task behind them, and their response looks like everyone else's.

This catches the accidental case. It does not stop someone who deliberately reconstructs the URL —
for that, the join to the platform data is your real defence: only count participants whose session
in `behavioural.study_session` has `completed = true`. If you want it enforced rather than checked
after the fact, say so and I will add an endpoint Survey 2 can call to verify a completion.

### c. End of Survey — back to Prolific

Redirect to your Prolific completion URL as usual.

---

# Option B — the iframe

Keep this if you decide the immediacy of Emotion T1 outweighs the realism of a full window.

One **Text / Graphic** question, on its own page. Switch the editor to **HTML view**:

```html
<iframe id="salvena"
        src="https://<app-fqdn>/?t=${e://Field/handoverToken}"
        style="width:100%;height:780px;border:1px solid #ccc"
        title="Salvena customer portal"></iframe>
```

Then **JavaScript** on the same question:

```js
Qualtrics.SurveyEngine.addOnload(function () {
  var q = this;
  q.hideNextButton();

  window.addEventListener('message', function (e) {
    // Only trust messages from the platform's own origin.
    if (e.origin !== 'https://<app-fqdn>') return;
    if (!e.data || e.data.source !== 'salvena-pretest') return;
    if (e.data.type !== 'salvena-pretest:complete') return;

    Qualtrics.SurveyEngine.setEmbeddedData('platformParticipantId', e.data.participantId);
    Qualtrics.SurveyEngine.setEmbeddedData('platformArm', e.data.arm);
    q.clickNextButton();
  });
});
```

Hiding the Next button is what keeps the participant in the task: there is no way past the page
until the platform says the arm is finished. Declare `platformParticipantId` and `platformArm` as
Embedded Data in the flow first.

In this setup `callbackUrl` is not used for navigation — but it **is** used as the origin the
platform posts its completion message to. Set it to `https://mtecethz.eu.qualtrics.com` or the
browser refuses the message and the Next button never appears.

The platform detects which mode it is in on its own (`window.self !== window.top`), so the same
deployment serves both. Nothing needs rebuilding to switch.

---

## Order across the two surveys

```
SURVEY 1
  Information / Consent
  Task Bonus / Comprehension
  Scenario Briefing
  → Randomizer (arm) · Web Service (handoverToken)
  → End of Survey: redirect to https://<app-fqdn>/?t=…

SALVENA                      ← files the claim, sees the arm's outcome, clicks finish

SURVEY 2
  → Embedded Data: participantId, arm  (from the URL)
  → Branch: participantId empty → screen out
  Emotion T1
  Filler / Delay
  Emotion T2
  Post-delay Motivation / Realism / Appraisals
  Checks / Funnel Debrief
  Demographics
  Final Bonus / Debrief
  → End of Survey: redirect to Prolific
```

---

## Before you launch

- **Set `HANDOVER_SECRET`.** Without it anyone who finds the platform URL can mint a token and
  enter in whichever arm they choose, and those responses look exactly like real ones.
- **Easy Auth must be off.** It puts an Entra login in front of everything, which blocks anonymous
  Prolific participants.
- **Pilot all four arms through the real Survey 1 link**, not just `./scripts/pilot.sh` — the
  script proves the platform works, not that Qualtrics is wired to it. Then check
  `https://<app-fqdn>/data`, especially an S4 run: exactly two `CLAIM_ATTEMPT` rows under one
  session.
- **Check both Qualtrics exports join.** Survey 1's `PROLIFIC_PID` must equal Survey 2's
  `participantId` for every pilot run. Find that out on four responses, not four hundred.
- **Check the briefing matches the portal's form.** The briefing lists *Type of damage, Cause, Date
  of incident, Estimated damage*; the portal's real form is *Policy, Type, Incident date, Amount
  claimed, Description*. There is no Cause field, and picking the right policy is a step the
  briefing does not mention.

## When something does not work

| What you see | Why |
| --- | --- |
| Fail-loud error instead of the portal | `handoverToken` is empty — the Web Service call failed. Check the secret and the URL. |
| `401` from the Web Service | The `X-Handover-Secret` header does not match what was deployed. Redeploy after changing it in `infra/.env`. |
| `400` from the Web Service | The arm is not one the app loaded. It knows `S0 S1 S2 S4` — there is no `S3`. |
| Survey 2 opens with `participantId` empty | The name in Survey 2's Embedded Data does not match the query parameter exactly, or `callbackUrl` in the Web Service body is not Survey 2's link. |
| Iframe stays blank | Easy Auth is on, the token is empty, or the survey is being previewed over `http` while the app is `https`. The app sends no `X-Frame-Options` or CSP header, so it is embeddable by design. |
