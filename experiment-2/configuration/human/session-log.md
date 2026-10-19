# The agent's session log

One row per call, filled in by the agent **immediately after hanging up** — not at the end of a
block of sessions. Most of what is worth recording is the part you would not remember an hour
later.

Keep it in a spreadsheet. The columns:

```
session_id, agent, condition, started, duration_min, reached_close, pushed_for_the_figure,
acknowledgement_slipped, participant_distress, technical_problem, exclude, notes
```

## What goes in each one

| Column | What to put |
| --- | --- |
| `session_id` | The handover id from the console header. The join key - without it the row cannot be matched to anything |
| `agent` | Your initials. Which agent ran which call **enters the analysis**, so this is data, not bookkeeping |
| `condition` | `ack` or `noack`, copied from the card you were given |
| `started` | Clock time you picked up |
| `duration_min` | Roughly, to the minute. The two conditions are written to take the same time, and this is how that gets checked |
| `reached_close` | `y` if you got through the standardised close; `n` if the call ended some other way |
| `pushed_for_the_figure` | `y` if they asked what they would actually get. Expected on most calls - this measures how often, not whether anyone misbehaved |
| `acknowledgement_slipped` | **`y` if you acknowledged how they felt in a no-ack call.** See below |
| `participant_distress` | `y` if they were upset beyond the scenario, whatever you did about it |
| `technical_problem` | Audio dropouts, a transcript that was not there, anything that made the call unlike the others |
| `exclude` | `y` if you think this one should not be analysed, with the reason in notes |
| `notes` | Free text. Anything that would change how someone reads the recording of this call |

## The one that matters most

**`acknowledgement_slipped`.** When someone is genuinely upset, "I'm sorry" is close to reflexive,
and it is the single most likely way a no-ack call gets contaminated.

A flagged session can be excluded, or analysed separately, or kept with the flag as a covariate.
An unflagged one quietly weakens the effect and nothing in the data shows why. **Flagging costs a
data point; not flagging costs the finding.** Nobody is checking up on you — the flag exists so the
analysis can be honest.

Same for `exclude`. A lost call is a much smaller problem than a bad one counted as good.

## Why not in the console

It could be, and the console already knows the session id, the condition and the duration, so it
could fill half the row for you. That is worth building if these are run at any volume.

It is not built yet, and the log has to exist before the first call rather than after — so for the
pilot this is a spreadsheet. Decide after the pilot whether the typing is worth the code.

## Afterwards

The log joins to the behavioural data on `session_id`, and to the Qualtrics export through the
participant id the console shows beside it. Keep it wherever the exports go — **not** in this
repository, which is gitignored against exactly that.
