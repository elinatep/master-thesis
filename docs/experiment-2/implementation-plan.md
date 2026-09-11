# Experiment 2 — implementation plan

Written after reading `insurance-portal-core` and `levels-of-ai-help` at the commits
mirrored to `elinatep` on 11 Sep 2026.

## 1. What we are building on

The platform is in good shape for this, and better factored than it needed to be.

- **The portal is a library, not an app.** `insurance-portal-core` ships as
  `solutions.andreas:insurance-portal-core` (Maven) + `@insurance-portal/core` (npm).
  `levels-of-ai-help` is one *consumer* of it, plugging in through exactly five seams —
  `AccountRefProvider`, `AccountProvisioner`, `PortalActionRegistry`, `PortalEventSink`,
  and `PortalHost` on the frontend (`docs/portal-carveout.md`).
- **The manipulation lives in files, not code.** `configuration/voice/instructions/level-N.md`
  is overlaid onto `session.json` as `instructions` when the backend mints a token
  (`POST /api/voice/token?level=N`), read from disk per request. Changing what the bot
  does is a file edit and a restart.
- **The Qualtrics seam already exists.** Two-step token handover: Qualtrics `POST`s
  `{participantId, level, name, callbackUrl}` to `/api/handover`, gets an opaque token,
  redirects with `?t=`. `StudyGate.tsx` exchanges it and fails loud on an unknown token.
  Handback returns to `callbackUrl` with `participantId` appended.
- **The data contract is already what the ethics application promises.** `behavioural`
  schema: `study_session`, `event` (CLIENT + SERVER streams), `transcript_entry`
  (text only, audio never stored), `handover`. All keyed to `participantId`.

So Experiment 2 does not need a new platform. It needs a new *consumer* of this one.

## 2. Where Experiment 2 should live

**A sibling repository, not a branch of `levels-of-ai-help`.**

The architecture already anticipates this. `CLAUDE.md` on the infra split:

> Split by **lifecycle**, so a second deployable — the portal on its own — is another
> `containerapp` module instance reusing the same platform.

And the reason it matters practically: experiments run September–December 2026 with the
order still undetermined. Experiment 3 must stay deployable and runnable while Experiment 2
is built. A branch would couple their release cycles; a sibling consumer does not.

Consequences to handle:

- `levels-of-ai-help` **owns `platform.bicep`**. Experiment 2 references the same resources
  read-only through its own `platform-existing.bicep`, exactly as the portal repo already
  does. Only one repo may own it.
- Both deployables would share one Postgres Flexible Server. They cannot share a
  `behavioural` schema — give Experiment 2 its **own database** on that server. Flyway
  histories are already per-repo, so nothing else collides.
- **Pin the portal artifact versions** before the study run. `CLAUDE.md` warns that Maven
  caches SNAPSHOTs for a day, so a republished portal can be silently missed.

## 3. The condition model

Experiment 3's IV is an ordinal level 0–3. Experiment 2's is a 2×2:
AI acknowledgement (none / acknowledged) × Human acknowledgement (none / acknowledged).

`level` will not carry this. It is a `short` on `Handover` and `study_session`, validated
`@Min(0) @Max(3)` in `HandoverController.RegisterRequest`.

**Use two explicit boolean columns (`ai_ack`, `human_ack`), not four packed codes.**
The analysis is a 2×2 ANOVA; the factors need to be separate columns so main effects and
the interaction can be read straight out of the export, rather than decoded from a lookup
after the fact. The migration is trivial and this is a new repo, so there is no legacy to
carry.

Carry the same two fields through: `handover` → `StudyGate` exchange → `study_session` →
the `/data` CSV export.

**Randomisation stays in Qualtrics.** It already owns consent, screening and the survey; the
`POST /api/handover` call is server-side from Qualtrics, so the condition is assigned before
the participant ever reaches the portal. Nothing in the survey screenshot implements this yet —
it is the first Qualtrics-side task.

## 4. The human agent leg — the actual build

This is what does not exist. `VoiceWidget.tsx` / `useVoiceSession.ts` is browser↔OpenAI only;
there is no second human role anywhere in either repo.

### 4.1 Agent console (new frontend surface)

A route outside the participant gate (like `/data` is), but **authenticated** — see §6.

- **Queue** of participants awaiting pickup, with wait time.
- **Live AI transcript**, streaming, already populated at pickup. This is the instrument,
  not a convenience: `Cross.agent.cont` asks the participant whether the human "appeared to
  know what the AI assistant had already learned".
- **Structured handover payload**: claim ref, £1,000 claimed, £50 approved, what the
  participant asked for, and whether the AI already acknowledged their affect.
- **Condition card**: this participant's `human_ack` cell, the required opening move, and a
  short checklist. Without it the manipulation drifts across ~200 calls per cell.

### 4.2 Voice path

Real two-way WebRTC between participant and agent. A synthesised voice would sound like the
AI and collapse the distinction the experiment rests on.

Note this is a **different topology** from what exists. Today the browser dials OpenAI
directly (`client.json` → `realtimeUrl`). Participant↔agent audio is peer-to-peer and needs
signalling the platform does not currently have — a small signalling channel plus STUN/TURN.
This is the largest single piece of new infrastructure.

Transcription of the human leg must still produce `transcript_entry` rows. Audio is never
stored, on either leg — including the agent's.

### 4.3 Transcript continuity

`transcript_entry.role` is `VARCHAR(16) NOT NULL` with **no CHECK constraint** and is a plain
`String` in Java, so adding `HUMAN_AGENT` alongside `USER` / `ASSISTANT` needs no migration.

Keep both legs in **one** `study_session` so `participantId` still joins cleanly and the
handover boundary is analysable.

### 4.4 Handover trigger and hold state

The AI ends its turn and transfers; the participant needs a credible hold state while an
agent picks up. Emit it as a `VOICE_STATUS`-family event so the wait is measurable —
time-to-pickup is a plausible covariate on the downstream ratings.

Have the AI write its handover summary as an **explicit tool call** (a `tools/` JSON entry,
matching how levels 2 and 3 already declare functions) rather than deriving it in the console.
The tool call lands in `event` as `VOICE_TOOL_CALL` and becomes analysable.

## 5. Scenario and seed

Experiment 2's scenario is not Experiment 3's task, so `portal-seed/` needs its own fixtures.
`SEED_LOCATION` / `CONTENT_LOCATION` already override this — no code change.

Needed: a household policy with **90% coverage indicated**, and the participant's water-damage
claim moving to an assessed outcome of **£50 against £1,000 claimed**.

**The outcome should be shown in the portal, not in Qualtrics.** The ethics application
describes it that way — *"They will be briefed with reviewing a fictional claim on an
insurance platform. Upon reviewing their claim, they will see the full amount promised to
them is not going to be reimbursed"* — and it is where the affect manipulation actually
lands. The current survey puts it in Qualtrics block 5 (`Q15`). See §6.

## 6. Findings that need a decision

**a. Currency.** The portal is Swiss: `format.ts` hardcodes `CHF 1'250.00` as "the brand spec
regardless of the runtime's locale", `CertificateService.java:219` and
`PolicyPdfService.java:233` build `"CHF " + …` by hand, and `portal-seed/policies.json` is CHF
throughout. Experiment 2 briefs a **UK sample in £**. Participants would read £1,000 in
Qualtrics and see CHF in the portal.

Contained but it is a *portal library* change, not a study-repo one, so Experiment 3 has to
tolerate it. The schema already has a per-policy `currency` column and `money()` already takes
a currency parameter — so the fix is parameterising the two PDF services and the call sites,
not a rewrite. Five non-test files touched.

**b. Where the claim outcome is shown.** Per §5 it belongs in the portal. That makes the portal
session contiguous — file claim → see the £50 → dispute → AI → human — with Qualtrics doing
consent and briefing before, and the survey after. But `Emotion.check` (block 10) has to fire
*after* the outcome and *before* the dispute decision, so it would have to move into the portal
as a study interstitial logged to `behavioural`.

The alternative is two portal visits. The handover token is reusable within its TTL, so
re-entry works — but `StudyGate` creates a new `study_session` per entry, which would split one
participant's transcript and events across two rows. Fixable, but it fragments exactly the data
the handover DVs depend on. **Recommend the contiguous session.**

**c. £50 vs £25.** The survey approves £50; the ethics application's Experiment 2 debrief says
25 scenario pounds. Pick one before it is hard-coded in the seed and the debrief.

**d. Remote participants break a stated assumption.** `CLAUDE.md`:

> The study runs on a standardised setup (a provided laptop running Chrome) to remove this as
> a confound — optimise for that target, don't burn time on broad cross-browser support.

Experiment 2 recruits 400 people on Prolific, on their own machines and browsers. Microphone
permissions, browser WebRTC variance and audio quality become real sources of attrition and of
between-subject noise — the thing Experiment 3 deliberately de-scoped. Needs a browser check
and mic test before the endowment is committed, plus a documented exclusion rule.

**e. `/data` and `/api/admin/*` are unauthenticated.** Documented as acceptable for Experiment 3
("fake data, scheduled sessions"). With a public Prolific link and an agent console holding live
participant transcripts, that no longer holds. The console needs real auth; `/data` and the
admin reset endpoints should be locked down in the Experiment 2 deployable.

**f. `CLAUDE.md` is stale on the voice provider.** It says the browser connects "directly to
OpenAI (not via Azure)", but `configuration/voice/client.json` points `realtimeUrl` at
`mtec-insurance-portal.openai.azure.com`. Azure is the correct target — the ethics application
rests on ETH's no-data-retention contract with Microsoft/Azure — so the config is right and the
document should be corrected to match, since the ethics claim depends on it.
