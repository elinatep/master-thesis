# Deploying experiment 2

Step by step, in order. Everything is paste-able; the lines that need a decision from you are
marked **you choose** or **you need to get this**.

There are two things only you can supply: the resource group (one command) and the OpenAI key
(someone has to give it to you). Everything else is scripted.

---

## First: new resource group, or reuse the pre-test's?

**A new one.** `rg-exp2-feeling-heard`, separate from the pre-test's `rg-e2-feeling-heard`.

Not because it is tidier - because of what sharing would couple:

- The pre-test's behavioural data lives in that resource group's Postgres server and **nowhere
  else**. If the two studies share a group, every `az group delete`, every resize, every "let me
  just clear this out" touches both.
- The main experiment runs for weeks with scheduled sessions. A database server restarted for one
  study lands on a participant mid-call in the other.
- It costs about twenty francs a month more. That is the whole saving sharing would buy.

`scripts/deploy.sh` refuses to run if `infra/.env` names the pre-test's group, because copying the
pre-test's `.env` as a starting point and not changing the first line is the obvious way to end up
there by accident.

### Do not wipe the pre-test yet

Deleting `rg-e2-feeling-heard` deletes the database with it, and Azure's own automated backups live
*inside* that resource group, so they go too. The admin password is in `pre-test/infra/.env` and
nowhere else - Azure cannot show it to you again.

From `pre-test/`:

```bash
./scripts/backup_db.sh --check   # what is on the server, writes nothing
./scripts/backup_db.sh           # dump + plain SQL + the behavioural log, into data/
```

That takes the portal schema as well as the behavioural one, which the `/data` page does not - the
claims participants actually filed live in the portal's tables, and "what did they type into the
claim form" is a question the write-up may need. It verifies the dump reads back before reporting
success.

Then copy those files somewhere that is actually backed up. Only then is deleting the group a
reasonable thing to do - and it is worth doing, at roughly 50-60 CHF a month.

---

## 1. Be logged in, with the role active

```bash
az login
az account show --output table
```

If the subscription is wrong:

```bash
az account list --output table
az account set --subscription "<the right one>"
```

If a deploy later fails with `AuthorizationFailed`, the PIM role has expired. Activate it in the
portal (Entra ID → Privileged Identity Management → My roles → Azure resource roles), then clear
the cached token, which does not notice the new role on its own:

```bash
az account clear && az login
```

## 2. Create the resource group

Put it in the same region as the pre-test. Check which that is:

```bash
az group show --name rg-e2-feeling-heard --query location --output tsv
```

Then (substituting that region if it differs):

```bash
az group create --name rg-exp2-feeling-heard --location switzerlandnorth
```

Switzerland North matters: the ethics application commits to holding research data on ETH
infrastructure.

## 3. Fill in `infra/.env`

```bash
cd experiment-2
cp infra/.env.example infra/.env
```

Then open `infra/.env` and set five values. The file explains each one in place; in short:

| Key | What to put |
| --- | --- |
| `RESOURCE_GROUP` | `rg-exp2-feeling-heard` (already there) |
| `DB_ADMIN_PASSWORD` | **you choose.** 8-128 chars, 3 of upper/lower/digit/special. Save it somewhere you will still have in March - it cannot be read back out of Azure |
| `HANDOVER_SECRET` | `uuidgen` and paste the result. A **different** value from the pre-test's |
| `RESEARCHER_PASSWORD` | **you choose.** The password on `/data` and on the endpoints that can delete the study |
| `AGENT_PASSWORD` | **you choose.** What the confederate signs in with. Different from the researcher's - that separation is what stops a misclick ending the study |
| `OPENAI_API_KEY` | **you need to get this.** See below |

```bash
uuidgen    # for HANDOVER_SECRET
```

**Do not paste any of these into a chat, an email or a commit.** `infra/.env` is gitignored, and
that is the only place they should exist outside your password manager.

### The OpenAI key

The app defaults to the MTEC Azure OpenAI deployment
(`https://mtec-insurance-portal.openai.azure.com/openai/v1`). You need a key for it - ask Andreas
or Joe.

It has to be an **Azure** OpenAI resource, not `api.openai.com`. The ethics application rests on
ETH's no-data-retention contract with Microsoft, so where the call audio goes is not a free choice.
If you do stand up your own, create an Azure OpenAI resource with a Realtime model deployed and set
both `OPENAI_API_KEY` and `OPENAI_BASE_URL`.

Without it, everything deploys and works except the voice: the participant watches the assistant
fail to connect. So this is not optional for a session, but it does not block getting the
deployment up today.

## 4. Start the two local registries

The portal library is a dependency resolved from registries on your own machine, so they have to be
running before anything builds. From the **library** repository:

```bash
docker compose -f docker/maven-registry.yaml up -d   # :8082
docker compose -f docker/npm-registry.yaml   up -d   # :4873
```

The frontend needs `@insurance-portal/core` **0.8.0 or later** published there - that is the version
with `configureFormat`, which is what makes the portal show pounds instead of francs. The pre-test
already uses it, so if the pre-test builds, this will.

## 5. Update the frontend lockfile once

`exp2-frontend` was pinned to `^0.7.1` and is now on `^0.8.0`, so the lockfile needs refreshing:

```bash
cd exp2-frontend && npm install && cd ..
git add exp2-frontend/package-lock.json && git commit -m "chore(exp2): refresh the lockfile"
```

Worth committing before deploying: the image is tagged with the git SHA, and `release.sh` warns
when the tree is dirty because a dirty tree means the tag no longer identifies what shipped.

## 6. Deploy

```bash
./scripts/release.sh
```

The first run takes about fifteen minutes and does more than later runs: there is no registry to
push an image to yet, so it creates the platform (registry, database server, Container Apps
environment) first, then builds, pushes and deploys. Later runs skip straight to the build and take
two or three minutes.

It finishes by printing the app URL in a box. If it does not, the deploy failed - read upwards.

## 7. Check it is actually up

Open the URL. You should get an **error page**, not the portal:

> Could not start from the study handover: no handover token in the URL.

That is correct. Nobody enters this study except through Qualtrics.

Then walk one cell end to end, as a participant would:

```bash
./scripts/pilot.sh ack-ack
```

That registers a handover, prints the entry link and opens it. You should land on the Salvena
dashboard - in pounds, with a Manchester address - file the claim, and be able to start the voice
assistant.

```bash
./scripts/pilot.sh --list     # the four cells
./scripts/pilot.sh --url      # just the app URL
```

Check what was recorded at `<appUrl>/data`, with the researcher username and password from
`infra/.env`.

## 8. What works and what does not, at this point

Works:

- the portal, in pounds, with the British content and a seeded account that has no claims yet
- the Qualtrics handover in and the handback out, with both factors carried as booleans
- behavioural logging and the transcript tables
- the AI voice leg, if `OPENAI_API_KEY` is set
- `/data`, `/api/admin/**` and `/api/agent/queue` behind their passwords

- the whole server side of the handover: the queue, the brief the agent reads before picking up,
  claiming and ending a call, and the tokens both sides use to join it
- Azure Communication Services, created by the deploy, holding its data in Switzerland - one more
  resource in the group, billed per participant-minute and nothing while idle

Does not work yet:

- **the agent console's page, and the participant's waiting screen.** Everything they talk to is
  built and tested; the two UIs are not. That is the next piece of work, and it is the half of the
  study the confederate scripts in `configuration/human/` are written for.

---

## Resetting between pilot runs

Pilot data has to be out of the database before real participants arrive, and `/data` exports it,
so "I will tidy it later" means exporting a file with pilot rows in it.

The admin endpoints do it, and they now need the researcher credential:

```bash
curl -u researcher:<your researcher password> -X POST "$(./scripts/pilot.sh --url | tail -1)/api/admin/reset/all"
```

That deletes every behavioural log, every transcript and every seeded participant account. It does
not touch the schema, so the next participant is re-seeded on arrival.

## If something goes wrong

| Symptom | Cause |
| --- | --- |
| `AuthorizationFailed` on deploy | PIM role expired, or `az` is holding a cached token. §1 |
| `401` from `pilot.sh` | The secret in `infra/.env` differs from the one the app was deployed with. Redeploy with `./scripts/release.sh` |
| `000` from `pilot.sh` | The app is still starting after a deploy. Wait a minute |
| Blocked mirror / cannot resolve `insurance-portal-core` | The local registries are not running. §4 |
| `$color1: null is not a color` during the build | The frontend is being built inside Docker instead of on the host. `release.sh` builds it on the host for exactly this reason - do not move it |
| The assistant will not connect | `OPENAI_API_KEY` was empty at the last deploy. Check the browser console for the failed `/api/voice/token` |
| The portal shows CHF | The library was rebuilt from `main`, which does not have the currency change. See the last Known gap in `experiment-2/README.md` |
