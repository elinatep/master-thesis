# The human agent's instructions

The second leg of the call, in the same shape as the first: one shared base plus one block that
differs by condition.

```
human-base.md     everything every condition shares - the case, the rules, the close
human-ack.md      humanAck = true   responds to how the customer feels
human-noack.md    humanAck = false  procedural detail instead
```

The agent reads `human-base.md` plus exactly one block. Same construction as
`../voice/instructions/`, for the same reason: two long near-identical scripts drift apart, and the
first correction made in one and not the other is a confound that no test catches and no reviewer
sees. Here it matters more than it does for the AI, because a person reading two scripts will
remember the wrong one.

## Why the blocks are semi-structured rather than word-for-word

A fully scripted call is controlled and sounds like it. A participant who notices the agent is
reading stops treating it as a service call, and what gets measured afterwards is their reaction to
a performance.

So the blocks fix what must be fixed — **how many moments, where in the call, what each one has to
achieve, and what is forbidden** — and leave the wording to the agent. The example lines are the
shape of the thing, not lines to deliver.

That puts weight on training. Expect to run the pilot, listen back, and rewrite.

## The confound these are built against

The acknowledging agent will naturally also talk *more* and *warmer*. Left alone, the study
manipulates three things and measures one.

Both blocks therefore specify **three moments at the same points in the call**. The acknowledging
one spends them on feelings; the other spends them on procedural detail. The call should come out
the same length either way, with the agent talking for the same share of it.

The pilot has to check this rather than assume it. If acknowledging calls run consistently longer,
the blocks get rewritten — the agent must not pad on the call to fix it, because then the padding
varies by agent and by mood.

## What is held constant and is not a factor

**Continuity.** The agent always opens having read the transcript, always refers to a specific
detail the participant gave the assistant, and never asks them to start again. That is identical in
all four cells.

The survey measures it —

> "I did not have to repeat important information."
> "The customer-service employee appeared to know what the AI assistant had already learned."

— as a check that the handover design works at all, not as something being varied. An agent who
makes the participant start over has broken the call in every condition.

## The rule that outranks the manipulation

**The agent must never reveal the final reimbursement.** The survey shows it afterwards. An agent
who says it, hints at it, or lets it be inferred has made that participant's data unusable, in any
condition.

`human-base.md` gives fixed answers for the pushback that leads there, because it will come up on
most calls and an improvised answer is a difference between participants that nobody recorded.

## Settled

- **Whether the agent knows the AI condition.** No. The card shows `humanAck` — their own
  instruction — and nothing about the AI leg. They can read the transcript and will often infer it,
  so this is not blinding; it is consistency, so that what they are *told* is the same every
  session rather than varying with how closely they read. `AgentConsoleTest` asserts that no
  agent-facing payload carries the AI factor, so a later convenience field cannot quietly undo it.
- **The session log.** [`session-log.md`](session-log.md): one spreadsheet row per call, filled in
  immediately after hanging up. The column that matters most is `acknowledgement_slipped` — in a
  no-ack call, "I'm sorry" is close to reflexive, and flagging it costs a data point while not
  flagging it costs the finding.

## Still to settle, before the pilot

- **The agent's name**, on the condition card. Same name every call, or varied? Same is one less
  moving part; varied is more realistic if several agents run sessions. The card currently tells
  them to give "the one on your condition card", which nothing yet fills in.
- **How long the calls actually run**, and whether the two conditions match. The blocks are written
  to take the same time; the pilot is where that gets checked, from `duration_min` in the session
  log. If the acknowledging calls run consistently longer, the blocks get rewritten rather than the
  agent padding on the call.
