#!/usr/bin/env bash
# pilot.sh - walk one cell of the 2x2 yourself, as a participant would.
#
#   ./scripts/pilot.sh ack-ack            # AI acknowledges, human acknowledges
#   ./scripts/pilot.sh noack-ack          # AI does not, human does
#   ./scripts/pilot.sh ack-ack my-own-id  # with an id you choose
#   ./scripts/pilot.sh --url              # just print the app URL and stop
#   ./scripts/pilot.sh --list             # print the four cells and stop
#
# Nothing to fill in: the app URL comes from Azure and the handover secret from infra/.env, which
# are the same two values the deploy used. Doing this by hand means pasting a URL and a secret into
# a curl command, and a mistyped secret fails with 401 in a way that reads like a broken app.
#
# Each run gets a NEW participant id unless you pass one. Reusing an id resumes the earlier
# session, which is occasionally what you want and usually not: the portal's seeded policies are
# already provisioned and the behavioural log continues where it left off, so a "fresh start" that
# is not one makes a confusing recording.
#
# The cell is the two factors, never one packed code - that is also how the app stores them, and
# how the analysis reads them.

set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
env_file="$root/infra/.env"
app_name="exp2-feeling-heard"
cell=""
participant=""
url_only=0
list_only=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --url)      url_only=1; shift ;;
    --list)     list_only=1; shift ;;
    --env-file) env_file="$2"; shift 2 ;;
    -h|--help)  sed -n '2,21p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0 ;;
    -*)         echo "Unknown option: $1" >&2; exit 2 ;;
    *)
      if [[ -z "$cell" ]]; then cell="$1"; elif [[ -z "$participant" ]]; then participant="$1"; else
        echo "Too many arguments: $1" >&2; exit 2
      fi
      shift ;;
  esac
done

# Lower-cased with tr, not with ${x,,}: macOS ships bash 3.2 as /bin/bash, where that expansion is
# a syntax error ("bad substitution") - and this script exists to be run on a Mac.
lower() { printf '%s' "$1" | tr '[:upper:]' '[:lower:]'; }

# The four cells, spelled as <ai>-<human>. Spelled out rather than computed so the usage message
# and the parser cannot disagree.
print_cells() {
  echo "  ack-ack      AI acknowledges,         human acknowledges       (AI+/H+)"
  echo "  ack-noack    AI acknowledges,         human does not           (AI+/H-)"
  echo "  noack-ack    AI does not acknowledge, human does               (AI-/H+)"
  echo "  noack-noack  AI does not acknowledge, human does not           (AI-/H-)"
}

if [[ $list_only -eq 1 ]]; then
  echo "The four cells of the 2x2:"
  print_cells
  exit 0
fi

if [[ ! -f "$env_file" ]]; then
  echo "Env file not found: $env_file" >&2
  echo "This is the same infra/.env the deploy used - run from the experiment-2 directory." >&2
  exit 1
fi

read_var() {
  sed -n "s/^[[:space:]]*$1[[:space:]]*=//p" "$env_file" | head -n1 | sed 's/[[:space:]]*$//'
}

resource_group="$(read_var RESOURCE_GROUP)"
[[ -n "$resource_group" ]] || resource_group="rg-exp2-feeling-heard"
secret="$(read_var HANDOVER_SECRET)"

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

if [[ -z "$cell" ]]; then
  echo
  echo "Which cell? Pass one of:" >&2
  print_cells >&2
  echo >&2
  echo "  ./scripts/pilot.sh ack-ack" >&2
  exit 2
fi

# Translate the cell name into the two booleans the register call takes. Rejected here rather than
# by the server, so the message can list the real ones.
case "$(lower "$cell")" in
  ack-ack)     ai_ack=true;  human_ack=true  ;;
  ack-noack)   ai_ack=true;  human_ack=false ;;
  noack-ack)   ai_ack=false; human_ack=true  ;;
  noack-noack) ai_ack=false; human_ack=false ;;
  *)
    echo "Unknown cell '$cell'. The four are:" >&2
    print_cells >&2
    exit 2 ;;
esac

cell_label="$([[ $ai_ack == true ]] && printf 'AI+' || printf 'AI-')/$([[ $human_ack == true ]] && printf 'H+' || printf 'H-')"

[[ -n "$participant" ]] || participant="pilot-$(lower "$cell")-$(date +%H%M%S)"

echo "==> Registering a handover: cell=$cell_label participant=$participant"

# Build the body with python so the values are JSON-escaped rather than string-glued in. aiAck and
# humanAck are booleans, not strings: the app rejects a missing factor with 400 rather than
# defaulting it, so "false" quoted as a string would fail here rather than silently assign a cell.
body="$(python3 -c '
import json, sys
print(json.dumps({
    "participantId": sys.argv[1],
    "aiAck": sys.argv[2] == "true",
    "humanAck": sys.argv[3] == "true",
    "name": "Joe Smith",
    "callbackUrl": "https://mtecethz.eu.qualtrics.com",
}))
' "$participant" "$ai_ack" "$human_ack")"

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
         echo "400 means the body was rejected - most likely a missing aiAck or humanAck." >&2 ;;
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
echo " Cell:        $cell_label  ($cell)"
echo " Participant: $participant"
echo
echo " Entry link (this is what Qualtrics sends a participant to):"
echo
echo "   $entry"
echo
echo " You should land on the Salvena dashboard with the task card beside it, file the"
echo " claim, and be able to start the voice assistant. Afterwards, check what was"
echo " recorded at:"
echo
echo "   $app/data"
echo
echo " The voice leg needs OPENAI_API_KEY to have been set at the last deploy. If the"
echo " assistant refuses to connect, that is the first thing to check - the browser"
echo " console will show the failed call to /api/voice/token."
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
