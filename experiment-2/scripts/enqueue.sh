#!/usr/bin/env bash
# enqueue.sh - put a participant in the agent queue by hand, as the assistant's tool call would.
#
#   ./scripts/enqueue.sh pilot-ack-ack-203115
#
# For testing the human leg without the AI leg. Normally the assistant calls
# transfer_to_human_agent and the browser POSTs this; without OPENAI_API_KEY there is no assistant,
# so this stands in for it.
#
# The participant must already have an open session - that is created when their page loads - so
# run ./scripts/pilot.sh first, let the portal appear, and pass the participant id it printed.

set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
env_file="$root/infra/.env"
app_name="exp2-feeling-heard"
participant=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --env-file) env_file="$2"; shift 2 ;;
    -h|--help)  sed -n '2,13p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0 ;;
    -*)         echo "Unknown option: $1" >&2; exit 2 ;;
    *)          participant="$1"; shift ;;
  esac
done

if [[ -z "$participant" ]]; then
  echo "Which participant? Pass the id ./scripts/pilot.sh printed." >&2
  echo "  ./scripts/enqueue.sh pilot-ack-ack-203115" >&2
  exit 2
fi

resource_group="$(sed -n 's/^[[:space:]]*RESOURCE_GROUP[[:space:]]*=//p' "$env_file" 2>/dev/null | head -n1 | sed 's/[[:space:]]*$//')"
[[ -n "$resource_group" ]] || resource_group="rg-exp2-feeling-heard"

fqdn="$(az containerapp show --resource-group "$resource_group" --name "$app_name" \
          --query properties.configuration.ingress.fqdn --output tsv 2>/dev/null || true)"
[[ -n "$fqdn" ]] || { echo "Could not find '$app_name' in $resource_group." >&2; exit 1; }
app="https://$fqdn"

# The same shape the assistant's tool call produces, so the console shows what it would really show.
summary='{"dispute_summary":"Says the 90% cover was clear when she filed.","requested_outcome":"The full 1,080 pounds.","grounds_given":"The policy page showed 90% for water damage.","customer_state":"very frustrated"}'
body="$(python3 -c '
import json, sys
print(json.dumps({"summary": sys.argv[1]}))
' "$summary")"

response="$(curl -sS -X POST "$app/api/study/agent-handover" \
  -H 'Content-Type: application/json' \
  -H "X-Participant-Id: $participant" \
  --data-binary "$body" \
  -w $'\n%{http_code}')"
status="$(printf '%s' "$response" | tail -n1)"
payload="$(printf '%s' "$response" | sed '$d')"

if [[ "$status" != "200" ]]; then
  echo "Enqueue failed (HTTP $status)." >&2
  echo "$payload" >&2
  if [[ "$status" == "400" ]]; then
    echo >&2
    echo "400 means there is no open session for '$participant'. The session is created when the" >&2
    echo "participant's page loads - run ./scripts/pilot.sh, wait for the portal to appear, then" >&2
    echo "pass the id it printed." >&2
  fi
  exit 1
fi

echo "$payload"
echo
echo "-------------------------------------------------------------------------------"
echo " $participant is now in the queue."
echo
echo " Their tab should switch to 'putting you through' within a couple of seconds."
echo " Now open the console, log in as the agent, and pick them up:"
echo
echo "   $app/agent"
echo
echo " Use headphones - otherwise the two tabs feed each other."
echo "-------------------------------------------------------------------------------"
