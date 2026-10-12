# Qualtrics copy that contradicts the flow (`SV_0vNMcH7s9iawN14`)

Read off the print preview of 12 Oct 2026. The survey now runs on **1,200 claimed / 90% indicated /
60 approved / 1,080 final**. Several blocks still carry the amounts from the earlier version
(**1,000 / 25 / 900**), and the debrief carries a sentence from a design where the bonus depended on
winning the dispute.

These are Qualtrics edits, not code. Nothing in the repository reads them.

Ordered by how much damage each one does.

---

## 1. The debrief states the wrong final amount — **blocking**

Block **Debrief**, first paragraph.

> The initial reimbursement was deliberately set below the expected amount. The final reimbursement
> was standardized at ~~900~~ scenario pounds across conditions and was not determined by what you
> said.

Replace `900` with `1,080`. The *Final Task Bonus* screen shown one page earlier says
`Final reimbursement: 1080 scenario pounds` / `GBP 1.08`. As it stands the study tells the
participant two different numbers within two screens, which reads as a payment error and is the kind
of thing that comes back as a Prolific message rather than a complaint.

## 2. "If you successfully disputed your case, you retained your endowment" — **blocking**

Second debrief block, last paragraph.

> If you successfully disputed your case, you retained your endowment. Endowments will be paid via
> the Prolific platform within 30 days.

This directly contradicts the paragraph above it ("was not determined by what you said"), and it is
the more consequential direction to be wrong in: it tells the participant their pay was
performance-contingent when it was not. Someone who believes they *failed* the dispute will think
they lost money.

Replace with:

> Everyone who completed the study receives the same final reimbursement of 1,080 scenario pounds,
> which corresponds to a bonus of GBP 1.08. This does not depend on how the conversation went. The
> bonus is paid through Prolific.

Keep whatever payment timescale you actually commit to, and make it the same one that is in the
ethics application.

## 3. "check the status of your claim" — they file one

Second debrief block, first paragraph.

> We then briefed you that you needed to ~~check the status of~~ **file** your claim on the website.
> Upon ~~checking this claim~~ **receiving the assessment**, you saw that only a small fraction of
> the money (~~25~~ **60**) scenario pounds would be reimbursed.

Two errors in one sentence: the task is filing a claim in the portal, not checking one, and the
initial award is 60, not 25.

## 4. The two debrief blocks should be one

There are currently two, and together they say the final amount twice, the deception twice, and the
payment arrangement twice — in different words each time. Suggested single version, which keeps
every disclosure the ethics application requires:

> **Debrief**
>
> Thank you. The insurer, the claim and the assessment were fictional.
>
> You were briefed to file a claim with Salvena for 1,200 scenario pounds, with 90% of this type of
> water damage indicated as covered, so you expected 1,080. The assessment then returned 60. That
> initial decision was deliberately set below the expected amount, and no real assessment took
> place.
>
> We varied aspects of how the AI assistant and the customer-service employee responded, in order to
> understand how AI-to-human handovers should be designed in difficult service situations. If a
> person took part in the second stage of the call, that person was following a standardised research
> script.
>
> Everyone who completed the study receives the same final reimbursement of 1,080 scenario pounds,
> which corresponds to a bonus of GBP 1.08. This does not depend on how the conversation went, or on
> whether you disputed the decision. The bonus is paid through Prolific.
>
> These details were not disclosed beforehand because advance disclosure would have affected the
> responses we were measuring.
>
> If you are experiencing distress following this study, you can contact Samaritans for free,
> 24-hour emotional support (U.K. phoneline: 116 123). Relaxation exercises can be found on the Mind
> website.
>
> Questions: Dr. Joseph Ollier (jollier@ethz.ch).

## 5. `1'000 scenario pounds` — Swiss separator

Block **Task Bonus / Comprehension**:

> 1'000 scenario pounds = GBP £1 real currency

British thousands separator is a comma: `1,000`. Same fix as the portal got. Also `GBP £1` says the
currency twice — pick one.

## 6. The comprehension check example is the old final amount

> if the final reimbursement is **900** scenario pounds, how much additional task bonus does this
> correspond to in real GBP?

The arithmetic is right (£0.90), so this is not an error. But 900 is now a figure that appears
nowhere else in the study except, wrongly, in the debrief — and a participant who remembers it from
the comprehension check and then receives £1.08 has one more reason to think the payment is
inconsistent. Using a number that cannot be mistaken for the outcome (e.g. 500 → GBP 0.50) removes
that.

## 7. The briefing hard-codes a date, in US format

Block **File Insurance Claim** shows `Date 10/11/2026`, and the briefing says the pipe burst
"yesterday evening".

- The format is month/day. In British format the day before 11 October 2026 reads `11/10/2026` —
  this is the same ambiguity the portal's date field had.
- More importantly it is **fixed**, and the study will run over several days. On any other day,
  "yesterday evening" and `10/11/2026` disagree, and a participant who follows the briefing
  faithfully enters a date that is not yesterday.

Either drop the date from the briefing and let the instruction stand on its own ("date the claim
yesterday"), or pipe today's date in via embedded data. Do not leave a literal date in the copy.

## 8. `Portal Completion` expects a code the platform does not yet produce

> Enter the completion code shown at the end of the Salvena interaction.
> If the portal did not display a code, enter NO CODE. If the portal failed before completion, enter
> PORTAL ERROR.

This is a **platform** requirement, not a copy fix, and it is not built. Two options:

- **Build it.** The handback already carries `participantId` back to Qualtrics, so a code is
  redundant as a join key — but it is a genuine check that the participant reached the end of the
  call rather than closing the tab, which the redirect alone does not prove.
- **Delete the block**, and rely on the handback plus the session record in `/data`.

Worth deciding before the pilot either way, because "NO CODE" answers are indistinguishable from a
participant who skipped the portal.

---

## One thing to check with Joe, not to fix here

The ethics application's debrief (Appendix 1b) is where the **1,000 / 25 / 900** wording came from.
The survey has since moved to **1,200 / 60 / 1,080**, which changes what participants are paid and
what the deception consists of. Whether that needs an amendment or just a note is his call, but it
should not be found out after launch.
