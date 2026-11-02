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

So the blocks fix what must be fixed — **what each condition has to achieve, roughly how often, and
what is forbidden** — and leave the wording to the agent. The example lines are the shape of the
thing, not lines to deliver.

They used to fix three acknowledging moments at three named points in the call. That was dropped:
a participant who opens with "I just want to know why I was offered sixty instead of a thousand and
eighty" has given you nothing to acknowledge yet, and acknowledging it anyway is exactly what makes
someone notice they are being handled. The floor is now **at least two acknowledgements, placed
where the moment is there**.

## What the base owns, and why

The base holds the opening, **the three questions**, and the close. The condition blocks hold
nothing but the response that goes in front of each question.

That split is the manipulation. Both agents genuinely handle a dispute: same questions, same
information gathered, same procedural steps. The only thing that varies is whether the turn before
each question responds to how they feel.

It also removes the way this design fails quietly. A question worded one way in one arm and another
way in the other is a second manipulation nobody recorded — and it arrives by a single word. "What
concerned you about it" and "what you wanted reviewed" are the same question with an emotion word
in one of them. With one copy of each question there is nothing to drift.

**Asking about feelings is forbidden in both arms.** "How did that make you feel?" in the
acknowledging condition only would mean manipulating acknowledgement *and* inviting emotional
disclosure, and no analysis separates them afterwards. Respond to feelings in one arm; ask about
them in neither.

**Acknowledge, then ask.** An acknowledgement followed by silence is a different thing from an
acknowledgement inside a turn that carries the call forward. The blocks put it in front of the
question deliberately.

That puts weight on training. Expect to run the pilot, listen back, and rewrite.

## The confound these are built against

The acknowledging agent will naturally also talk *more* and *warmer*. Left alone, the study
manipulates three things and measures one.

Both blocks therefore respond to **the same things, at the same points, at the same length** — the
acknowledging one to the feeling, the other to the content. The call should come out the same
length either way, with the agent talking for the same share of it.

Dropping the fixed three moments moved that from the script onto the agent, which is the cost of
sounding real. The non-acknowledging block carries the instruction that matters: if everything left
to say would be acknowledgement, go back to the detail of the claim rather than going quiet.

**This is now the pilot's first job**, not an assumption. If acknowledging calls run consistently
longer, the blocks get rewritten — the agent must not pad on the call to fix it, because then the
padding varies by agent and by mood.

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
- **How long the calls actually run**, and whether the two conditions match. With the fixed moments
  gone, nothing in the script enforces this any more — only the instruction to respond to the same
  things at the same length. Check it from `duration_min` in the session log over the pilot. If the
  acknowledging calls run consistently longer, the blocks get rewritten rather than the agent
  padding on the call.
- **How many acknowledgements actually land**, per call, in the pilot recordings. Two is the stated
  floor; whether a floor without a fixed structure is reliably met is the thing a semi-structured
  block trades away, and it is worth knowing before 400 calls rather than after.
