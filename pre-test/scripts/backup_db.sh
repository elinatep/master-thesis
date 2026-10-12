#!/usr/bin/env bash
# backup_db.sh - take the pre-test's data off Azure and onto this machine.
#
#   ./scripts/backup_db.sh                    # dump everything into data/
#   ./scripts/backup_db.sh --out ~/Archive    # somewhere else
#   ./scripts/backup_db.sh --check            # just say what is on the server, change nothing
#
# Run this BEFORE deleting the resource group, and before any schema reset. Three things to know
# about what you are protecting:
#
#   1. Azure's own automated backups live INSIDE the resource group. Deleting the group deletes
#      them with it, so they are not a copy of anything - they protect against a bad week, not
#      against a deliberate teardown.
#   2. The admin password is in infra/.env and nowhere else. Azure cannot show it to you again. If
#      that file is lost the server cannot be reached even while it is still running.
#   3. The /data page exports the behavioural log only. The claims the participants actually filed
#      live in the portal's own schema, and the write-up may well need them - "what did they type
#      into the claim form" is a portal question, not a behavioural-log one.
#
# So this takes three artefacts: a restorable dump, the same thing as readable SQL, and the
# behavioural log as JSON. The first is the archive; the second is what survives a tool-version
# change in five years; the third is what the analysis reads today.

set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
env_file="$root/infra/.env"
out_dir="$root/data"
db_name="pretest_feeling_heard"
app_name="pretest-feeling-heard"
resource_group=""
server=""
check_only=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --out)            out_dir="$2"; shift 2 ;;
    --resource-group) resource_group="$2"; shift 2 ;;
    --db)             db_name="$2"; shift 2 ;;
    --server)         server="$2"; shift 2 ;;
    --env-file)       env_file="$2"; shift 2 ;;
    --check)          check_only=1; shift ;;
    -h|--help)        sed -n '2,23p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "Unknown argument: $1" >&2; exit 2 ;;
  esac
done

[[ -f "$env_file" ]] || {
  echo "Env file not found: $env_file" >&2
  echo "That file holds the database password, and it is the only copy. Without it this" >&2
  echo "server cannot be reached at all." >&2
  exit 1
}

read_var() {
  sed -n "s/^[[:space:]]*$1[[:space:]]*=//p" "$env_file" | head -n1 | sed 's/[[:space:]]*$//'
}

[[ -n "$resource_group" ]] || resource_group="$(read_var RESOURCE_GROUP)"
[[ -n "$resource_group" ]] || resource_group="rg-e2-feeling-heard"

db_user="$(read_var DB_ADMIN_USER)"
[[ -n "$db_user" ]] || db_user="portaladmin"

db_password="$(read_var DB_ADMIN_PASSWORD)"
[[ -n "$db_password" ]] || { echo "DB_ADMIN_PASSWORD missing in $env_file" >&2; exit 1; }

# pg_dump, not just psql. Both come from libpq, but a machine can have one without the other if
# psql was installed some other way - and finding that out after the firewall rule is added and the
# password is in the environment is a worse moment for it.
for tool in pg_dump psql; do
  if ! command -v "$tool" >/dev/null 2>&1; then
    echo "$tool is not installed - this script needs it to read the database." >&2
    echo >&2
    echo "On macOS:" >&2
    echo "  brew install libpq" >&2
    echo "  echo 'export PATH=\"/opt/homebrew/opt/libpq/bin:\$PATH\"' >> ~/.zprofile" >&2
    echo "  source ~/.zprofile" >&2
    echo >&2
    echo "libpq is keg-only, so brew installs these without putting them on your PATH. Skipping" >&2
    echo "that second line is why 'brew install libpq' can look like it did nothing." >&2
    exit 1
  fi
done

if [[ -z "$server" ]]; then
  server="$(az postgres flexible-server list --resource-group "$resource_group" \
              --query "[0].fullyQualifiedDomainName" --output tsv 2>/dev/null || true)"
fi
[[ -n "$server" ]] || {
  echo "No Postgres server found in resource group '$resource_group'." >&2
  echo "If the group is already gone, so is the data - check with:" >&2
  echo "  az group list --output table" >&2
  exit 1
}
echo "==> Server:   $server"
echo "==> Database: $db_name"

# pg_dump refuses to dump a server NEWER than itself ("server version X; pg_dump version Y"), and
# that refusal happens after the connection, so it reads as a network or permission problem. Check
# the two major versions here instead, where the message can say what to do.
local_major="$(pg_dump --version | sed -n 's/.* \([0-9]\{1,\}\)\..*/\1/p;s/.* \([0-9]\{1,\}\)$/\1/p' | head -n1)"
server_major="$(az postgres flexible-server list --resource-group "$resource_group" \
                  --query "[0].version" --output tsv 2>/dev/null || echo "")"
if [[ -n "$local_major" && -n "$server_major" ]] && (( local_major < server_major )); then
  echo >&2
  echo "Your pg_dump is version $local_major and the server is $server_major. pg_dump will not dump" >&2
  echo "a server newer than itself. Upgrade libpq:" >&2
  echo "  brew upgrade libpq" >&2
  exit 1
fi

# The deployment's only firewall rule is AllowAllAzureServices, which lets the container app in and
# keeps your laptop out. Without a rule for this machine pg_dump just hangs until it times out.
my_ip="$(curl -fsS --max-time 10 https://api.ipify.org 2>/dev/null || true)"
server_name="$(az postgres flexible-server list --resource-group "$resource_group" \
                 --query "[0].name" --output tsv 2>/dev/null || true)"
rule_added=""
if [[ -z "$my_ip" || -z "$server_name" ]]; then
  echo "Could not check the firewall (no public IP or no server name); carrying on." >&2
  echo "If the dump hangs, that is why - add a rule for your IP in the Azure portal." >&2
else
  # The server is --server-name and the rule is --name. Both halves have to be right and they are
  # easy to swap: --name alone is rejected with "the following arguments are required:
  # --server-name/-s", and --rule-name is rejected as unrecognised.
  rule_name="laptop-$(echo "$my_ip" | tr '.' '-')"
  allowed="$(az postgres flexible-server firewall-rule list \
               --resource-group "$resource_group" --server-name "$server_name" \
               --query "[?startIpAddress=='$my_ip'] | length(@)" --output tsv 2>/dev/null || echo 0)"
  if [[ "$allowed" == "0" ]]; then
    echo "Your IP ($my_ip) is not allowed on $server_name's firewall, so nothing can connect."
    read -r -p "Add a firewall rule for $my_ip? [y/N] " answer
    if [[ "$answer" == "y" || "$answer" == "Y" ]]; then
      az postgres flexible-server firewall-rule create \
        --resource-group "$resource_group" --server-name "$server_name" \
        --name "$rule_name" \
        --start-ip-address "$my_ip" --end-ip-address "$my_ip" \
        --output none
      rule_added="$rule_name"
      echo "Rule added."
    else
      echo "Not adding it. Nothing can connect." >&2
      exit 1
    fi
  fi
fi

export PGPASSWORD="$db_password"
export PGSSLMODE="require"

psql_q() {
  psql --host="$server" --username="$db_user" --dbname="$db_name" \
       --no-psqlrc --tuples-only --no-align --command="$1" 2>/dev/null || echo "?"
}

echo "==> What is on the server right now"
sessions="$(psql_q 'select count(*) from behavioural.study_session')"
events="$(psql_q 'select count(*) from behavioural.event')"
claims="$(psql_q "select count(*) from insurance_portal.claim")"
printf '    sessions: %s\n    events:   %s\n    claims:   %s\n' "$sessions" "$events" "$claims"

if [[ "$sessions" == "0" || "$sessions" == "?" ]]; then
  echo
  echo "    Note: no sessions found. If you expected data, check you are pointed at the right"
  echo "    resource group - this is '$resource_group'."
fi

if [[ $check_only -eq 1 ]]; then
  echo
  echo "--check: nothing written, nothing changed."
  [[ -n "$rule_added" ]] && echo "The firewall rule '$rule_added' was added and is still there."
  exit 0
fi

mkdir -p "$out_dir"
stamp="$(date -u +%Y%m%dT%H%M%SZ)"
base="$out_dir/pretest-$stamp"

# Never overwrite. A backup that silently replaced an earlier one is worse than no backup, because
# you believe you have two.
for ext in dump sql log.json; do
  [[ -e "$base.$ext" ]] && { echo "Refusing to overwrite $base.$ext" >&2; exit 1; }
done

echo "==> Dumping (restorable, custom format)"
pg_dump --host="$server" --username="$db_user" --dbname="$db_name" \
        --format=custom --no-owner --no-privileges --file="$base.dump"

echo "==> Dumping (plain SQL, for reading in five years)"
pg_dump --host="$server" --username="$db_user" --dbname="$db_name" \
        --format=plain --no-owner --no-privileges --file="$base.sql"

# The behavioural log as the analysis consumes it. Through the app rather than the database, so it
# is the same joined, ordered rows the /data page shows rather than raw tables - and if this one
# fails, the dumps above still hold everything it is derived from.
researcher_user="$(read_var RESEARCHER_USER)"
[[ -n "$researcher_user" ]] || researcher_user="researcher"
researcher_password="$(read_var RESEARCHER_PASSWORD)"

if [[ -z "$researcher_password" ]]; then
  echo "==> Skipping the behavioural log export: no RESEARCHER_PASSWORD in $env_file"
  echo "    (the dumps above still contain every row it is built from)"
else
  fqdn="$(az containerapp show --resource-group "$resource_group" --name "$app_name" \
            --query properties.configuration.ingress.fqdn --output tsv 2>/dev/null || true)"
  if [[ -z "$fqdn" ]]; then
    echo "==> Skipping the behavioural log export: could not find the app '$app_name'"
  else
    echo "==> Exporting the behavioural log from https://$fqdn/api/study/log"
    if curl -fsS -u "$researcher_user:$researcher_password" \
            "https://$fqdn/api/study/log" -o "$base.log.json"; then
      rows="$(python3 -c '
import json, sys
try:
    print(len(json.load(open(sys.argv[1]))))
except Exception:
    print("?")
' "$base.log.json")"
      echo "    $rows rows"
    else
      echo "    Failed. A 401 means RESEARCHER_PASSWORD differs from the one the app was" >&2
      echo "    deployed with. The dumps above are unaffected." >&2
      rm -f "$base.log.json"
    fi
  fi
fi

echo
echo "==> Verifying the dump can be read back"
# A dump that cannot be listed is not a backup. This reads the archive's own table of contents,
# which fails on a truncated or half-written file - the failure mode that otherwise waits until the
# day you need it.
if pg_restore --list "$base.dump" >/dev/null 2>&1; then
  tables="$(pg_restore --list "$base.dump" | grep -c "TABLE DATA" || true)"
  echo "    OK - $tables tables with data"
else
  echo "    FAILED to read $base.dump back. Do not delete anything." >&2
  exit 1
fi

echo
echo "-------------------------------------------------------------------------------"
echo " Saved:"
for f in "$base.dump" "$base.sql" "$base.log.json"; do
  [[ -e "$f" ]] && printf '   %-14s %s\n' "$(du -h "$f" | cut -f1)" "$f"
done
echo
if [[ "$out_dir" == "$root/data" ]]; then
  echo " That is inside the repository, in a gitignored directory - which keeps it out of"
  echo " git, the thing that matters most."
else
  # Written somewhere this repository does not control, so it is worth saying out loud that
  # nothing is stopping it being committed or synced from there.
  echo " That is outside the repository, so no gitignore is protecting it. Make sure it is"
  echo " not in a folder that syncs to a shared Drive."
fi
echo
echo " Either way it is still one disk. Copy it somewhere that is backed up (ETH drive,"
echo " an external disk) before deleting anything on Azure. Prolific ids are personal"
echo " data: not a shared Drive folder, and not a chat window."
echo
echo " To restore into a fresh server later:"
echo "   createdb -h <new server> -U <admin> $db_name"
echo "   pg_restore -h <new server> -U <admin> -d $db_name --no-owner $base.dump"
echo "-------------------------------------------------------------------------------"

if [[ -n "$rule_added" ]]; then
  echo
  echo " The firewall rule for your IP is still in place. Remove it when you are done:"
  echo "   az postgres flexible-server firewall-rule delete -g $resource_group \\"
  echo "     --server-name $server_name --name $rule_added --yes"
fi
