# Experiment 2 — Qualtrics survey as built (`SV_0vNMcH7s9iawN14`)

Read off the survey's own print preview, 12 Oct 2026. This is the baseline the platform has to plug
into — it is *not* a proposal.

It supersedes the earlier reconstruction from the builder screenshot (`Exp2_S1_Anger_v0`,
comments by Joseph Ollier dated 9 Sep 2026), which had the amounts at **1,000 claimed / 900 expected
/ 50 approved**. The survey now runs on **1,200 / 1,080 / 60**.

Copy that still contradicts the flow is listed separately in
[`qualtrics-copy-fixes.md`](qualtrics-copy-fixes.md).

## Block map

| # | Block | Questions | Owner today |
|---|-------|-----------|-------------|
| 1 | Intro / Consent | information sheet, consent, prior-mental-health screening question | Qualtrics |
| 2 | Task Bonus / Comprehension | £1.00 provisional task balance, **1,000 scenario pounds = GBP £1**, comprehension check (900 → £0.09 / **£0.90** / £9.00) | Qualtrics |
| 3 | Scenario Briefing | "Your situation" — burst water pipe, **1,200** scenario pounds of damage, 90% indicated as covered, so **1,080 expected** | Qualtrics |
| 4 | File Insurance Claim | the claim as it must be filed — water damage / burst water pipe / **1,200** / dated the day before; then `RESEARCHER SETUP REQUIRED`: *"Replace this block with the embedded Salvena claim portal"*, passing `session_token` | **Platform** |
| 5 | Initial Claim Outcome | claimed 1,200, expected 1,080 (£1.08), **approved 60 (£0.06)** — "only 5% of the claimed damage is currently eligible", documentation complete, damage not disputed, no explanation given | Qualtrics |
| 6 | Customer Service Review | "The review follows a standardized procedure. The final reimbursement will be shown after the customer-service interaction." | Qualtrics |
| 7 | AI Voice Assistant | *"You will now speak with Salvena's AI voice assistant"* → `RESEARCHER SETUP REQUIRED`: embed the voicebot, pass `session_token`, `condition`, `AI_ack`, `Human_ack` | **Platform — the gap** |
| 8 | Human Handover | *"You will now continue the conversation with a Salvena customer-service employee"* → `RESEARCHER SETUP REQUIRED`: the confederate handover; the employee gets only their condition's script; **must not disclose or resolve the final reimbursement** | **Platform — the gap** |
| 9 | Portal Completion | a completion code shown at the end of the Salvena interaction, with `NO CODE` / `PORTAL ERROR` fallbacks | **Platform — not built** |
| 10 | Post_Checks | `MC.AI.Acknowledge` (3 items), `MC.Hu.Acknowledge` (3 items), realism, how many agents they spoke with, who they think each stage was | Qualtrics |
| 11 | Post Interaction Measures | `Feeling.heard` (Understood / Affirmed / Validated / Seen / Accepted / Cared for), felt-heard per agent, handover continuity, NPS | Qualtrics |
| 12 | Final Task Bonus | `Final reimbursement: 1080 scenario pounds` / `Your additional task bonus is GBP 1.08` | Qualtrics |
| 13 | Debrief | two blocks, currently inconsistent with each other and with block 12 — see the copy fixes | Qualtrics |
| 14 | End of Survey | thank-you, Samaritans signpost, results signup | Qualtrics |

## Consequences for the platform

1. **Three separate hand-off points**, not one: block 4 (file the claim), block 7 (AI voicebot),
   block 8 (human agent). Blocks 5–6 sit *between* the first and second hand-off, and block 8
   immediately follows block 7.
2. **The emotion check has to fire after the participant sees the 60 and before the call.** That is
   the anger manipulation check, so its position is load-bearing — it constrains how much of the
   flow the platform can own contiguously.
3. **Blocks 7 → 8 must be one continuous voice session** for the handover DVs to mean anything.
   "I did not have to repeat important information" is only measurable if the AI-side context
   actually reaches the human agent — which is what `human-base.md` makes the agent demonstrate in
   its opening.
4. **Neither the AI nor the human may reveal the final 1,080.** The survey shows it in block 12,
   after the measures. The survey's own setup note says so, and it is the first rule in both sets of
   agent instructions.
5. **Block 9 wants a completion code the platform does not produce.** Build it or delete the block;
   see the copy fixes.

## Manipulations to carry (from ethics application §2.2)

2 (AI voicebot acknowledgement: none / acknowledged)
× 2 (Human acknowledgement: none / acknowledged), between subjects, N = 400.

The survey's setup notes name `condition`, `AI_ack` and `Human_ack` as the values to pass to the
voicebot, but no randomiser or embedded-data field appears in the print preview — condition
assignment is still unimplemented on the Qualtrics side. The pre-test does this with a randomiser
plus `arm` / `arm_label` embedded data; the same pattern applies here.

## Data the platform must return (ethics Appendix 2, Experiment 2)

- Transcript: chat messages + timestamps (text only — **no voice data may be stored**)
- System metrics: portal page navigation, tool calls, documents opened, + timestamps
- Join key: **`participantId`, pre-set in Qualtrics** ("Transcripts will be joined to the Qualtrics
  survey data using participantId"). The survey's own notes call it `session_token`; one name needs
  to win, and the pre-test already uses `participantId` end to end.
