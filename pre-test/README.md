# Feeling-heard pre-test

The platform half of `Feeling-heard_Study1_pre-test-2`. Qualtrics runs the survey; this runs the
bit the participant does on the Salvena website — filing a claim and seeing what happens to it.

## What the pre-test is for

It compares four candidate service situations to find one that reliably produces the affect the
later customer-service study needs. Everyone files the same claim; only the outcome differs.

| Arm | What the participant is shown |
| --- | --- |
| **S0** Control | The claim is approved as expected: 1,080 of 1,200 (GBP 1.08). |
| **S1** Anger | Only 5% is eligible — 60 (GBP 0.06). Documentation complete, damage not disputed, **no explanation given**. |
| **S2** Worry | 60 (GBP 0.06) *provisional*. A policy limitation *may* apply, passed to a specialist team, **no timeline**. |
| **S4** Irritation | Submission fails with E-4092, the form comes back empty, **and fails again**. No reimbursement determined. |

There is no S3. The arms are whatever `configuration/arms/*.json` defines, so that is a naming
choice rather than a gap in the code — but if an S3 was intended, it is missing.

## Where the line falls

Qualtrics keeps consent, the bonus explanation, the scenario briefing, both emotion measures, the
filler task, the appraisals, the funnel checks, demographics and the debrief.

The platform owns everything the participant does *on the website*. They land on the **real Salvena
portal** — dashboard, policies, claims, navigation — find their way to the claim form, choose a
policy and file the claim there. It is the portal's own form, writing a real claim to the portal's
own claims engine.

```
Qualtrics  consent → bonus → briefing
                ↓  POST /api/handover  {participantId, arm, name, callbackUrl} → token
                ↓  redirect to  /?t=<token>
Portal     dashboard → navigate → file a claim (the portal's own form) → submit
                ↓  the portal reports what happened; the study takes over
Study      outcome screen  →  [S4: retry → back into the portal's form → submit → dead end]
                ↓  Continue: redirect to callbackUrl?participantId=…
Qualtrics  Emotion T1 → filler → Emotion T2 → appraisals → checks → demographics → debrief
```

## Three decisions worth knowing about

**The outcome is scripted, not produced by the portal's claim engine.** The portal models claims
properly — policies, coverage tables, SUBMITTED → IN_REVIEW → SETTLED. It would never approve 5%
without explanation, and it would certainly never fail twice with the same error code. Those
outcomes are apparatus, so they live here and are read from `configuration/arms/`. The portal files
the claim; what Salvena then *says* about it is the study's.

**The failure is injected in front of the portal, not inside it.** `ClaimAttemptFilter` sits ahead
of the portal's own claim endpoint and, in S4, refuses the request before it arrives. The portal's
claim service is untouched and still behaves correctly — which matters, because Experiment 3 uses
the same library and must not inherit a portal that has been taught to fail. A refused submission
saves no claim, exactly as the arm's own words promise ("the information you entered was not
saved"); there is a test for that.

**The attempt count lives on the server.** In S4 the portal must fail exactly twice, and the number
of failures *is* the irritation manipulation. Counting in the browser would let a reload, a back
button or a second tab hand the participant a different number of failures than the arm specifies.
The count is derived from the logged attempts, so it survives anything done to the page. For the
same reason the behavioural session is opened once per run and not once per page: a second session
would reset that count mid-arm.

## Configuration

`configuration/portal-seed/` is the participant's account. Two active policies and **no claims** —
someone about to report their first burst pipe should not find the portal already handling two
others — and the household policy's coverage table states the 90% the briefing tells them to
expect, so a participant who checks finds it. The persona is British; the portal's shipped fixtures
are Swiss.

`configuration/arms/*.json` holds every word each arm shows — banner, detail, retry notice,
assessment rows, body paragraphs. Rewording an arm is a file edit and a restart. Tuning that
wording is most of what a pre-test produces, so nothing in it is compiled in.

Arms are loaded at **startup**, not per request: an arm edited midway through a live run would put
participants before and after the edit in silently different conditions under the same arm name.
A restart is a visible event; a hot reload is not.

## Prerequisites

- Docker Desktop · JDK 21 · Node 22+ / npm 10+

## Running it

The portal is a dependency, not a module. Start its registries from the portal repository:

```
..\mtec-insurance-portal-core> docker compose -f docker/maven-registry.yaml up -d   # :8082
..\mtec-insurance-portal-core> docker compose -f docker/npm-registry.yaml   up -d   # :4873
```

Then, from here:

```
docker compose -f docker/postgres.yaml up -d     # → localhost:5432, db pretest_feeling_heard
.\pretest-app>      .\mvnw.cmd spring-boot:run   # :8080
.\pretest-frontend> npm run dev                  # :5173
```

There is no direct-URL path into the study: the token is the only accepted handover, and an unknown
or expired one fails loud rather than opening a session. Register one first:

```
curl -X POST http://localhost:8080/api/handover \
  -H 'Content-Type: application/json' \
  -d '{"participantId":"test-1","arm":"S1","name":"Alex Morgan",
       "callbackUrl":"http://localhost:5173/done"}'
```

then open `http://localhost:5173/?t=<token>`.

Researcher tools: `/data` for the behavioural log and CSV export; `showAdmin()` in the browser
console reveals the reset menu.

## Tests

```
.\pretest-app> .\mvnw.cmd test
```

`ClaimFlowTest` runs against the real `configuration/arms/` files rather than fixtures — it exists
to catch an edit to the arms as much as a regression in the code. The test that matters most is
`irritationArmFailsTwiceThenStops`, with `reloadingMidFailureResumesRatherThanRestarting` behind it.

## Open questions

- **The Qualtrics briefing describes a different form.** It lists *Type of damage, Cause, Date of
  incident, Estimated damage*. The portal's real form is *Policy, Type, Incident date, Amount
  claimed, Description* — there is no Cause field, and choosing the right policy is a step the
  briefing does not mention. The briefing needs rewording to match what the participant will
  actually see, or people will hunt for a field that is not there.
- **The portal is visibly Swiss.** The logo reads "Salvena Versicherungen AG", the footer gives a
  Zürich address and a +41 number, and a news item announces a service centre in Zürich. The
  policies are now seeded in GBP, but claim amounts still render with the CHF default
  (`money(claim.amount)` in three portal pages passes no currency) and the thousands separator is
  the Swiss apostrophe. Branding and news are overridable via `CONTENT_LOCATION`; the currency
  defaults are a small change in the portal library, which Experiment 3 shares.
- **GBP 1.00 vs GBP 1.08.** The Qualtrics bonus block calls it "a provisional GBP £1.00 task
  balance", but the expected reimbursement is 1,080 scenario pounds = GBP 1.08, and the debrief
  pays GBP 1.08.
- **The incident date.** The briefing says "yesterday" and the survey's form shows `09/20/2026`.
  A fixed date stops meaning "yesterday" the day after the pre-test runs. The portal uses a native
  date input, so its display format follows the participant's own browser locale.
- **The portal discards server error messages.** Its API client throws
  `new Error(status + " " + statusText)` without reading the body, so the arm's E-4092 wording
  cannot reach the portal's own inline alert. The participant reads it on the study's screen a
  moment later instead. Worth knowing for Experiment 2, where a portal-rendered error may matter
  more.
