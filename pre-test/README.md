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

The platform owns everything the participant does *on the website*: the claim form, the submission,
the outcome, and — in S4 — the failure and the forced retry.

```
Qualtrics  consent → bonus → briefing
                ↓  POST /api/handover  {participantId, arm, name, callbackUrl} → token
                ↓  redirect to  /?t=<token>
Platform   claim form → submit → [S4: error → form again → submit] → outcome → Continue
                ↓  redirect to callbackUrl?participantId=…
Qualtrics  Emotion T1 → filler → Emotion T2 → appraisals → checks → demographics → debrief
```

## Two decisions worth knowing about

**The outcome is scripted, not produced by the portal's claim engine.** The portal models claims
properly — policies, coverage tables, SUBMITTED → IN_REVIEW → SETTLED. It would never approve 5%
without explanation, and it would certainly never fail twice with the same error code. Those
outcomes are apparatus, so they live here and are read from `configuration/arms/`. The portal
supplies the look and the event sink; it is not taught to lie about its own claims.

**The attempt count lives on the server.** In S4 the portal must fail exactly twice, and the number
of failures *is* the irritation manipulation. Counting in the browser would let a reload, a back
button or a second tab hand the participant a different number of failures than the arm specifies.
The count is derived from the logged attempts, so it survives anything done to the page.

## Configuration

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

- **Is the bare flow right?** The survey mockups show one header and one panel — no dashboard, no
  policy list, no navigation. That is what is built. If participants should instead land in the
  full Salvena portal and find the claim form themselves, that is a different build.
- **The portal is Swiss.** `format.ts` and the two PDF services render CHF. Nothing in the
  pre-test's own screens uses them, because the arm files carry their own strings — but the moment
  a real portal page is shown, the participant sees CHF in a study briefed in scenario pounds.
- **GBP 1.00 vs GBP 1.08.** The Qualtrics bonus block calls it "a provisional GBP £1.00 task
  balance", but the expected reimbursement is 1,080 scenario pounds = GBP 1.08, and the debrief
  pays GBP 1.08.
- **The incident date.** The briefing says "yesterday" and the survey's form shows `09/20/2026`.
  A fixed date will stop meaning "yesterday" the day after the pre-test runs; the form's reminder
  box here says "Yesterday" instead.
