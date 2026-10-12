#!/usr/bin/env bash
# deploy.sh - deploy experiment 2, loading parameters from infra/.env
#
#   ./scripts/deploy.sh --image <acr>.azurecr.io/exp2-feeling-heard:<sha>
#   ./scripts/deploy.sh --platform-only
#
# Resource-group scoped: the group must already exist, and this never owns it. Everything inside it
# belongs to this study alone - it does not share the pre-test's platform.
#
# Prefer ./scripts/release.sh, which builds and tags the image before calling this.

set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
env_file="$root/infra/.env"
app_name="exp2-feeling-heard"
image=""
resource_group=""
platform_only=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --image)          image="$2"; shift 2 ;;
    --resource-group) resource_group="$2"; shift 2 ;;
    --env-file)       env_file="$2"; shift 2 ;;
    --platform-only)  platform_only=1; shift ;;
    -h|--help)        sed -n '2,11p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "Unknown argument: $1" >&2; exit 2 ;;
  esac
done

if [[ ! -f "$env_file" ]]; then
  echo "Env file not found: $env_file" >&2
  echo "Copy infra/.env.example to infra/.env and fill it in." >&2
  exit 1
fi

# Read KEY=VALUE lines, ignoring blanks and comments. Values are read as-is, so a password
# containing spaces or '=' survives.
read_var() {
  sed -n "s/^[[:space:]]*$1[[:space:]]*=//p" "$env_file" | head -n1 | sed 's/[[:space:]]*$//'
}

# Whether the group was named on the command line matters below: an explicit flag is taken at
# face value, a value sitting in .env is not.
group_from_flag=0
if [[ -n "$resource_group" ]]; then group_from_flag=1; fi
[[ -n "$resource_group" ]] || resource_group="$(read_var RESOURCE_GROUP)"
[[ -n "$resource_group" ]] || resource_group="rg-exp2-feeling-heard"

# Refuse to deploy into the pre-test's group by accident. These are separate studies with separate
# databases, and the likeliest way to get there is copying the pre-test's infra/.env as a starting
# point and not changing the first line. The result would be a second Postgres server beside the
# pre-test's, billed for, with two near-identically named apps in one group - and the pre-test's
# data one careless `az group delete` away from the thing you were trying to keep.
if [[ "$resource_group" == "rg-e2-feeling-heard" && $group_from_flag -eq 0 ]]; then
  echo "RESOURCE_GROUP in $env_file is '$resource_group' - the PRE-TEST's resource group." >&2
  echo >&2
  echo "Experiment 2 deploys into its own group; infra/.env.example says why. Either fix that" >&2
  echo "line, or pass --resource-group $resource_group if you really do mean this one." >&2
  exit 1
fi

db_password="$(read_var DB_ADMIN_PASSWORD)"
if [[ -z "$db_password" ]]; then
  echo "DB_ADMIN_PASSWORD is missing or empty in $env_file." >&2
  echo "You choose it: this deployment creates its own Postgres server with that password." >&2
  echo "8-128 characters, at least 3 of upper/lower/digit/special." >&2
  exit 1
fi

if [[ $platform_only -eq 0 && -z "$image" ]]; then
  echo "--image is required. Run ./scripts/release.sh, which builds and tags it for you." >&2
  exit 1
fi

# One array element per parameter, so special characters in the password stay intact.
params=( "dbAdminPassword=$db_password" )

if [[ $platform_only -eq 1 ]]; then
  echo "Platform only: creating the registry, database server and environment (no app yet)."
  params+=( "deployApp=false" )
else
  params+=( "containerImage=$image" )
fi

db_user="$(read_var DB_ADMIN_USER)"
[[ -n "$db_user" ]] && params+=( "dbAdminUser=$db_user" )

handover_secret="$(read_var HANDOVER_SECRET)"
if [[ -n "$handover_secret" ]]; then
  echo "Handover secret:      SET (Qualtrics must send X-Handover-Secret)"
  params+=( "handoverSecret=$handover_secret" )
else
  echo "WARNING: handover secret is EMPTY. Anyone who finds the URL can mint a token and enter the"
  echo "         study in a cell of their choosing, and those responses look exactly like real ones."
  echo "         Set HANDOVER_SECRET in infra/.env before a real run."
fi

researcher_password="$(read_var RESEARCHER_PASSWORD)"
if [[ -n "$researcher_password" ]]; then
  echo "Researcher view:      PASSWORD SET (/data, /api/study/log, /api/admin)"
  params+=( "researcherPassword=$researcher_password" )
else
  echo "Researcher view:      CLOSED (no RESEARCHER_PASSWORD - /data and /api/admin refuse everyone)"
fi

researcher_user="$(read_var RESEARCHER_USER)"
[[ -n "$researcher_user" ]] && params+=( "researcherUser=$researcher_user" )

agent_password="$(read_var AGENT_PASSWORD)"
if [[ -n "$agent_password" ]]; then
  echo "Agent console:        PASSWORD SET"
  params+=( "agentPassword=$agent_password" )
else
  echo "WARNING: AGENT_PASSWORD is empty, so the agent console is CLOSED. The AI leg still works,"
  echo "         but nobody can pick up a handover - which is the second half of the study."
fi

agent_user="$(read_var AGENT_USER)"
[[ -n "$agent_user" ]] && params+=( "agentUser=$agent_user" )

openai_key="$(read_var OPENAI_API_KEY)"
if [[ -n "$openai_key" ]]; then
  echo "OpenAI Realtime key:  SET"
  params+=( "openAiApiKey=$openai_key" )
else
  echo "WARNING: OPENAI_API_KEY is empty. The portal will work and the voice will NOT: the"
  echo "         participant watches the assistant fail to connect. Set it before a session."
fi

openai_base_url="$(read_var OPENAI_BASE_URL)"
if [[ -n "$openai_base_url" ]]; then
  echo "OpenAI base URL:      $openai_base_url"
  params+=( "openAiBaseUrl=$openai_base_url" )
fi

auth_client_id="$(read_var AUTH_CLIENT_ID)"
auth_client_secret="$(read_var AUTH_CLIENT_SECRET)"
if [[ -n "$auth_client_id" && -n "$auth_client_secret" ]]; then
  echo "WARNING: Easy Auth is ON. This blocks anonymous Prolific participants and breaks the"
  echo "         Qualtrics iframe. Clear both AUTH_ values before a real run."
  params+=( "authClientId=$auth_client_id" "authClientSecret=$auth_client_secret" )
else
  echo "Easy Auth:            OFF (anonymous access - the real study mode)"
fi

echo "==> Deploying to resource group $resource_group"

# Ask for the outputs as JSON, not as a table. `--output table` on a deployment's outputs object
# prints an empty table: each output is itself an object ({type, value}), and the table renderer
# has no columns to make of that. The pre-test's first successful deploy finished without ever
# showing the app URL because of it.
outputs="$(az deployment group create \
  --name "$app_name" \
  --resource-group "$resource_group" \
  --template-file "$root/infra/main.bicep" \
  --parameters "${params[@]}" \
  --query "properties.outputs" \
  --output json)"

# Pull one output's value out of that JSON without depending on jq being installed.
output_value() {
  printf '%s' "$outputs" | python3 -c '
import json, sys
name = sys.argv[1]
try:
    data = json.load(sys.stdin)
except ValueError:
    sys.exit(0)
entry = (data or {}).get(name) or {}
value = entry.get("value")
if value:
    print(value)
' "$1" 2>/dev/null
}

app_url="$(output_value appUrl)"

echo
if [[ -n "$app_url" ]]; then
  echo "-------------------------------------------------------------------------------"
  echo " Experiment 2 is live at:"
  echo
  echo "   $app_url"
  echo
  echo " Open it in a browser and you should get an ERROR page, not the portal:"
  echo '   "Could not start from the study handover: no handover token in the URL."'
  echo " That is correct - nobody enters this study except through Qualtrics."
  echo
  echo " Qualtrics posts participants to:  $(output_value handoverRegisterUrl)"
  echo " Your data is at:                  $(output_value researcherDataUrl)"
  echo
  echo " Next: ./scripts/pilot.sh ack-ack   walks one cell end to end, as a participant."
  echo "-------------------------------------------------------------------------------"
else
  echo "Deployment finished, but no appUrl output came back."
  if [[ $platform_only -eq 1 ]]; then
    echo "That is expected with --platform-only: there is no app yet."
  else
    echo "Ask Azure directly:"
    echo "  az containerapp show -g $resource_group -n $app_name \\"
    echo "    --query properties.configuration.ingress.fqdn -o tsv"
  fi
fi

# Still print the raw outputs, so the registry name, database host and so on are on the terminal
# for anyone who needs them.
echo
echo "All deployment outputs:"
printf '%s\n' "$outputs"
