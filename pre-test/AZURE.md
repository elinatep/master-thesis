# Deploying to Azure

Written for **macOS**. There are PowerShell copies of every script (`.ps1`) for Windows — same
names, same behaviour.

## The short version

There is almost nothing to click in Azure. You already did the only portal step — creating the
resource group. Deploying is one command; the Azure portal is then just somewhere to *look at* what
got made.

The setup before that first deploy is the long part, and it is all one-off: install a few tools,
clone two repositories, and publish the insurance portal into two local registries so the build can
find it. Budget an hour for the first time and ten minutes for every time after.

**About the database password: you invent it.** There is nothing to look up. This deployment
creates its own PostgreSQL server, and the password you put in the file in Step 1 is the password
it gets created with. Pick one, save it in your password manager, done.

---

## Before you start (one-off)

Open **Terminal**: Cmd+Space, type `Terminal`, Enter.

### a. Homebrew

macOS does not ship with it. Run:

```bash
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
```

It asks for your Mac login password (the prompt shows nothing as you type — that is normal), and
may install Apple's Command Line Tools first, which can take ten minutes.

**Then read the last few lines it prints.** On an Apple Silicon Mac it finishes with a "Next steps"
section telling you to run two commands. You must run them, or `brew` still will not be found:

```bash
echo >> ~/.zprofile
echo 'eval "$(/opt/homebrew/bin/brew shellenv)"' >> ~/.zprofile
eval "$(/opt/homebrew/bin/brew shellenv)"
```

Check it took:

```bash
brew --version
```

### b. Azure CLI and Docker

```bash
brew install azure-cli
brew install --cask docker
```

Then **open Docker Desktop from Applications** and let it finish starting — the whale icon in the
menu bar stops animating. Installing it is not enough; it has to be running.

Check both:

```bash
az version
docker version
```

> **Prefer not to install Homebrew?** Docker Desktop has a direct download at
> https://docker.com/products/docker-desktop — pick the Apple Silicon or Intel build to match your
> Mac (Apple menu → About This Mac). The Azure CLI has no supported standalone macOS installer,
> though, so Homebrew is the practical route for that one.

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

### c. JDK 21 and Node

Needed to publish the portal library in Step 0 (the deployment itself builds inside Docker, but
populating the registries happens on your machine):

```bash
brew install openjdk@21 node
sudo ln -sfn /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk \
             /Library/Java/JavaVirtualMachines/openjdk-21.jdk
```

Check: `java -version` shows 21, `node -v` shows 22 or higher.

---

## Step 0 — get the code, and publish the portal library

You need **two** repositories. This study is a consumer of the insurance portal, which is a
separate library — the build fetches it from two small registries running on your own machine
rather than from your working tree.

### a. Clone both

```bash
mkdir -p ~/eth && cd ~/eth

git clone https://github.com/elinatep/master-thesis.git
git clone https://github.com/elinatep/insurance-portal-core.git

cd master-thesis
git checkout claude/platform-experiment-2-integration-cplg9e
```

Everything from here happens in `~/eth/master-thesis/pre-test`, except Step 0b–d, which is in
`~/eth/insurance-portal-core`.

### b. Start the two registries

```bash
cd ~/eth/insurance-portal-core
docker compose -f docker/maven-registry.yaml up -d   # Reposilite, :8082
docker compose -f docker/npm-registry.yaml   up -d   # Verdaccio,  :4873
```

Leave them running. They hold the published portal; the Docker build reaches them at
`host.docker.internal`.

### c. Registry credentials (one-off per machine)

Maven — create or edit `~/.m2/settings.xml` so it contains:

```xml
<settings>
  <servers>
    <server>
      <id>local-maven-registry</id>
      <username>publisher</username>
      <password>publisher</password>
    </server>
  </servers>
</settings>
```

(`mkdir -p ~/.m2 && open -e ~/.m2/settings.xml` — if the file already exists, add the `<server>`
block inside its existing `<servers>`.)

npm — one signup, which writes a token to `~/.npmrc`:

```bash
npm adduser --registry http://host.docker.internal:4873
```

Any username, password and email will do; it is a local registry.

### d. Publish the portal into them

```bash
cd ~/eth/insurance-portal-core/insurance-portal-core
./mvnw clean deploy -DskipTests

cd ../frontend-core
npm install
npm run build
npm publish
```

`-DskipTests` because you are publishing a dependency, not developing it; the portal's own tests
want a database that you do not otherwise need.

**Redo this step whenever the portal changes.** The registries hold a published copy, not your
working tree, so a portal fix that has not been re-published is invisible to this study's build.

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

## Step 2 — deploy

Back in `pre-test`:

```bash
./scripts/release.sh
```

Make sure the two registries from Step 0b are still running — this is where a stopped one bites.

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

## Step 3 — check it worked

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

**`release.sh` fails while building**, with Maven or npm unable to find `insurance-portal-core` or
`@insurance-portal/core` — the registries from Step 0b are not running, or the portal has not been
published into them (Step 0d). This is by far the most common failure, and it is also what happens
after a Docker restart, which stops the registry containers. Bring them back up with the same
`docker compose … up -d` commands; the published copies survive in their volumes.

**`permission denied: ./scripts/release.sh`** — `chmod +x scripts/*.sh`.

**"The subscription is not registered to use namespace…"** — run the `az provider register`
commands above, wait a minute, try again.

**The deploy rejects the password** — it broke a rule: 8–128 characters, three of
upper/lower/digit/special, and it must not contain the admin username (`portaladmin`).

**`failed to connect to the docker API at unix:///var/run/docker.sock`** — you have the Docker
client but no running daemon. Check which:

```bash
ls -d /Applications/Docker.app
```

If it exists, Docker Desktop is just not started — `open -a Docker`, then wait for the whale icon
in the menu bar to stop animating. If it does not exist, `brew install docker` was run instead of
`brew install --cask docker`: the plain formula installs only the command-line client. Install the
cask and open it.

**The app URL loads but errors about the database** — on a first deploy the container often starts
before the database is reachable. Wait a minute and reload; if it persists, check Log stream.

**Participants see an ETH login page** — `AUTH_CLIENT_ID` / `AUTH_CLIENT_SECRET` are filled in.
Empty them and redeploy.

Paste me whatever Terminal prints and I will tell you what it means.
