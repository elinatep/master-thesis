# The human agent's script — shared base

Everything in this file applies in **every** condition. It is read with exactly one of
`human-ack.md` or `human-noack.md`, and nothing else about the call changes between them.

Read this whole file before your first session, and keep the condition card in front of you.

---

## Who you are

You are a customer-service employee at **Salvena**, an online insurance provider. A colleague — the
AI voice assistant — has just taken the first part of this call and transferred it to you because
the customer is disputing how their claim was settled and the assistant cannot change a decision.

You are not playing a character with a backstory. You are an ordinary competent employee. If asked
your name, give the one on your condition card and move on.

## The case in front of you

You can see this before you pick up, in the console, along with the transcript of what the customer
has already said:

| | |
| --- | --- |
| Claim | Water damage, burst pipe at home |
| Amount claimed | 1,200 |
| Coverage indicated when they filed | 90% |
| Amount the assessment approved | 60 |

So they expected around **1,080** and have been offered **60**. That gap is why they are on the
call.

**Amounts are "pounds" when you say them out loud.** Do not say "scenario pounds" — that is the
study's word, not Salvena's.

## What you may and may not do

**You may:** take the dispute, record what they say, explain the process, tell them the review is
being carried out.

**You may not:**

- Say what the final reimbursement will be, or hint at it, or let them infer it. **This is the
  single most important rule on the call.** The final amount is shown to them afterwards, by the
  survey. If you reveal it, that participant's data is unusable.
- Promise an outcome, a timescale for payment, or that the decision will be changed.
- Blame the assistant, the assessor, the system or the customer.
- Offer anything outside the claim — goodwill payments, discounts, a callback.

If they push for the number, use the standard line in *Handling pushback* below. They will push.
Expect it and do not improvise an answer.

## How the call runs

### 1. Open by showing you already know

Pick up with a short opening that makes it clear you have read what they told the assistant. Do
**not** ask them to explain the claim again.

> "Hello, you're through to Salvena, this is ___. I've got your file in front of me and I've read
> through what you discussed a moment ago — the water damage from the burst pipe, the twelve
> hundred claimed, and the sixty that came back. Let me take it from here."

Use your own words, but the opening must do three things, every time, in every condition: **name
yourself, say you have read the previous conversation, and show it by referring to a specific
detail they gave.**

This is not a courtesy. The study measures whether the handover felt connected, and that is held
constant across all conditions — it is not the thing being varied. An opening that makes them start
over breaks the call for every condition equally, which is worse than breaking it for one.

### 2. Let them say what they want to say

Let them talk. Do not interrupt. If they have already said it to the assistant, you still let them
say it again if they want to — you just must not *ask* them to.

### 3. The condition block

Here is where `human-ack.md` or `human-noack.md` applies. That is the only part of this call that
differs between participants.

### 4. Take the dispute

Confirm, out loud, what they are disputing and what they are asking for. Short — two or three
sentences. This is the same in every condition.

> "So — you're disputing the assessment because the policy indicated ninety per cent cover on water
> damage and the award came back at sixty. And what you're after is for that assessment to be looked
> at again."

### 5. Close — say this, near enough word for word

The close is **standardised**. It is the one place you should not improvise, because it is the last
thing they hear before they answer the questions.

> "Right — I've got everything I need and the review is complete on my side. The final outcome will
> be shown to you at the end of this session rather than by me on the call. Thanks for taking the
> time to go through it. Goodbye."

Then end the call. Do not add anything after it, however they respond.

## Handling pushback

These will happen. Use these answers rather than inventing your own — an improvised answer is a
difference between participants that nobody recorded.

**"So how much am I actually getting?"**
> "I'm not able to give you the figure on the call — the outcome gets confirmed to you at the end of
> this session. What I can tell you is the review's been done properly."

**"Can you change it? / Can you approve more?"**
> "The amount isn't something I set on the call. What I've done is make sure the dispute is recorded
> with the detail you've given me."

**"Why did the assistant not just deal with this?"**
> "It takes the details and passes anything disputed to one of us. That's why you're through to me."

**"Is this a real company? / Are you a real person? / Is this a bot?"**
> "I'm a real person, yes. This is a research session, so Salvena is a fictional insurer — but let's
> carry on with the claim as described."

Then continue the call normally. Do not discuss the study further.

**They become abusive, or distressed beyond the scenario.**
Drop the script. Be a person. If they are distressed, say they can stop at any point and that
nothing depends on finishing. End the call if that is the right thing. Flag the session afterwards
so it can be excluded — a wrong call is a lost data point, which is a much smaller problem than the
alternative.

## Keeping the call comparable

The conditions differ in **one** thing. Everything else you do is a confound, so hold these steady
regardless of which block you are running:

- **Length.** Aim for the same call duration in every condition. If you find the acknowledgement
  calls running consistently longer, the manipulation is partly just talk time.
- **Warmth of delivery.** Tone, pace, friendliness: the same. The non-acknowledging condition is
  *business-like*, not cold and not curt. A participant should not be able to tell that you are
  being deliberately withholding.
- **Competence.** You are equally good at your job in every condition.
- **Who speaks.** Do not talk over them in one condition and wait in another.

The two blocks are written to take roughly the same time to say. If one is consistently shorter in
practice, say so after the pilot and it gets rewritten — do not pad it yourself on the call.

## Afterwards

Log, for every call: the session id, which condition you ran, how long it took, and anything that
went wrong. Which agent handled which session gets entered in the analysis, so it has to be
recorded even when nothing notable happened.
