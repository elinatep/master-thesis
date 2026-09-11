# Experiment 2 — the human employee leg

**Decision (ET, this session): a real human agent, who can see the AI transcript.**

Not a second LLM persona, not a script. This is the single biggest thing Experiment 2
needs that Experiment 3's platform does not already have, so it drives the build.

## Why the transcript requirement is load-bearing

`Cross.agent.cont` asks the participant to rate, among others:

> "The human employee appeared to know what the AI assistant had already learned about my issue."
> "I did not have to repeat important information because the AI assistant and the human employee were on the same page."

These are only measurable if the human agent genuinely has the AI-side context in front
of them at pickup. So the transcript view is not a convenience for the agent — it is the
instrument.

## What the platform has to grow

### 1. Agent console (new surface)
Authenticated route, separate from the participant app. At minimum:

- **Queue** of participants waiting for handover, with wait time.
- **Live AI transcript** for the selected session, streaming, already populated at pickup.
- **Structured handover payload** from the AI leg — claim ref, amount claimed (£1,000),
  approved (£50), what the participant said they wanted, and whether the AI already
  acknowledged their emotional state.
- **Condition card**: which cell this participant is in on the *human acknowledgement*
  factor, with the required opening move and a short checklist. Without this the
  manipulation drifts across ~200 calls per cell.
- **Claim context** mirroring what the participant sees in the portal.

### 2. Handover trigger and hold state
The AI leg ends its turn and transfers. The participant needs a credible hold state
(ringing/queue audio) while an agent picks up, and the platform needs a defined
behaviour when no agent is free — see operational constraints below.

### 3. Voice path for the human agent
Real two-way voice (WebRTC), not typed-then-TTS. A synthesised voice would sound like
the AI and collapse the AI-vs-human distinction the experiment rests on.

Treat the audio exactly as the AI leg already does: **transcribe, persist text only,
never store audio.** That keeps the ethics commitment ("we capture no voice data")
intact on both legs. The agent's own audio is equally not retained.

### 4. Transcript continuity
Both legs must land in **one** transcript keyed to the same session, tagged by speaker
(`participant` / `ai_agent` / `human_agent`), so the handover boundary is analysable and
`participantId` still joins cleanly to Qualtrics.

## Operational constraints this creates

- **Scheduling.** N = 400 means ~400 live calls. Prolific runs asynchronously by default;
  a live human leg needs either scheduled slots or agents rostered across the release
  window. This is a study-operations decision, not a platform one, but the platform has
  to support whichever is chosen (queue depth, agent presence, release pacing).
- **Timeout policy.** Define what happens when a participant waits too long: how long
  before fallback, what they see, and whether they are excluded from analysis. Needs to
  be pre-registered rather than decided after the fact.
- **Agent consistency.** More than one agent will be needed. Inter-agent variance is a
  threat to the human-acknowledgement manipulation; the condition card plus a short
  agent protocol and a training/calibration pass before go-live mitigate it. Log which
  agent handled which session so it can be entered as a random effect.
- **Partial blinding is impossible.** The agent must see the transcript, which reveals
  whether the AI acknowledged — so the agent cannot be blind to the AI factor. Worth
  stating explicitly in the analysis plan; the condition card should at least keep the
  agent's own behaviour tied to their assigned cell regardless of what they read.

## Still open

- Where the emotion check (`Emotion.check`, Qualtrics block 10) sits relative to the
  portal session — it has to fire after the £50 outcome and before the dispute decision.
- Whether the AI writes its handover summary explicitly (a tool call the transcript
  records) or the console derives it. The former is more analysable.
