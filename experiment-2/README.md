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

## The design

2 (AI voicebot acknowledgement: none, acknowledged) × 2 (Human acknowledgement: none,
acknowledged), between subjects, N = 400.

Carried as **two booleans**, `aiAck` and `humanAck`, from the Qualtrics register call all the way
to the CSV export. Not one packed 0–3 code: the analysis is a 2×2 ANOVA and the factors have to
come out of the export as separate variables.

Randomisation is Qualtrics'. It already owns consent, screening and the survey, and it registers
the condition server-side before the participant is ever redirected here. This application
receives, stores and renders what it was told — it never assigns.

## Relationship to `levels-of-ai-help`

This is a **sibling consumer** of the same portal library, not a fork of that study. Both depend on
`solutions.andreas:insurance-portal-core` and `@insurance-portal/core` and plug into the same five
seams. Experiment 3 has to stay deployable and runnable while this is built, so the two release
independently.

Two consequences:

- **`levels-of-ai-help` owns `platform.bicep`.** This deployable references the same Azure
  resources read-only through its own `platform-existing.bicep`, exactly as the portal repo does.
  Only one repository may own them.
- **This study has its own database**, not just its own schema. Both name their study schema
  `behavioural`; sharing one database would put two different schemas and two Flyway histories on
  a collision course. Set in `study.properties`, overridable via `SPRING_DATASOURCE_URL`.

What is deliberately *not* inherited: the integration levels. The portal is interactive in every
cell and the voice assistant is always present. Both manipulations happen inside the call, so
there is no readonly/headless mode, no voice-only page, and no GUI-driving tools — the assistant's
only tool is `transfer_to_human_agent`.

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

## Known gaps

- **`GET /api/agent/queue` is unauthenticated**, as are `/data` and `/api/admin/*` (inherited).
  That was acceptable for a study run on a supplied laptop in scheduled sessions; it is not
  acceptable here, where the participant link is public on Prolific and the queue exposes live
  participant context. Must be behind auth before any real run.
- **The agent console does not exist yet.** The queue endpoint is its backend; the console UI and
  the participant↔agent voice leg (WebRTC signalling, STUN/TURN) are still to build.
- **The portal is Swiss and the participants are British.** `format.ts` and the two PDF services
  render CHF; the briefing is in £. See `docs/experiment-2/implementation-plan.md` §6a.
