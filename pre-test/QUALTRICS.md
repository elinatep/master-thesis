# Wiring the pre-test into Qualtrics

Everything in the survey stays where it is. Two things get added: a randomiser that assigns the
arm, and a question that embeds the platform.

The participant never leaves the survey — the platform runs in an iframe inside one question, and
tells the survey when it is finished. That matters more than it sounds: **Emotion T1 has to be the
very next thing the participant sees after the claim outcome**, and any route that sends them out
to another URL and back risks losing them between the two.

---

## 1. Survey Flow

Add these above the block that will hold the platform.

### a. Embedded Data (first element in the flow)

Declare the fields so they exist before anything sets them:

```
arm            (leave blank)
handoverToken  (leave blank)
PROLIFIC_PID   (leave blank — Prolific sets it from the URL)
```

### b. Randomizer

**Add a Randomizer**, set it to **randomly present 1** element, and tick **Evenly Present
Elements**. Inside it, four Embedded Data elements:

| Element | Sets |
| --- | --- |
| 1 | `arm` = `S0` |
| 2 | `arm` = `S1` |
| 3 | `arm` = `S2` |
| 4 | `arm` = `S4` |

"Evenly Present Elements" is what balances the cells. Without it Qualtrics randomises
independently per respondent and the arms drift apart.

### c. Web Service

**Add a Web Service** element, after the randomiser and before the platform block.

- **URL**: `https://<app-fqdn>/api/handover`
- **Method**: `POST`
- **Body**: JSON

```json
{
  "participantId": "${e://Field/PROLIFIC_PID}",
  "arm": "${e://Field/arm}",
  "name": "Joe Smith",
  "callbackUrl": "https://<your-qualtrics-domain>"
}
```

- **Header**: `X-Handover-Secret: <the HANDOVER_SECRET you deployed with>`
- **Set Embedded Data**: map the response field `token` → `handoverToken`

`callbackUrl` is not used for navigation in the embedded setup, but it **is** used as the origin
the platform will post its completion message to. Set it to your Qualtrics domain
(`https://mtecethz.eu.qualtrics.com`) or the message will be refused by the browser.

> If the web service call fails, `handoverToken` stays empty and the participant meets a
> fail-loud error instead of the portal. That is deliberate — better than a participant running in
> no arm at all. Pilot this step first.

---

## 2. The platform block

One **Text / Graphic** question. Switch the editor to **HTML view** and paste:

```html
<div id="salvena-wrap">
  <iframe id="salvena"
          src="https://<app-fqdn>/?t=${e://Field/handoverToken}"
          style="width:100%;height:780px;border:1px solid #ccc"
          title="Salvena customer portal"></iframe>
</div>
```

Then open **JavaScript** on the same question and paste:

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

Hiding the Next button is what keeps the participant in the task: there is no way past this page
until the platform says the arm is finished. `platformParticipantId` and `platformArm` come back
from the platform itself, so you can check in the data that the arm Qualtrics assigned is the arm
the participant actually saw — declare both as Embedded Data in the flow first.

Give the block **its own page** (nothing else on it), and set the question to full width if your
survey theme is narrow. The portal needs roughly 1000px to look like a real website rather than a
phone app.

---

## 3. Order in the flow

```
Information / Consent
Task Bonus / Comprehension
Scenario Briefing
  ↓ Embedded Data · Randomizer (arm) · Web Service (handoverToken)
[ the platform block — iframe ]          ← participant files the claim, sees the outcome
Emotion T1
Filler / Delay
Emotion T2
Post-delay Motivation / Realism / Appraisals
Checks / Funnel Debrief
Demographics
Final Bonus / Debrief
```

---

## 4. Before you launch

- **Set `HANDOVER_SECRET`.** Without it anyone who finds the URL can mint a token and enter the
  study in whichever arm they choose, and those responses look exactly like real ones.
- **Easy Auth must be off.** It puts an Entra login in front of everything, which blocks anonymous
  Prolific participants and breaks the iframe.
- **Pilot all four arms yourself** through the real survey link, then open `https://<app-fqdn>/data`
  and check the events look right — especially an S4 run, which must show exactly two
  `CLAIM_ATTEMPT` rows under one session.
- **Check the briefing matches the portal's form.** The briefing lists *Type of damage, Cause, Date
  of incident, Estimated damage*; the portal's real form is *Policy, Type, Incident date, Amount
  claimed, Description*. There is no Cause field, and picking the right policy is a step the
  briefing does not mention.

## If the iframe will not load

The app sends no `X-Frame-Options` or CSP header, so it is embeddable by design. If a frame stays
blank, in order: Easy Auth is on; the handover token is empty (the web service failed); or the
survey is being previewed over `http` while the app is `https`.
