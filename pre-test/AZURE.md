# Deploying to Azure

Written for **macOS**. There are PowerShell copies of every script (`.ps1`) for Windows — same
names, same behaviour.

## The short version

There is almost nothing to click. You already did the only portal step — creating the resource
group. The rest is three commands in Terminal. The Azure portal is then just somewhere to *look
at* what got made.

**About the database password: you invent it.** There is nothing to look up. This deployment
creates its own PostgreSQL server, and the password you put in the file below is the password it
gets created with. Pick one, save it in your password manager, done.

---

## Before you start (one-off)

Open **Terminal** (Cmd+Space, type "Terminal"). If you do not have Homebrew, get it from
https://brew.sh, then:

```bash
brew install azure-cli
brew install --cask docker     # then open Docker Desktop once, so it is running
```

Check both work:

```bash
az version
docker version
```

Sign in and pick the subscription your resource group is in:

```bash
az login
az account list --output table
az account set --subscription "<the SubscriptionId from that table>"
```

Only if a deploy later complains that a provider is not registered:

```bash
az provider register --namespace Microsoft.App
az provider register --namespace Microsoft.ContainerRegistry
az provider register --namespace Microsoft.DBforPostgreSQL
az provider register --namespace Microsoft.OperationalInsights
```

---

## Step 1 — make the `.env` file

Yes, a separate file, and it is **not** committed — it holds secrets, and git already ignores it.
It lives at `pre-test/infra/.env`.

From the `pre-test` folder:

```bash
cp infra/.env.example infra/.env
open -e infra/.env          # opens it in TextEdit
```

Fill in three lines and leave the rest as they are:

```
RESOURCE_GROUP=rg-e2-feeling-heard

DB_ADMIN_PASSWORD=<invent one - see below>

HANDOVER_SECRET=<invent one - see below>
```

**`DB_ADMIN_PASSWORD`** — you choose it. 8–128 characters, with at least three of: upper case,
lower case, digit, special character. Avoid `$`, backtick, `"` and `\`. Something like
`Salvena-Pretest-2026!` is fine. **Save it** — after the first deploy the server keeps this
password, and changing it afterwards is a separate chore.

**`HANDOVER_SECRET`** — any random string:

```bash
uuidgen
```

This is the password Qualtrics sends when it tells the platform which arm a participant is in.
Without it, anyone who finds the study URL can enter in whichever arm they like, and those
responses are indistinguishable from real ones in your data. You will paste this same value into
Qualtrics later (see `QUALTRICS.md`).

Leave `AUTH_CLIENT_ID` and `AUTH_CLIENT_SECRET` **empty**. Filling them in puts an ETH login in
front of the study, which blocks Prolific participants and breaks the Qualtrics iframe.

---

## Step 2 — start the portal's two registries

The insurance portal is a library this study depends on, served from two small local servers. They
must be running before the build, because the build fetches the portal from them.

In the **portal repository** (`mtec-insurance-portal-core`, not this one):

```bash
docker compose -f docker/maven-registry.yaml up -d
docker compose -f docker/npm-registry.yaml   up -d
```

Leave them running. If you have never published the portal into them, do that first — that is the
portal repo's own README.

---

## Step 3 — deploy

Back in `pre-test`:

```bash
./scripts/release.sh
```

The first run takes **10–15 minutes** and, in order:

1. Sees the resource group is empty, so creates the platform first — a container registry, a
   PostgreSQL server, a Container Apps environment, a managed identity and a log workspace. The
   database server is most of that time.
2. Builds the study into a Docker image tagged with the current git commit.
3. Pushes it to the registry it just made.
4. Deploys the app.

Later runs skip step 1 and take two or three minutes.

It prints a table at the end. The line you want is **`appUrl`** — that is your study.

> On an Apple Silicon Mac the build is forced to `linux/amd64`. Azure Container Apps cannot run an
> arm64 image, and the symptom is a container that starts and immediately dies. The script handles
> this; it just means the build is a little slower than a native one.

---

## Step 4 — check it worked

Open `<appUrl>` in a browser. You should get a **fail-loud error page**, not the portal:

> Can't start the session — Could not start from the study handover: no handover token in the URL.

That is correct, and is the point: there is no way into the study except through Qualtrics with a
valid token. If you see the Salvena dashboard instead, something is wrong.

Now make a token and walk one arm yourself:

```bash
APP="<appUrl>"
SECRET="<your HANDOVER_SECRET>"

TOKEN=$(curl -s -X POST "$APP/api/handover" \
  -H 'Content-Type: application/json' \
  -H "X-Handover-Secret: $SECRET" \
  -d '{"participantId":"pilot-1","arm":"S1","name":"Joe Smith","callbackUrl":"https://mtecethz.eu.qualtrics.com"}' \
  | python3 -c 'import sys,json; print(json.load(sys.stdin)["token"])')

open "$APP/?t=$TOKEN"
```

Swap `"arm":"S1"` for `S0`, `S2` or `S4` to try the others. Use a different `participantId` each
time, or you will resume the previous run instead of starting a fresh one.

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

**`release.sh` fails while building** — the registries from Step 2 are not running, or the portal
has never been published into them. This is by far the most common failure.

**`permission denied: ./scripts/release.sh`** — `chmod +x scripts/*.sh`.

**"The subscription is not registered to use namespace…"** — run the `az provider register`
commands above, wait a minute, try again.

**The deploy rejects the password** — it broke a rule: 8–128 characters, three of
upper/lower/digit/special, and it must not contain the admin username (`portaladmin`).

**`Cannot connect to the Docker daemon`** — Docker Desktop is not running. Open it and wait for
the whale in the menu bar to settle.

**The app URL loads but errors about the database** — on a first deploy the container often starts
before the database is reachable. Wait a minute and reload; if it persists, check Log stream.

**Participants see an ETH login page** — `AUTH_CLIENT_ID` / `AUTH_CLIENT_SECRET` are filled in.
Empty them and redeploy.

Paste me whatever Terminal prints and I will tell you what it means.
