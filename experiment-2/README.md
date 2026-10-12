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

## Deploying to Azure

Full step-by-step, including creating the resource group: `docs/experiment-2/deployment.md`.

Once `infra/.env` exists, one command builds the frontend, builds and pushes the image, and
deploys:

```
./scripts/release.sh
```

Then walk one cell of the 2×2 end to end, as a participant would:

```
./scripts/pilot.sh ack-ack
./scripts/pilot.sh --list     # the four cells
```

The deploy and the pilot both read `infra/.env`, so there is no URL or secret to paste by hand.

## Known gaps

- **The agent console does not exist yet.** `GET /api/agent/queue` is its backend and is behind the
  agent credential; the console UI and the participant↔agent voice leg (WebRTC signalling,
  STUN/TURN) are still to build. This is the next piece of work.
- **The AI leg needs `OPENAI_API_KEY`** in `infra/.env`, minted server-side into an ephemeral
  token. Without it the portal works and the assistant cannot connect.
- **The portal's generated PDFs follow `portal.format.currency`**, which is set to GBP here - but
  that property only exists on the library's `claude/configurable-currency-and-dates` branch, which
  is not merged into the library's `main`. Rebuilding the library from `main` would silently take
  the PDFs back to CHF. Merging that branch is the fix.
