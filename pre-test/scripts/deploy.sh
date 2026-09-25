#!/usr/bin/env bash
# deploy.sh - deploy the pre-test, loading parameters from infra/.env
#
#   ./scripts/deploy.sh --image <acr>.azurecr.io/pretest-feeling-heard:<sha>
#   ./scripts/deploy.sh --platform-only
#
# Resource-group scoped: the group must already exist, and this never owns it. Everything inside it
# belongs to this study alone - it does not share levels-of-ai-help's platform.
#
# Prefer ./scripts/release.sh, which builds and tags the image before calling this.

set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
env_file="$root/infra/.env"
image=""
resource_group=""
platform_only=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --image)          image="$2"; shift 2 ;;
    --resource-group) resource_group="$2"; shift 2 ;;
    --env-file)       env_file="$2"; shift 2 ;;
    --platform-only)  platform_only=1; shift ;;
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

[[ -n "$resource_group" ]] || resource_group="$(read_var RESOURCE_GROUP)"
[[ -n "$resource_group" ]] || resource_group="rg-e2-feeling-heard"

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
  echo "Handover secret: SET (Qualtrics must send X-Handover-Secret)"
  params+=( "handoverSecret=$handover_secret" )
else
  echo "WARNING: handover secret is EMPTY. Anyone who finds the URL can mint a token and enter the"
  echo "         study in an arm of their choosing, and those responses look exactly like real ones."
  echo "         Set HANDOVER_SECRET in infra/.env before a real run."
fi

auth_client_id="$(read_var AUTH_CLIENT_ID)"
auth_client_secret="$(read_var AUTH_CLIENT_SECRET)"
if [[ -n "$auth_client_id" && -n "$auth_client_secret" ]]; then
  echo "WARNING: Easy Auth is ON. This blocks anonymous Prolific participants and breaks the"
  echo "         Qualtrics iframe. Clear both AUTH_ values before a real run."
  params+=( "authClientId=$auth_client_id" "authClientSecret=$auth_client_secret" )
else
  echo "Easy Auth: OFF (anonymous access - the real study mode)"
fi

echo "==> Deploying to resource group $resource_group"

az deployment group create \
  --name pretest-feeling-heard \
  --resource-group "$resource_group" \
  --template-file "$root/infra/main.bicep" \
  --parameters "${params[@]}" \
  --query "properties.outputs" \
  --output table
