# Experiment 2 — Qualtrics survey as built (`Exp2_S1_Anger_v0`)

Reconstructed from the survey-builder screenshot (status: **Draft**, last saved 6:10 PM,
comments by Joseph Ollier dated 9 Sep 2026). This is the baseline the platform has to
plug into — it is *not* a proposal.

## Block map

| # | Block | Questions | Owner today |
|---|-------|-----------|-------------|
| 1 | Intro / Consent | `Consent.Information` (info sheet), consent Accept/Reject | Qualtrics |
| 2 | Endowment | "Insurance task and bonus" (1000 scenario £ = £1 real), bonus comprehension check (£900 → £0.09 / **£0.90** / £9.00) | Qualtrics |
| 3 | Scenario Briefing | `Q12` "Your situation" — burst water pipe, **£1,000** damage | Qualtrics |
| 4 | File Insurance Claim | `Q13` "File your insurance claim" — *"You will now be taken to the Salvena customer portal"*; type = water damage, cause = burst pipe, amount = £1,000 | **Platform** |
| 5 | Claim Outcome | `Q15` "Your claim has been assessed" — claimed £1,000, coverage indicated 90%, expected £900, **approved £50** | Qualtrics |
| 6 | Block 10 (unnamed) | `Emotion.check` — Angry / Irritated / Worried / Helpless / Sad, 0–10 sliders. Marked *"separate pre-test"* | Qualtrics |
| 7 | Escalation Decision | `Dispute.Offer` — Accept the £50 **or** Contact customer service | Qualtrics |
| 8 | *(AI Voice Interaction)* | *"You will now speak with Salvena's **AI voice assistant**…"* → **`[VOICEBOT / SALVENA INTEGRATION TO BE ADDED]`** | **Platform — the gap** |
| 9 | Human Handover | `Q10` "Transfer to a customer service employee" — *"You will now be transferred to a Salvena customer service employee."* | **Platform** |
| 10 | Post Interaction Survey | `MC.AI.Acknowledge` (3 items), `MC.Hu.Acknowledge` (3 items), `Feeling.heard` (Understood/Affirmed/Validated/Seen/Accepted/Cared for, 7-pt), `Cross.agent.cont` (6-item handover continuity), `NPS` (0–10) | Qualtrics |
| 11 | Debrief | `Q16` — full debrief incl. Samaritans signpost | Qualtrics |
| 12 | End of Survey | standard thank-you | Qualtrics |

Trash contains `Q18` (empty) and `Informed.Results`.

## Consequences for the platform

1. **Three separate hand-off points**, not one: block 4 (file claim), block 8 (AI
   voicebot), block 9 (human agent). Blocks 5–7 sit *between* the first and second
   hand-off, and block 9 immediately follows block 8.
2. **Block 6 must fire after the participant sees the £50 outcome and before they
   decide to dispute.** That is the anger manipulation check, so its position is
   load-bearing — it constrains how much of the flow the platform can own contiguously.
3. **Blocks 8 → 9 must be one continuous voice session** for the handover DVs
   (`Cross.agent.cont`) to mean anything. "The human employee built on the AI
   assistant conversation rather than ignoring it" is only measurable if AI-side
   context actually reaches the human-agent persona.
4. The **£50** in the survey conflicts with the **£25 scenario pounds** stated in the
   ethics application debrief (Appendix 1b, Experiment 2). One of the two needs
   correcting before the platform hard-codes an amount.

## Manipulations to carry (from ethics application §2.2)

2 (AI voicebot acknowledgement: none / acknowledged)
× 2 (Human acknowledgement: none / acknowledged), between subjects, N = 400.

Neither factor appears as a randomiser or embedded-data field anywhere in the survey
screenshot — condition assignment is currently unimplemented on the Qualtrics side.

## Data the platform must return (ethics Appendix 2, Experiment 2)

- Transcript: chat messages + timestamps (text only — **no voice data may be stored**)
- System metrics: portal page navigation, tool calls, documents opened, + timestamps
- Join key: **`participantId`, pre-set in Qualtrics** ("Transcripts will be joined to
  the Qualtrics survey data using participantId")
