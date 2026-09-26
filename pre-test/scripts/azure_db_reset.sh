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
# arms.
#
# Needs psql, and your own IP allowed on the server's firewall. The deployment opens the firewall
# to Azure services only - that is what lets the container app in, and it deliberately does not let
# your laptop in. Both are checked below before anything is dropped, because both otherwise fail
# AFTER the confirmation prompt, which reads as though the wipe half-happened.

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

if ! command -v psql >/dev/null 2>&1; then
  echo "psql is not installed - this script needs it to talk to Postgres." >&2
  echo >&2
  echo "On macOS:" >&2
  echo "  brew install libpq" >&2
  echo "  echo 'export PATH=\"/opt/homebrew/opt/libpq/bin:\$PATH\"' >> ~/.zprofile" >&2
  echo "  source ~/.zprofile" >&2
  echo >&2
  echo "libpq is keg-only, so brew installs psql without putting it on your PATH. Skipping that" >&2
  echo "second line is why 'brew install libpq' can look like it did nothing." >&2
  exit 1
fi

# The deployment's only firewall rule is AllowAllAzureServices, which lets the container app in and
# your laptop stay out. Without a rule for this machine psql just hangs until it times out, well
# after the confirmation prompt - so check now, while nothing has been dropped.
my_ip="$(curl -fsS --max-time 10 https://api.ipify.org 2>/dev/null || true)"
if [[ -z "$my_ip" ]]; then
  echo "Could not work out this machine's public IP; skipping the firewall check." >&2
  echo "If psql hangs below, that is why - add a rule for your IP in the Azure portal." >&2
else
  server_name="$(az postgres flexible-server list --resource-group "$resource_group" \
                   --query "[0].name" --output tsv)"
  allowed="$(az postgres flexible-server firewall-rule list \
               --resource-group "$resource_group" --name "$server_name" \
               --query "[?startIpAddress=='$my_ip'] | length(@)" --output tsv 2>/dev/null || echo 0)"
  if [[ "$allowed" == "0" ]]; then
    echo "Your IP ($my_ip) is not allowed on $server_name's firewall, so psql cannot connect."
    echo "Adding a rule opens the database to this IP until you remove it."
    read -r -p "Add a firewall rule for $my_ip? [y/N] " answer
    if [[ "$answer" == "y" || "$answer" == "Y" ]]; then
      az postgres flexible-server firewall-rule create \
        --resource-group "$resource_group" --name "$server_name" \
        --rule-name "laptop-$(echo "$my_ip" | tr '.' '-')" \
        --start-ip-address "$my_ip" --end-ip-address "$my_ip" \
        --output none
      echo "Rule added. Remove it when you are done:"
      echo "  az postgres flexible-server firewall-rule delete -g $resource_group \\"
      echo "    -n $server_name -r laptop-$(echo "$my_ip" | tr '.' '-') --yes"
    else
      echo "Not adding it. psql will not be able to connect." >&2
      exit 1
    fi
  fi
fi

echo
echo "About to DROP schemas insurance_portal and behavioural in $db_name on $server."
echo "This deletes every participant's data in the pre-test."
echo "Export it first if you have not: the CSV button at <appUrl>/data."
echo
read -r -p "Type the database name to confirm ($db_name): " confirm
[[ "$confirm" == "$db_name" ]] || { echo "Aborted."; exit 1; }

PGPASSWORD="$db_password" psql \
  --host="$server" --username="$db_user" --dbname="$db_name" \
  --set=sslmode=require \
  --command='DROP SCHEMA IF EXISTS behavioural CASCADE; DROP SCHEMA IF EXISTS insurance_portal CASCADE;'

echo "==> Dropped. Restart the container app (or redeploy) to rebuild both schemas."
