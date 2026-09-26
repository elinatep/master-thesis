#!/usr/bin/env bash
# pilot.sh - walk one arm of the pre-test yourself, as a participant would.
#
#   ./scripts/pilot.sh S4                 # fresh run in arm S4
#   ./scripts/pilot.sh S1 my-own-id       # fresh run in arm S1, with an id you choose
#   ./scripts/pilot.sh --url              # just print the app URL and stop
#
# Nothing to fill in: the app URL comes from Azure and the handover secret from infra/.env, which
# are the same two values the deploy used. Doing this by hand means pasting a URL and a secret into
# a curl command, and a mistyped secret fails with 401 in a way that reads like a broken app.
#
# Each run gets a NEW participant id unless you pass one. That is deliberate: reusing an id resumes
# the earlier session, which in S4 means arriving with an attempt already spent - the failure you
# were trying to see does not happen, and nothing says why.

set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
env_file="$root/infra/.env"
app_name="pretest-feeling-heard"
arm=""
participant=""
url_only=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --url)      url_only=1; shift ;;
    --env-file) env_file="$2"; shift 2 ;;
    -h|--help)  sed -n '2,20p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0 ;;
    -*)         echo "Unknown option: $1" >&2; exit 2 ;;
    *)
      if [[ -z "$arm" ]]; then arm="$1"; elif [[ -z "$participant" ]]; then participant="$1"; else
        echo "Too many arguments: $1" >&2; exit 2
      fi
      shift ;;
  esac
done

if [[ ! -f "$env_file" ]]; then
  echo "Env file not found: $env_file" >&2
  echo "This is the same infra/.env the deploy used - run from the pre-test directory." >&2
  exit 1
fi

read_var() {
  sed -n "s/^[[:space:]]*$1[[:space:]]*=//p" "$env_file" | head -n1 | sed 's/[[:space:]]*$//'
}

resource_group="$(read_var RESOURCE_GROUP)"
[[ -n "$resource_group" ]] || resource_group="rg-e2-feeling-heard"
secret="$(read_var HANDOVER_SECRET)"

# The arms are the files in configuration/arms, so this list cannot drift from what the app loads.
known_arms=()
for f in "$root/configuration/arms"/*.json; do
  [[ -e "$f" ]] || continue
  known_arms+=( "$(basename "$f" .json)" )
done

echo "==> Asking Azure for the app URL"
fqdn="$(az containerapp show \
  --resource-group "$resource_group" \
  --name "$app_name" \
  --query properties.configuration.ingress.fqdn \
  --output tsv 2>/dev/null || true)"

if [[ -z "$fqdn" ]]; then
  echo "Could not find the container app '$app_name' in resource group '$resource_group'." >&2
  echo "Either the deploy has not run yet, or you are logged into a different subscription." >&2
  echo "Check with:  az containerapp list -g $resource_group -o table" >&2
  exit 1
fi

app="https://$fqdn"
echo "    $app"

if [[ $url_only -eq 1 ]]; then
  echo
  echo "$app"
  exit 0
fi

if [[ -z "$arm" ]]; then
  echo
  echo "Which arm? Pass one of: ${known_arms[*]}" >&2
  echo "  ./scripts/pilot.sh S4" >&2
  exit 2
fi

# Match the arm case-insensitively but send the id as configured, so 's4' works and the app still
# gets 'S4'. An unknown arm is rejected here rather than by the server, so the message can list
# the real ones.
#
# Lower-cased with tr, not with ${x,,}: macOS ships bash 3.2 as /bin/bash, where that expansion is
# a syntax error ("bad substitution") - and this script exists to be run on a Mac.
lower() { printf '%s' "$1" | tr '[:upper:]' '[:lower:]'; }
arm_lower="$(lower "$arm")"
matched=""
for a in "${known_arms[@]}"; do
  if [[ "$(lower "$a")" == "$arm_lower" ]]; then matched="$a"; break; fi
done
if [[ -z "$matched" ]]; then
  echo "Unknown arm '$arm'. Configured arms are: ${known_arms[*]}" >&2
  exit 2
fi
arm="$matched"

[[ -n "$participant" ]] || participant="pilot-$(lower "$arm")-$(date +%H%M%S)"

echo "==> Registering a handover: arm=$arm participant=$participant"

# Build the body with python so the values are JSON-escaped rather than string-glued in.
body="$(python3 -c '
import json, sys
print(json.dumps({
    "participantId": sys.argv[1],
    "arm": sys.argv[2],
    "name": "Joe Smith",
    "callbackUrl": "https://mtecethz.eu.qualtrics.com",
}))
' "$participant" "$arm")"

curl_args=( -sS -X POST "$app/api/handover"
            -H 'Content-Type: application/json'
            --data-binary "$body"
            -w $'\n%{http_code}' )
if [[ -n "$secret" ]]; then
  curl_args+=( -H "X-Handover-Secret: $secret" )
else
  echo "    (no HANDOVER_SECRET in $env_file - sending an unauthenticated register)"
fi

response="$(curl "${curl_args[@]}")"
status="$(printf '%s' "$response" | tail -n1)"
payload="$(printf '%s' "$response" | sed '$d')"

if [[ "$status" != "200" ]]; then
  echo
  echo "Registering the handover failed (HTTP $status)." >&2
  echo "$payload" >&2
  case "$status" in
    401) echo
         echo "401 means the secret did not match. The app has the value HANDOVER_SECRET had at" >&2
         echo "the last deploy; if you changed it in infra/.env since, redeploy:" >&2
         echo "  ./scripts/release.sh" >&2 ;;
    400) echo
         echo "400 usually means the arm is not one the app loaded. It knows: ${known_arms[*]}" >&2 ;;
    000) echo
         echo "No response at all - the app may still be starting after a deploy. Wait a minute." >&2 ;;
  esac
  exit 1
fi

token="$(printf '%s' "$payload" | python3 -c '
import json, sys
try:
    print(json.load(sys.stdin)["token"])
except Exception:
    raise SystemExit("could not read a token from: " + sys.stdin.read()[:200])
')"

entry="$app/?t=$token"

echo
echo "-------------------------------------------------------------------------------"
echo " Arm:         $arm"
echo " Participant: $participant"
echo
echo " Entry link (this is what Qualtrics sends a participant to):"
echo
echo "   $entry"
echo
echo " You should land on the Salvena dashboard, file a claim, and end on the arm's"
echo " outcome screen. Afterwards, check what was recorded at:"
echo
echo "   $app/data"
echo "-------------------------------------------------------------------------------"

# Open it for them where we can. Printing the link is the fallback, not a failure.
if command -v open >/dev/null 2>&1; then
  open "$entry"
elif command -v xdg-open >/dev/null 2>&1; then
  xdg-open "$entry" >/dev/null 2>&1 || true
else
  echo
  echo "Copy the entry link into a browser."
fi
