#!/usr/bin/env bash
# azure_db_reset.sh - wipe the pre-test's data on Azure, leaving the server alone.
#
#   ./scripts/azure_db_reset.sh
#
# Drops both schemas in the pre-test's database: insurance_portal (the seeded account) and
# behavioural (the study log). Each carries its own Flyway history table, so dropping them is
# enough - the next container start rebuilds both from scratch.
#
# Run this between pilot rounds so an analysis never mixes runs from two different versions of the
# arms. Needs psql (brew install libpq) and your IP allowed on the server's firewall.

set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
env_file="$root/infra/.env"
db_name="pretest_feeling_heard"
resource_group=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --resource-group) resource_group="$2"; shift 2 ;;
    --db)             db_name="$2"; shift 2 ;;
    *) echo "Unknown argument: $1" >&2; exit 2 ;;
  esac
done

[[ -f "$env_file" ]] || { echo "Env file not found: $env_file" >&2; exit 1; }

read_var() {
  sed -n "s/^[[:space:]]*$1[[:space:]]*=//p" "$env_file" | head -n1 | sed 's/[[:space:]]*$//'
}

[[ -n "$resource_group" ]] || resource_group="$(read_var RESOURCE_GROUP)"
[[ -n "$resource_group" ]] || resource_group="rg-e2-feeling-heard"

db_user="$(read_var DB_ADMIN_USER)"
[[ -n "$db_user" ]] || db_user="portaladmin"

db_password="$(read_var DB_ADMIN_PASSWORD)"
[[ -n "$db_password" ]] || { echo "DB_ADMIN_PASSWORD missing in $env_file" >&2; exit 1; }

server="$(az postgres flexible-server list --resource-group "$resource_group" \
            --query "[0].fullyQualifiedDomainName" --output tsv)"
[[ -n "$server" ]] || { echo "No Postgres server found in $resource_group." >&2; exit 1; }

echo "About to DROP schemas insurance_portal and behavioural in $db_name on $server."
echo "This deletes every participant's data in the pre-test."
read -r -p "Type the database name to confirm: " confirm
[[ "$confirm" == "$db_name" ]] || { echo "Aborted."; exit 1; }

PGPASSWORD="$db_password" psql \
  --host="$server" --username="$db_user" --dbname="$db_name" \
  --set=sslmode=require \
  --command='DROP SCHEMA IF EXISTS behavioural CASCADE; DROP SCHEMA IF EXISTS insurance_portal CASCADE;'

echo "==> Dropped. Restart the container app (or redeploy) to rebuild both schemas."
