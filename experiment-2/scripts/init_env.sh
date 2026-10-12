#!/usr/bin/env bash
# init_env.sh - generate the secrets infra/.env needs, write them in, and print them once.
#
#   ./scripts/init_env.sh
#
# Fills only the keys that are EMPTY. Anything you have already set is left exactly as it is, so
# running this twice cannot quietly replace a password the deployed database is already using -
# that one is unrecoverable, because Azure will not show it to you again.
#
# What it generates:
#   DB_ADMIN_PASSWORD    the Postgres admin password, created with the server on the first deploy
#   HANDOVER_SECRET      what Qualtrics sends as X-Handover-Secret
#   RESEARCHER_PASSWORD  the password on /data and the admin endpoints
#   AGENT_PASSWORD       what the confederate signs into the console with
#
# OPENAI_API_KEY is NOT generated: it belongs to the MTEC Azure OpenAI resource and somebody has to
# give it to you.
#
# The values are printed at the end, once. Put them in a password manager then - infra/.env is the
# only other copy, it is gitignored, and the database password cannot be read back out of Azure.

set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
env_file="$root/infra/.env"
example="$root/infra/.env.example"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --env-file) env_file="$2"; shift 2 ;;
    -h|--help)  sed -n '2,22p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "Unknown argument: $1" >&2; exit 2 ;;
  esac
done

if [[ ! -f "$env_file" ]]; then
  if [[ -f "$example" ]]; then
    cp "$example" "$env_file"
    echo "Created $env_file from the template."
  else
    echo "No $env_file and no template at $example." >&2
    exit 1
  fi
fi

read_var() {
  sed -n "s/^[[:space:]]*$1[[:space:]]*=//p" "$env_file" | head -n1 | sed 's/[[:space:]]*$//'
}

# A password Azure accepts and a shell will not mangle.
#
# Azure's rule for the Postgres admin password is 8-128 characters with at least three of upper
# case, lower case, digit and special - so this guarantees one of each of the four and fills the
# rest at random, rather than generating and hoping.
#
# The special set excludes $ ` " \ and ' because those are mangled or quoted differently by bash,
# zsh and PowerShell, and this value is passed through all of them; and # because a value starting
# with one reads like a comment to a human scanning the file. The remainder is plenty of entropy at
# this length.
gen_password() {
  python3 - <<'PY'
import secrets
upper = "ABCDEFGHJKLMNPQRSTUVWXYZ"   # no I or O: this gets read off a screen and retyped
lower = "abcdefghijkmnopqrstuvwxyz"  # no l, for the same reason
digits = "23456789"                  # no 0 or 1
special = "-_.!%+:@^"
alphabet = upper + lower + digits + special
rng = secrets.SystemRandom()
chars = [rng.choice(upper), rng.choice(lower), rng.choice(digits), rng.choice(special)]
chars += [rng.choice(alphabet) for _ in range(20)]
rng.shuffle(chars)
print("".join(chars))
PY
}

gen_uuid() {
  if command -v uuidgen >/dev/null 2>&1; then
    uuidgen | tr '[:upper:]' '[:lower:]'
  else
    python3 -c 'import uuid; print(uuid.uuid4())'
  fi
}

# Rewrite one KEY=VALUE line through a temp file.
#
# Not `sed -i`: its in-place flag takes an argument on macOS and takes none on GNU, so the one
# spelling that works on this machine corrupts the file on the other. Python also means the value
# is never re-interpreted as a sed replacement, where a & or a / in a generated password would
# quietly produce a different password from the one printed below.
set_var() {
  python3 - "$env_file" "$1" "$2" <<'PY'
import re, sys, os
path, key, value = sys.argv[1], sys.argv[2], sys.argv[3]
with open(path, encoding='utf-8') as fh:
    lines = fh.readlines()
pattern = re.compile(r'^\s*' + re.escape(key) + r'\s*=')
found = False
for i, line in enumerate(lines):
    if pattern.match(line):
        lines[i] = f'{key}={value}\n'
        found = True
        break
if not found:
    if lines and not lines[-1].endswith('\n'):
        lines[-1] += '\n'
    lines.append(f'{key}={value}\n')
tmp = path + '.tmp'
with open(tmp, 'w', encoding='utf-8') as fh:
    fh.writelines(lines)
os.replace(tmp, path)
PY
}

declare -a generated_keys=()
declare -a generated_values=()
declare -a kept=()

fill() {
  local key="$1" value="$2"
  if [[ -n "$(read_var "$key")" ]]; then
    kept+=( "$key" )
    return
  fi
  set_var "$key" "$value"
  generated_keys+=( "$key" )
  generated_values+=( "$value" )
}

fill DB_ADMIN_PASSWORD "$(gen_password)"
fill HANDOVER_SECRET "$(gen_uuid)"
fill RESEARCHER_PASSWORD "$(gen_password)"
fill AGENT_PASSWORD "$(gen_password)"

# The file holds live credentials; there is no reason for anyone else on the machine to read it.
chmod 600 "$env_file"

echo
if [[ ${#kept[@]} -gt 0 ]]; then
  echo "Already set, left untouched: ${kept[*]}"
  echo
fi

if [[ ${#generated_keys[@]} -eq 0 ]]; then
  echo "Nothing to generate - every secret in $env_file already has a value."
else
  echo "==============================================================================="
  echo " Generated and written to infra/.env. COPY THESE INTO YOUR PASSWORD MANAGER NOW."
  echo "==============================================================================="
  echo
  for i in "${!generated_keys[@]}"; do
    printf '  %-20s %s\n' "${generated_keys[$i]}" "${generated_values[$i]}"
  done
  echo
  echo " Why now, and not later: the database password is created WITH the Postgres"
  echo " server on the first deploy and Azure will never show it to you again. If"
  echo " infra/.env is lost after that, the server is unreachable while still running"
  echo " and still billing."
  echo
  echo " Do not paste these into a chat window, an email, or a commit. infra/.env is"
  echo " gitignored and is now readable only by you."
fi

openai="$(read_var OPENAI_API_KEY)"
echo
if [[ -n "$openai" ]]; then
  echo "OPENAI_API_KEY: set."
else
  echo "OPENAI_API_KEY: still empty - ask Andreas or Joe for the MTEC Azure OpenAI key."
  echo "  Everything deploys and works without it except the AI leg of the call: the"
  echo "  participant would watch the assistant fail to connect."
fi

echo
echo "Next:  ./scripts/release.sh"
