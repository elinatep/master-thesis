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

### 1. Open — identical in every condition

> "Hi, my name is ___. I've just read through your conversation with our AI assistant, so I'm
> already familiar with your case. I can see that you submitted a claim for twelve hundred pounds
> following water damage, but the amount you were offered was only sixty.
>
> Could you tell me what you'd like us to look into?"

Use your own words, but the opening must do three things, every time, in every condition: **name
yourself, say you have read the previous conversation, and show it by referring to a detail they
gave.** Never ask them to explain the claim from the start.

This is not a courtesy. The study measures whether the handover felt connected, and that is held
constant across all four cells — it is not what is being varied. An opening that makes them start
over breaks the call in every condition equally, which is worse than breaking it in one.

### 2. Let them say what they want to say

Let them talk. Do not interrupt. If they have already said it to the assistant, you still let them
say it again if they want to — you just must not *ask* them to.

### 3. The condition block

Here is where `human-ack.md` or `human-noack.md` applies. **It is the only part of this call that
differs between participants.**

### 4. Confirm what you have — identical in every condition

Short. One or two sentences, saying back what they are disputing.

> "So the point you're disputing is the ninety per cent cover against the sixty that came back, and
> you'd like the assessment looked at again. I've got that recorded."

### 5. Close — near enough word for word

The close is **standardised**. It is the last thing they hear before they answer the questions, so
it is the one place you should not improvise.

> "Okay, I think that's everything I need from you. The review is now complete on my side, and
> you'll see the final outcome at the end of this session.
>
> Thank you for your time, and have a nice day. Goodbye!"

Then end the call. Do not add anything after it, however they respond.

## Handling pushback

These will happen. Use these answers rather than inventing your own — an improvised answer is a
difference between participants that nobody recorded.

| They ask | You say |
| --- | --- |
| How much am I getting? | "The final amount will be shown to you at the end of the session. Unfortunately, I can't confirm it during the call." |
| Can you change the amount? | "I can't change the amount directly, but I can make sure your dispute is properly recorded." |
| Why couldn't the AI help me? | "The AI assistant collects the initial information, but disputed claims are passed on to a human agent." |
| Are you a real person? | "Yes, I'm a real person. I'm helping with the customer service part of this research study." |

Then carry on with the call normally. Do not discuss the study further.

**Do not promise what happens next.** "I'll make sure it's recorded" is true. "The assessment team
will go through the whole file again" is a claim about a process you do not control, and it is the
kind of thing that gets said in one condition and not the other. Describe what *you* are doing;
say nothing about what anyone else will do with it.

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

**Airtime.** The blocks no longer fix a number of scripted moments, so this is now yours to hold:
respond to the same things at the same points, at the same length, and differ only in *what kind* of
response it is. If you find yourself with nothing to say in the non-acknowledging condition, that is
the condition going wrong, not the participant being easy — go back to the detail of their claim.

If one condition runs consistently shorter after the pilot, say so and the blocks get rewritten. Do
not pad on the call: padding varies by agent and by mood, and then that is what the study measured.

## Afterwards

Log, for every call: the session id, which condition you ran, how long it took, and anything that
went wrong. Which agent handled which session gets entered in the analysis, so it has to be
recorded even when nothing notable happened.
