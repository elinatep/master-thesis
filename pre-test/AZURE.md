# Deploying to Azure

## The short version

There is almost nothing to click. You already did the only portal step — creating the resource
group. Everything else is three commands in a terminal. The Azure portal is then just somewhere to
*look at* what got made.

**About the password: you invent it.** Nothing to look up. This deployment creates its own
PostgreSQL server, and the password you put in the file below is the password it gets created
with. Pick one, save it in your password manager, and you will not need to think about it again.

---

## Before you start (one-off)

Install these on your own machine:

| What | Where | Check it worked |
| --- | --- | --- |
| **Azure CLI** | https://aka.ms/installazurecliwindows | `az version` |
| **Docker Desktop** | https://docker.com/products/docker-desktop | `docker version` |
| **Git** | you already have it | `git --version` |

Then sign in and point the CLI at the right subscription:

```powershell
az login
az account list --output table          # find the subscription your resource group is in
az account set --subscription "<the SubscriptionId from that table>"
```

One-off, and only if the deploy later complains about a provider not being registered:

```powershell
az provider register --namespace Microsoft.App
az provider register --namespace Microsoft.ContainerRegistry
az provider register --namespace Microsoft.DBforPostgreSQL
az provider register --namespace Microsoft.OperationalInsights
```

---

## Step 1 — make the `.env` file

Yes, a separate file, and it is **not** committed (it holds secrets — `.gitignore` already excludes
it). It lives at `pre-test/infra/.env`.

In the `pre-test` folder:

```powershell
Copy-Item infra\.env.example infra\.env
notepad infra\.env
```

Fill in exactly three lines and leave everything else as it is:

```
RESOURCE_GROUP=rg-e2-feeling-heard

DB_ADMIN_PASSWORD=<invent one - see below>

HANDOVER_SECRET=<invent one - see below>
```

**`DB_ADMIN_PASSWORD`** — you choose it. 8–128 characters, with at least three of: upper case,
lower case, digit, special character. Avoid `$`, backtick, `"` and `\`, which the shell mangles.
Something like `Salvena-Pretest-2026!` is fine. Save it — after the first deploy the server keeps
this password, and changing it later is a separate chore.

**`HANDOVER_SECRET`** — any random string. Generate one with:

```powershell
[guid]::NewGuid().ToString()
```

This is the password Qualtrics sends when it tells the platform which arm a participant is in.
Without it, anyone who finds the study URL could enter in whichever arm they liked, and those
responses would be indistinguishable from real ones in your data. You will paste this same value
into Qualtrics later (see `QUALTRICS.md`).

Leave `AUTH_CLIENT_ID` and `AUTH_CLIENT_SECRET` **empty**. Filling them in puts an ETH login in
front of the study, which blocks Prolific participants.

---

## Step 2 — start the portal's two registries

The insurance portal is a library this study depends on, and it is served from two small local
servers. They must be running for the build, because the build fetches the portal from them.

In the **portal repository** (`mtec-insurance-portal-core`, not this one):

```powershell
docker compose -f docker/maven-registry.yaml up -d
docker compose -f docker/npm-registry.yaml   up -d
```

Leave them running. If you have never published the portal to them, do that first — that is the
portal repo's own README.

---

## Step 3 — deploy

Back in `pre-test`:

```powershell
.\scripts\release.ps1
```

The first run takes **10–15 minutes** and does this, in order:

1. Sees the resource group is empty, so it creates the platform first — a container registry, a
   PostgreSQL server, a Container Apps environment, a managed identity and a log workspace. This
   is the slow part (the database server is most of it).
2. Builds the study into a Docker image, tagged with the current git commit.
3. Pushes that image to the registry it just created.
4. Deploys the app.

Later runs skip step 1 and take two or three minutes.

When it finishes it prints a table. The line you want is **`appUrl`** — that is your study.

---

## Step 4 — check it worked

Open `<appUrl>` in a browser. You should get a **fail-loud error page**, not the portal:

> Can't start the session — Could not start from the study handover: no handover token in the URL.

That is correct and is the point: there is no way into the study except through Qualtrics with a
valid token. If you see the Salvena dashboard instead, something is wrong.

Now make yourself a token and walk through one arm:

```powershell
$body = '{"participantId":"pilot-1","arm":"S1","name":"Joe Smith","callbackUrl":"https://mtecethz.eu.qualtrics.com"}'
$r = Invoke-RestMethod -Method Post -Uri "<appUrl>/api/handover" `
       -ContentType "application/json" `
       -Headers @{ "X-Handover-Secret" = "<your HANDOVER_SECRET>" } `
       -Body $body
Start-Process "<appUrl>/?t=$($r.token)"
```

Swap `"arm":"S1"` for `S0`, `S2` or `S4` to try the others.

Then open `<appUrl>/data` — every click and timestamp, with a CSV export. For an S4 run, check
there are exactly **two** `CLAIM_ATTEMPT` rows under **one** session.

---

## What you will see in the Azure portal

Open the resource group and you will find six things. You do not need to touch any of them:

| Resource | What it is |
| --- | --- |
| Container App `pretest-feeling-heard` | the study itself |
| Container Apps Environment | the thing it runs inside |
| Container Registry `acr…` | where the built image is stored |
| PostgreSQL flexible server `pg…` | the database |
| Managed Identity `id-…` | lets the app pull its image without a password |
| Log Analytics workspace `log-…` | where the app's logs go |

To read the app's logs: Container App → **Monitoring** → **Log stream**.

**Cost.** The database server is the bulk of it and bills whether or not anyone is using it. When
the pre-test is finished, delete the whole resource group — that is the clean way to stop paying.

---

## When something goes wrong

**`release.ps1` fails while building** — the registries from Step 2 are not running, or the portal
has not been published to them. This is the most common failure.

**"The subscription is not registered to use namespace…"** — run the `az provider register`
commands above, wait a minute, try again.

**Deploy fails on the password** — it broke one of the rules: 8–128 characters, three of
upper/lower/digit/special, and it must not contain the word `portaladmin`.

**The app URL loads but shows an error about the database** — the container usually starts before
the database is reachable on a first deploy. Wait a minute and reload; if it persists, check
Log stream.

**Participants see an ETH login page** — `AUTH_CLIENT_ID` / `AUTH_CLIENT_SECRET` are filled in.
Empty them and redeploy.

Paste me whatever the terminal prints and I will tell you what it means.
