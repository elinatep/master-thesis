# Experiment 2 — AI-to-Human Handover

The task environment for Experiment 2. It hosts the Salvena insurance portal and adds the
experiment around it: Qualtrics handover, 2×2 condition intake, the AI voice assistant, the
transfer to a human agent, behavioural logging, handback.

| Directory | What it is |
| --- | --- |
| `exp2-app/` | Spring Boot application. The deployable. |
| `exp2-frontend/` | Vite/React application. Builds into the jar's `static/`. |
| `configuration/` | Voice config and task text, read at runtime. **The prompts here are the manipulation.** |
| `docker/` | Local Postgres, the image build. |
| `infra/` | The Azure deployment (Bicep). `.env.example` is the file you fill in. |
| `scripts/` | Build, deploy, and walk one cell of the 2×2 as a participant. |

## What a participant does

One platform visit, in a straight line:

```
file the claim in the portal
  -> Salvena's assessment: 1,200 claimed, 1,080 expected, 60 awarded
       -> "Accept the assessment and finish"   -> back to Qualtrics, escalated=false
       -> "Contact customer service"           -> the AI assistant
                                               -> the human employee
                                               -> back to Qualtrics, escalated=true
```

The assessment is the pre-test's winning anger script (`configuration/study/outcome.json`, which is
its `S1.json`), held constant for everyone: the pre-test's job was to find an outcome that reliably
produces anger without reading as a bug, and this study takes the winner as its setup. What varies
between participants is what they then hear on the call.

**The choice is real.** Accepting the assessment ends the task, which means that participant never
meets either agent and is in no cell of the 2x2 - so the decision is logged (`ESCALATION_CHOICE`)
and carried back to Qualtrics as `escalated`, where it has to be branched on. How many decline is
unknown until the pilot, and it sets how far past 400 you have to recruit.

**The microphone is checked before they are put through**, with a live level meter, because
permission is per-origin and a participant whose microphone is muted otherwise finds out when a
confederate is already on the line listening to silence. A refused permission is a stop with a way
out — they can accept the assessment and still be paid. A quiet meter is not a stop: that may be a
quiet microphone rather than a broken one, and it is recorded either way (`MIC_CHECK`), so a call
that went ahead unconfirmed can be found afterwards.

**The assistant is not present until they ask for a review**, and it opens on the assessment
screen rather than taking them anywhere. The assessment stays on screen for the whole call, which
is the point: it is the thing being disputed, and the agent's script has them refer to its figures.
Sending the participant back to the portal dashboard to argue about numbers they can no longer see
would be the study getting in its own way.

It used to mount with the portal and open a voice session immediately, which meant an assistant
talking to someone who had not yet filed a claim, let alone seen anything to dispute.

## The design

2 (AI voicebot acknowledgement: none, acknowledged) × 2 (Human acknowledgement: none,
acknowledged), between subjects, N = 400.

Carried as **two booleans**, `aiAck` and `humanAck`, from the Qualtrics register call all the way
to the CSV export. Not one packed 0–3 code: the analysis is a 2×2 ANOVA and the factors have to
come out of the export as separate variables.

Randomisation is Qualtrics'. It already owns consent, screening and the survey, and it registers
the condition server-side before the participant is ever redirected here. This application
receives, stores and renders what it was told — it never assigns.

## Relationship to the pre-test

Both studies in this repository are **sibling consumers of the same portal library**, not forks of
each other. Both depend on `solutions.andreas:insurance-portal-core` and `@insurance-portal/core`
and plug into the same seams. Shared code lives in the library; everything else is deliberately
duplicated, because the two run in different windows and must be able to change independently.

That duplication is on purpose in three places:

- **Its own resource group and its own `platform.bicep`.** The pre-test's data has to survive this
  study being deployed, resized or deleted, and this study must not inherit a database server that
  a finished pre-test is about to have torn down. Sharing the platform would couple exactly the
  lifecycles that need to be independent, to save about twenty francs a month.
- **Its own database**, not just its own schema. Both name their study schema `behavioural`;
  sharing one database would put two different schemas and two Flyway histories on a collision
  course. Set in `study.properties`, overridable via `SPRING_DATASOURCE_URL`.
- **Its own copy of `containerapp.bicep` and the deploy scripts.** Generic files, copied rather
  than shared. If one changes, change both - a divergence would mean the two studies run on subtly
  different infrastructure without anyone deciding that they should.

What is deliberately *not* inherited: the integration levels from the levels-of-ai-help design.
The portal is interactive in every cell and the voice assistant is always present. Both
manipulations happen inside the call, so there is no readonly/headless mode, no voice-only page,
and no GUI-driving tools - the assistant's only tool is `transfer_to_human_agent`.

## Prerequisites

- Docker Desktop
- JDK 21
- Node 22+ / npm 10+

## 0. Registries and database

The portal is a dependency, not a module. Start its registries from the portal repository:

```
..\mtec-insurance-portal-core> docker compose -f docker/maven-registry.yaml up -d   # :8082
..\mtec-insurance-portal-core> docker compose -f docker/npm-registry.yaml   up -d   # :4873
```

Then the database, from here:

```
docker compose -f docker/postgres.yaml up -d    # start
docker compose -f docker/postgres.yaml down -v  # stop + wipe
```
→ localhost:5432 (db: `exp2_ai_human_handover`, user/pass: portal/portal)

Both schemas are created by Flyway: `insurance_portal` by the portal library, `behavioural` by this
application. Separate history tables, so dropping one leaves the other intact.

## 1. Locally

```
.\exp2-app>      $env:OPENAI_API_KEY = "sk-..."; .\mvnw.cmd spring-boot:run
.\exp2-frontend> npm run dev
```
→ http://localhost:5173/?t=&lt;handover token&gt;

There is no direct-URL condition path: the token is the only accepted handover, and an unknown or
expired one fails loud rather than opening a session. Register one first:

```
curl -X POST http://localhost:8080/api/handover \
  -H 'Content-Type: application/json' \
  -d '{"participantId":"test-1","aiAck":true,"humanAck":false,
       "name":"Alex Morgan","callbackUrl":"http://localhost:5173/done"}'
```

Both factors are required. An absent factor is a 400, never a silent `false`.

## Tests

```
.\exp2-app> .\mvnw.cmd test
```

`VoiceSessionCompositionTest` runs against the real `configuration/` files, not fixtures — it
exists to catch edits to the prompts. `ConditionIntakeTest` round-trips all four cells.
`AgentHandoverTest` covers the transfer.

## Wiring it into Qualtrics

[`QUALTRICS.md`](QUALTRICS.md): the randomiser for the 2×2, the web service that registers the
cell, the redirect chain, and what to branch on when a participant declines the review. Also what
to delete from the survey as it stands, now that the platform owns the assessment and the
escalation decision.

## Deploying to Azure

Full step-by-step, including creating the resource group: `docs/experiment-2/deployment.md`.

First, the secrets:

```
./scripts/init_env.sh
```

It creates `infra/.env`, generates the four secrets, and prints them once for your password manager
- the database password especially, which is created with the Postgres server and cannot be read
back out of Azure afterwards. It fills only empty keys, so a second run cannot replace a password
the deployed database is already using.

Then one command builds the frontend, builds and pushes the image, and deploys:

```
./scripts/release.sh
```

`scripts/enqueue.sh <participantId>` stands in for the assistant's `transfer_to_human_agent` call,
so the human leg can be tested without `OPENAI_API_KEY`. The participant needs an open session
first, which their page creates on load.

Then walk one cell of the 2×2 end to end, as a participant would:

```
./scripts/pilot.sh ack-ack
./scripts/pilot.sh --list     # the four cells
```

The deploy and the pilot both read `infra/.env`, so there is no URL or secret to paste by hand.

## Known gaps

- **Nothing has been through a real call yet.** Both ends are built - the console at `/agent`, the
  participant's hold-and-connect panel, and the Azure Communication Services leg between them - but
  a group call needs a deployed ACS resource and two browsers, so it has never actually connected.
  The first thing to do after a deploy is two tabs and a conversation with yourself.
- **`TRANSFER_GRACE_MS` in `CallStage.tsx` is a guess.** It is how long the assistant keeps talking
  after the transfer is registered, so it can finish "I'm putting you through now" before its
  session is torn down. Six seconds. Listen at the pilot for whether it is ever cut off, or whether
  there is dead air before the hold message.
- **The call runs on Azure Communication Services**, created by the deploy, with its data in
  Switzerland. Both browsers get a short-lived token and join a group call named by the handover;
  no audio passes through this application and none is stored. Chosen over our own WebRTC
  signalling plus a TURN server because it is an Azure service - the same Microsoft agreement the
  ethics application already rests on for Azure OpenAI - and because it handles media relay for
  participants on restrictive home networks.
- **The agent is never told the AI condition.** They see `humanAck`, their own instruction, and the
  transcript. They will often infer the rest and blinding is impossible; what this buys is
  consistency rather than blindness. `AgentConsoleTest` asserts no agent-facing payload carries the
  AI factor, so a later convenience field cannot quietly undo it.
- **The AI leg needs `OPENAI_API_KEY`** in `infra/.env`, minted server-side into an ephemeral
  token. Without it the portal works and the assistant cannot connect.
- **The portal's generated PDFs follow `portal.format.currency`**, which is set to GBP here - but
  that property only exists on the library's `claude/configurable-currency-and-dates` branch, which
  is not merged into the library's `main`. Rebuilding the library from `main` would silently take
  the PDFs back to CHF. Merging that branch is the fix.
